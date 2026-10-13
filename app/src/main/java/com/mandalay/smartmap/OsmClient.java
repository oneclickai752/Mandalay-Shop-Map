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

/**
 * An on-demand, explicitly user-triggered OSM dataset import for a downtown Mandalay pilot zone.
 * Public Overpass is a demo source only. Use an owned backend / extract for a production app.
 */
final class OsmClient {
    // Pilot extent covers central Mandalay, NOT all of Mandalay District.
    static final double SOUTH=21.935, WEST=96.045, NORTH=22.045, EAST=96.135;
    private static final String BBOX="(21.935,96.045,22.045,96.135)";
    private static final String ENDPOINT="https://overpass-api.de/api/interpreter";

    interface Progress { void onProgress(String state); }
    static final class Result { int roadCount, placeCount; }

    static Result downloadAndImport(MandalayDb db, Progress progress) throws Exception {
        // Named and unnamed streets are both drawn; named ones can be searched as intersections.
        progress.onProgress("Downloading Mandalay roads (first import needs internet)…");
        JSONObject roadsJson = request("[out:json][timeout:90];way[\"highway\"]"+BBOX+";out geom;");
        List<Road> roads = parseRoads(roadsJson);
        if (roads.isEmpty()) throw new Exception("Overpass returned no roads; local data was not replaced.");
        progress.onProgress("Saving " + roads.size() + " road segments…");
        db.replaceRoads(roads);

        progress.onProgress("Downloading business records…");
        String q = "[out:json][timeout:90];("+
            "nwr[\"shop\"]"+BBOX+";"+
            "nwr[\"amenity\"~\"restaurant|cafe|fast_food|pharmacy|bank|hospital|clinic|fuel|marketplace\"]"+BBOX+";"+
            "nwr[\"tourism\"~\"hotel|guest_house|hostel\"]"+BBOX+";"+
            ");out center;";
        List<Place> places = parsePlaces(request(q));
        progress.onProgress("Saving " + places.size() + " businesses…");
        db.mergePlaces(places);
        Result result = new Result(); result.roadCount=roads.size(); result.placeCount=places.size();
        return result;
    }

    private static JSONObject request(String overpassQuery) throws Exception {
        HttpURLConnection con=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        try {
            con.setRequestMethod("POST"); con.setDoOutput(true);
            con.setConnectTimeout(20000); con.setReadTimeout(120000);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            con.setRequestProperty("User-Agent", "MandalaySmartMapPilot/0.1 (personal research prototype)");
            byte[] body=("data="+URLEncoder.encode(overpassQuery,"UTF-8")).getBytes(StandardCharsets.UTF_8);
            try(OutputStream os=con.getOutputStream()){os.write(body);}
            int code=con.getResponseCode();
            if(code!=200) throw new Exception("Overpass HTTP "+code+". Try again later or switch connection.");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[16384]; int n; int total=0;
            try(InputStream in=con.getInputStream()){
                while((n=in.read(buf))!=-1){
                    total+=n;
                    if(total>32*1024*1024) throw new Exception("Map response too large for pilot import");
                    out.write(buf,0,n);
                }
            }
            return new JSONObject(out.toString("UTF-8"));
        } finally {con.disconnect();}
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
