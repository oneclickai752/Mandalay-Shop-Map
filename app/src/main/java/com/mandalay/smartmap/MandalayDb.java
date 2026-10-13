package com.mandalay.smartmap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MandalayDb extends SQLiteOpenHelper {
    MandalayDb(Context context) { super(context, "mandalay_places.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE places (id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT, address TEXT, phone TEXT, website TEXT, source TEXT, lat REAL, lon REAL, saved INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_places_name ON places(name)");
        db.execSQL("CREATE TABLE roads (id TEXT PRIMARY KEY, name TEXT, highway TEXT, geometry TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    void replaceRoads(List<Road> roads) throws JSONException {
        if (roads.isEmpty()) throw new IllegalArgumentException("Road data is empty");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("roads", null, null);
            for (Road r : roads) {
                JSONArray geom = new JSONArray();
                for (int i = 0; i < r.latitudes.length; i++) {
                    JSONArray p = new JSONArray(); p.put(r.latitudes[i]); p.put(r.longitudes[i]); geom.put(p);
                }
                ContentValues v = new ContentValues();
                v.put("id", r.id); v.put("name", r.name); v.put("highway", r.highway); v.put("geometry", geom.toString());
                db.insertOrThrow("roads", null, v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void mergePlaces(List<Place> places) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Place p : places) {
                ContentValues v = new ContentValues();
                v.put("id", p.id); v.put("name", p.name); v.put("type", p.type);
                v.put("address", p.address); v.put("phone", p.phone); v.put("website", p.website);
                v.put("source", p.source); v.put("lat", p.lat); v.put("lon", p.lon);
                // Existing favorites are preserved: the UPDATE below never changes saved.
                db.insertWithOnConflict("places", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                db.update("places", v, "id=?", new String[]{p.id});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    void saveManualPlace(Place p) { mergePlaces(java.util.Collections.singletonList(p)); }

    void setSaved(String id, boolean saved) {
        ContentValues v = new ContentValues(); v.put("saved", saved ? 1 : 0);
        getWritableDatabase().update("places", v, "id=?", new String[]{id});
    }

    List<Place> searchPlaces(String text, boolean favoritesOnly, int limit) {
        String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        String where = "(lower(name) LIKE ? OR lower(type) LIKE ? OR lower(address) LIKE ?)" + (favoritesOnly ? " AND saved=1" : "");
        return loadPlaces(where, new String[]{like, like, like}, limit);
    }

    List<Place> allPlaces(int limit) { return loadPlaces(null, null, limit); }

    private List<Place> loadPlaces(String where, String[] args, int limit) {
        List<Place> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("places", new String[]{"id","name","type","address","phone","website","source","lat","lon","saved"},
                where, args, null, null, "saved DESC, name COLLATE NOCASE", Integer.toString(limit))) {
            while (c.moveToNext()) {
                out.add(new Place(c.getString(0),c.getString(1),safe(c.getString(2)),safe(c.getString(3)),safe(c.getString(4)),
                        safe(c.getString(5)),safe(c.getString(6)),c.getDouble(7),c.getDouble(8),c.getInt(9)==1));
            }
        }
        return out;
    }

    List<Road> allRoads() {
        List<Road> roads = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("roads", new String[]{"id","name","highway","geometry"},null,null,null,null,null)) {
            while (c.moveToNext()) {
                try {
                    JSONArray arr = new JSONArray(c.getString(3));
                    double[] lats = new double[arr.length()], lons = new double[arr.length()];
                    for (int i=0;i<arr.length();i++) {
                        JSONArray p = arr.getJSONArray(i); lats[i] = p.getDouble(0); lons[i] = p.getDouble(1);
                    }
                    roads.add(new Road(c.getString(0),safe(c.getString(1)),safe(c.getString(2)),lats,lons));
                } catch (JSONException ignored) { /* Skip corrupt geometry only. */ }
            }
        }
        return roads;
    }

    int placeCount() { return count("places"); }
    int roadCount() { return count("roads"); }

    private int count(String table) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    private static String safe(String s) { return s==null?"":s; }
}
