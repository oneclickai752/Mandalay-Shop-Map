package com.mandalay.smartmap;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * User-triggered, small OpenStreetMap Nominatim search, for a PERSONAL PILOT only.
 * Not auto-complete, not bulk downloads, not Google/Facebook/TikTok extraction.
 * Production apps with many users must use an owned/licensed search provider.
 * License: OpenStreetMap contributors / ODbL.
 */
final class OnlinePlaceSearch {
    private static long lastRequestAt=0L;
    private OnlinePlaceSearch() {}

    static synchronized List<Place> search(String keyword) throws Exception {
        if(keyword.trim().isEmpty()) return new ArrayList<>();
        long wait=1500-(System.currentTimeMillis()-lastRequestAt);
        if(wait>0) Thread.sleep(wait);
        lastRequestAt=System.currentTimeMillis();

        String url="https://nominatim.openstreetmap.org/search?"
            +"format=jsonv2&addressdetails=1&extratags=1&limit=15"
            +"&countrycodes=mm&viewbox=95.94,22.18,96.28,21.78&bounded=1"
            +"&q="+URLEncoder.encode(keyword.trim(),"UTF-8");
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
        try {
            con.setRequestMethod("GET");
            con.setConnectTimeout(15000);
            con.setReadTimeout(30000);
            con.setRequestProperty("Accept","application/json");
            con.setRequestProperty("User-Agent","MandalaySmartMapPilot/0.3 (personal OSM-powered directory)");
            int response=con.getResponseCode();
            if(response!=200) throw new Exception("OSM online search HTTP "+response);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[8192]; int n,total=0;
            try(InputStream in=con.getInputStream()) {
                while((n=in.read(buf))!=-1) {
                    total+=n;
                    if(total>1024*1024) throw new Exception("Search result too large");
                    out.write(buf,0,n);
                }
            }
            JSONArray data=new JSONArray(out.toString("UTF-8"));
            List<Place> results=new ArrayList<>();
            for(int i=0;i<data.length();i++) {
                JSONObject row=data.getJSONObject(i);
                double lat=Double.parseDouble(row.getString("lat"));
                double lon=Double.parseDouble(row.getString("lon"));
                if(lat<21.78 || lat>22.18 || lon<95.94 || lon>96.28) continue;
                String full=row.optString("display_name","");
                String name=full.contains(",")?full.substring(0,full.indexOf(',')).trim():full.trim();
                if(name.isEmpty())continue;
                String osmType=row.optString("osm_type","").toLowerCase();
                String idType=osmType.equals("node")||osmType.equals("way")||osmType.equals("relation")
                    ? osmType : "nominatim";
                String id=idType+"/"+row.optString("osm_id",row.optString("place_id",""));
                JSONObject extra=row.optJSONObject("extratags");
                String phone=extra==null?"":extra.optString("contact:phone",extra.optString("phone",""));
                String site=extra==null?"":extra.optString("contact:website",extra.optString("website",""));
                String kind=row.optString("type",row.optString("category","place"));
                results.add(new Place(id,name,kind,full,phone,site,
                    "OpenStreetMap (Nominatim)",lat,lon,false));
            }
            return results;
        } finally {
            con.disconnect();
        }
    }
}
