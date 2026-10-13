package com.mandalay.smartmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small, sequential user-triggered downloads for a central-Mandalay pilot area.
 * The public Overpass API is not suitable for production-wide crawling.
 */
final class OsmClient {
    static final double SOUTH=21.935, WEST=96.045, NORTH=22.045, EAST=96.135;
    private static final String[] ENDPOINTS={
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass-api.de/api/interpreter"
    };
    interface Progress { void onProgress(String stage, int processedSteps, int totalSteps); }

    static final class Result {
        int roadCount, placeCount, failedRoadTiles, failedPlaceTiles;
        boolean isPartial() { return failedRoadTiles > 0 || failedPlaceTiles > 0; }
    }

    static Result downloadAndImport(MandalayDb db, Progress progress) throws Exception {
        Result result=new Result();
        Exception lastFailure=null;
        int completed=0;
        progress.onProgress("Starting Mandalay download…",0,8);
        // Four small areas reduce Overpass execution cost and allow partial offline saving.
        final double midLat=(SOUTH+NORTH)/2.0, midLon=(WEST+EAST)/2.0;
        final double[][] tiles={
            {SOUTH,WEST,midLat,midLon}, {SOUTH,midLon,midLat,EAST},
            {midLat,WEST,NORTH,midLon}, {midLat,midLon,NORTH,EAST}
        };
        for(int i=0;i<tiles.length;i++) {
            double[] t=tiles[i];
            String bounds=String.format(Locale.US,"(%.6f,%.6f,%.6f,%.6f)",t[0],t[1],t[2],t[3]);
            progress.onProgress("Roads "+(i+1)+"/4 — downloading smaller area…",completed,8);
            String roadsQuery="[out:json][timeout:35][maxsize:134217728];"+
                "way[\"highway\"~\"^(residential|unclassified|living_street|pedestrian|primary|secondary|tertiary|trunk|motorway|service|road|primary_link|secondary_link|tertiary_link)$\"]"+
                bounds+";out geom;";
            try {
                List<Road> roads=parseRoads(request(roadsQuery));
                if(!roads.isEmpty()) {
                    db.mergeRoads(roads);
                    result.roadCount+=roads.size();
                } else {
                    result.failedRoadTiles++;
                }
            } catch(Exception ex) {
                lastFailure=ex;
                result.failedRoadTiles++;
            }
            completed++;
            progress.onProgress("Roads "+(i+1)+"/4 processed",completed,8);
            progress.onProgress("Places "+(i+1)+"/4 — downloading business data…",completed,8);
            String placesQuery="[out:json][timeout:35][maxsize:134217728];("+
                "nwr[\"shop\"]"+bounds+";"+
                "nwr[\"amenity\"~\"^(restaurant|cafe|fast_food|pharmacy|bank|hospital|clinic|fuel|marketplace)$\"]"+bounds+";"+
                "nwr[\"tourism\"~\"^(hotel|guest_house|hostel)$\"]"+bounds+";"+
                ");out center;";
            try {
                List<Place> places=parsePlaces(request(placesQuery));
                db.mergePlaces(places);
                result.placeCount+=places.size();
            } catch(Exception ex) {
                lastFailure=ex;
                result.failedPlaceTiles++;
            }
            completed++;
            progress.onProgress("Places "+(i+1)+"/4 processed",completed,8);
            // Polite, sequential requests: do not flood community-hosted endpoints.
            if(i<tiles.length-1) Thread.sleep(1500);
        }
        if(result.roadCount==0 && result.placeCount==0) {
            throw new Exception("No map data was downloaded. Overpass servers may be busy or inaccessible on this network."
                +(lastFailure==null?"":" Last error: "+lastFailure.getMessage()));
        }
        progress.onProgress(result.isPartial()
            ? "8/8 steps attempted — partial data saved."
            : "8/8 steps done — Offline data ready.",8,8);
        return result;
    }

    private static JSONObject request(String query) throws Exception {
        Exception last=null;
        for(String endpoint:ENDPOINTS) {
            HttpURLConnection con=null;
            try {
                con=(HttpURLConnection)new URL(endpoint).openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setConnectTimeout(15000);
                con.setReadTimeout(55000);
                con.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                con.setRequestProperty("Accept","application/json");
                con.setRequestProperty("User-Agent","MandalaySmartMapPilot/0.2 (OpenStreetMap community pilot)");
                byte[] body=("data="+URLEncoder.encode(query,"UTF-8")).getBytes(StandardCharsets.UTF_8);
                try(OutputStream os=con.getOutputStream()) { os.write(body); }
                int code=con.getResponseCode();
                if(code!=200) {
                    String msg="Overpass HTTP "+code+" from "+new URL(endpoint).getHost();
                    if(code==400 || code==401 || code==403) throw new IllegalArgumentException(msg);
                    throw new Exception(msg);
                }
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buf=new byte[16384]; int n, total=0;
                try(InputStream in=con.getInputStream()) {
                    while((n=in.read(buf))!=-1) {
                        total+=n;
                        if(total>16*1024*1024) throw new Exception("Map response exceeded 16 MB");
                        out.write(buf,0,n);
                    }
                }
                JSONObject json=new JSONObject(out.toString("UTF-8"));
                String remark=json.optString("remark","");
                if(!remark.isEmpty()) throw new Exception("Overpass says: "+remark);
                if(!json.has("elements")) throw new Exception("Overpass response did not include elements");
                return json;
            } catch(IllegalArgumentException fatal) {
                throw fatal; // Syntax/authorization errors won't be fixed by retrying.
            } catch(Exception ex) {
                last=ex;
            } finally {
                if(con!=null) con.disconnect();
            }
        }
        throw new Exception("Both Overpass services unavailable. "
            +(last==null?"Please retry later.":last.getMessage()));
    }

    private static List<Road> parseRoads(JSONObject json) throws JSONException {
        List<Road> out=new ArrayList<>(); JSONArray elements=json.getJSONArray("elements");
        for(int i=0;i<elements.length();i++){
            JSONObject e=elements.getJSONObject(i), tags=e.optJSONObject("tags");
            if(tags==null) continue;
            JSONArray geom=e.optJSONArray("geometry"); if(geom==null||geom.length()<2) continue;
            double[] lats=new double[geom.length()], lons=new double[geom.length()];
            for(int j=0;j<geom.length();j++){
                JSONObject point=geom.getJSONObject(j);
                lats[j]=point.getDouble("lat"); lons[j]=point.getDouble("lon");
            }
            String name=tags.optString("name",tags.optString("name:en",tags.optString("name:my","")));
            out.add(new Road("way/"+e.getLong("id"),name,tags.optString("highway",""),lats,lons));
        }
        return out;
    }

    private static List<Place> parsePlaces(JSONObject json) throws JSONException {
        List<Place> out=new ArrayList<>(); JSONArray elements=json.getJSONArray("elements");
        for(int i=0;i<elements.length();i++){
            JSONObject e=elements.getJSONObject(i), tags=e.optJSONObject("tags");
            if(tags==null)continue;
            String name=tags.optString("name",tags.optString("name:en",tags.optString("name:my",""))).trim();
            if(name.isEmpty())continue;
            JSONObject center=e.optJSONObject("center");
            double lat=center==null?e.optDouble("lat",Double.NaN):center.optDouble("lat",Double.NaN);
            double lon=center==null?e.optDouble("lon",Double.NaN):center.optDouble("lon",Double.NaN);
            if(Double.isNaN(lat)||Double.isNaN(lon)) continue;
            String category=tags.optString("shop",tags.optString("amenity",tags.optString("tourism","business")));
            String street=tags.optString("addr:street", "");
            String house=tags.optString("addr:housenumber", "");
            String full=tags.optString("addr:full", (house+" "+street).trim());
            String phone=tags.optString("contact:phone",tags.optString("phone",""));
            String website=tags.optString("contact:website",tags.optString("website",""));
            out.add(new Place(e.optString("type","node")+"/"+e.getLong("id"), name,
                        category,full,phone,website,"OpenStreetMap",lat,lon,false));
        }
        return out;
    }
}
