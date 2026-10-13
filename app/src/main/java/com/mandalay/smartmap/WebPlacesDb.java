package com.mandalay.smartmap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Separate user-owned web bookmarks; does not copy data from Google Places. */
final class WebPlacesDb extends SQLiteOpenHelper {
    WebPlacesDb(Context context) { super(context, "saved_web_shops.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE saved_web (id INTEGER PRIMARY KEY AUTOINCREMENT, "
            +"name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', "
            +"notes TEXT NOT NULL DEFAULT '', source_url TEXT NOT NULL DEFAULT '', "
            +"latitude REAL, longitude REAL, saved_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_web_name ON saved_web(name)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }

    long save(String name,String address,String phone,String notes,String sourceUrl,
              Double latitude,Double longitude) {
        ContentValues values=new ContentValues();
        values.put("name",name.trim());
        values.put("address",address.trim());
        values.put("phone",phone.trim());
        values.put("notes",notes.trim());
        values.put("source_url",sourceUrl.trim());
        if(latitude==null || longitude==null) {
            values.putNull("latitude");
            values.putNull("longitude");
        } else {
            values.put("latitude",latitude);
            values.put("longitude",longitude);
        }
        values.put("saved_at",System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("saved_web",null,values);
    }

    void update(long id,String name,String address,String phone,String notes,
                String sourceUrl,Double latitude,Double longitude) {
        ContentValues values=new ContentValues();
        values.put("name",name.trim());
        values.put("address",address.trim());
        values.put("phone",phone.trim());
        values.put("notes",notes.trim());
        values.put("source_url",sourceUrl.trim());
        if(latitude==null || longitude==null) {
            values.putNull("latitude");values.putNull("longitude");
        } else {
            values.put("latitude",latitude);values.put("longitude",longitude);
        }
        getWritableDatabase().update("saved_web",values,"id=?",new String[]{Long.toString(id)});
    }

    void delete(long id) {
        getWritableDatabase().delete("saved_web","id=?",new String[]{Long.toString(id)});
    }

    int count() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM saved_web",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }

    List<SavedWebPlace> find(String search) {
        List<SavedWebPlace> items=new ArrayList<>();
        String where="(name LIKE ? OR address LIKE ? OR notes LIKE ? OR phone LIKE ?) COLLATE NOCASE";
        String token="%"+search.trim().toLowerCase(Locale.ROOT)+"%";
        try(Cursor c=getReadableDatabase().query("saved_web",
                new String[]{"id","name","address","phone","notes","source_url",
                             "latitude","longitude","saved_at"},
                where,new String[]{token,token,token,token},null,null,"saved_at DESC","300")){
            while(c.moveToNext()){
                items.add(new SavedWebPlace(c.getLong(0),c.getString(1),c.getString(2),
                    c.getString(3),c.getString(4),c.getString(5),
                    c.isNull(6)?null:c.getDouble(6),c.isNull(7)?null:c.getDouble(7),
                    c.getLong(8)));
            }
        }
        return items;
    }
}

final class SavedWebPlace {
    final long id,savedAt;
    final String name,address,phone,notes,url;
    final Double latitude,longitude;
    SavedWebPlace(long id,String name,String address,String phone,String notes,
                  String url,Double latitude,Double longitude,long savedAt) {
        this.id=id;this.name=name;this.address=address;this.phone=phone;
        this.notes=notes;this.url=url;this.latitude=latitude;
        this.longitude=longitude;this.savedAt=savedAt;
    }
}
