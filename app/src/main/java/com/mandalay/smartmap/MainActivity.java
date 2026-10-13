package com.mandalay.smartmap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Real installable Android MVP: on-device SQLite, Overpass imports, vector road map and favorites. */
public final class MainActivity extends Activity {
    private final int BLUE=Color.rgb(25,48,138), MID=Color.rgb(93,111,137);
    private MandalayDb db;
    private MapCanvas map;
    private TextView status, resultHeader;
    private EditText searchBox;
    private LinearLayout results;
    private Button syncButton, savedButton;
    private boolean favoritesOnly=false, busy=false;
    private List<Road> roads=new ArrayList<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db=new MandalayDb(this);
        buildScreen();
        // SQLite local contents are always usable after a prior successful internet sync.
        loadFromDisk();
    }

    private void buildScreen() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248,250,255));
        root.setPadding(dp(12),dp(12),dp(12),dp(8));
        setContentView(root);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);title.setText("MANDALAY SMART MAP");
        title.setTextSize(19);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(BLUE);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        Button near=makeButton("◎ GPS",false);header.addView(near);near.setOnClickListener(v->locateMe());
        root.addView(header);

        TextView subtitle=new TextView(this);
        subtitle.setText("မန္တလေး • Online + Offline • Pilot Version 0.1");
        subtitle.setTextColor(MID);subtitle.setTextSize(12);
        root.addView(subtitle);

        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(0,dp(9),0,dp(6));
        searchBox=new EditText(this);searchBox.setSingleLine(true);searchBox.setTextSize(15);
        searchBox.setHint("ဆိုင် / Hotpot / 41 လမ်း 83 လမ်း");
        searchBox.setPadding(dp(13),dp(7),dp(7),dp(7));
        searchBox.setBackground(bg(Color.WHITE,dp(12),Color.rgb(222,228,242)));
        searchRow.addView(searchBox,new LinearLayout.LayoutParams(0,dp(46),1));
        Button find=makeButton("ရှာမည်",true);find.setOnClickListener(v->search());
        LinearLayout.LayoutParams f=new LinearLayout.LayoutParams(-2,dp(46));f.leftMargin=dp(7);searchRow.addView(find,f);
        root.addView(searchRow);

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        syncButton=makeButton("↻ Sync Map",true);
        savedButton=makeButton("☆ Saved",false);
        Button add=makeButton("+ ဆိုင်ထည့်",false);
        Button online=makeButton("Web Search",false);
        actions.addView(syncButton,weightButton());actions.addView(savedButton,weightButton());
        actions.addView(add,weightButton());actions.addView(online,weightButton());
        root.addView(actions);
        syncButton.setOnClickListener(v->sync());
        savedButton.setOnClickListener(v->{favoritesOnly=!favoritesOnly;savedButton.setText(favoritesOnly?"★ All":"☆ Saved");search();});
        add.setOnClickListener(v->addManualPlace());
        online.setOnClickListener(v->webSearch());

        status=new TextView(this);status.setTextSize(12);status.setTextColor(MID);
        status.setPadding(0,dp(9),0,dp(7));root.addView(status);

        map=new MapCanvas(this);map.setMarkerListener(this::details);
        LinearLayout.LayoutParams mapParams=new LinearLayout.LayoutParams(-1,0,1);
        root.addView(map,mapParams);

        resultHeader=new TextView(this);resultHeader.setTextColor(BLUE);resultHeader.setTextSize(15);
        resultHeader.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        resultHeader.setPadding(0,dp(8),0,dp(4));root.addView(resultHeader);
        ScrollView scroller=new ScrollView(this);scroller.setFillViewport(true);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(results);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(176)));

        TextView credit=new TextView(this);
        credit.setText("Map: © OpenStreetMap contributors (ODbL) · Initial internet sync required");
        credit.setTextColor(MID);credit.setTextSize(10);root.addView(credit);
        credit.setOnClickListener(v->openUrl("https://www.openstreetmap.org/copyright"));
    }

    private void loadFromDisk(){
        roads=db.allRoads();map.setRoads(roads);
        updateCount();search();
    }

    private void updateCount(){
        status.setText("Offline: " + db.roadCount() + " road segments · " + db.placeCount() +
            " shops/places · downtown pilot zone");
    }

    private void search(){
        String keyword=searchBox.getText().toString().trim();
        hideKeyboard();
        if(!keyword.isEmpty()){
            Coordinate point=StreetFinder.find(roads,keyword);
            if(point!=null){map.focus(point.lat,point.lon);
                Toast.makeText(this,"လမ်းဆုံတည်နေရာ တွေ့ပါတယ်",Toast.LENGTH_SHORT).show();}
        }
        List<Place> found=db.searchPlaces(keyword,favoritesOnly,100);
        map.setPlaces(found);
        resultHeader.setText("ဆိုင်ရလဒ် ("+found.size()+(found.size()==100?"+":"")+")"+
                (favoritesOnly?" · Favorites":""));
        results.removeAllViews();
        if(found.isEmpty()){
            TextView no=new TextView(this);no.setText("ဒေတာမတွေ့ပါ။ Internet ရှိချိန် Sync Map နှိပ်ပါ။\nOSM မှာမရှိတဲ့ဆိုင်တွေကို + ဆိုင်ထည့် နဲ့ ကိုယ်တိုင်ထည့်နိုင်ပါတယ်။");
            no.setTextColor(MID);no.setTextSize(13);no.setPadding(dp(12),dp(14),dp(6),dp(14));results.addView(no);return;
        }
        for(Place p:found){
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12),dp(8),dp(12),dp(8));
            item.setBackground(bg(Color.WHITE,dp(9),Color.rgb(228,234,246)));
            TextView name=new TextView(this);name.setText((p.saved?"★ ":"📍 ")+p.name);
            name.setTextColor(BLUE);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setTextSize(14);
            item.addView(name);
            TextView desc=new TextView(this);
            desc.setText(p.type+(p.address.isEmpty()?"":" · "+p.address));
            desc.setTextColor(MID);desc.setTextSize(12);desc.setMaxLines(2);item.addView(desc);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(4);
            results.addView(item,lp);
            item.setOnClickListener(v->{map.focus(p.lat,p.lon);details(p);});
        }
    }

    private void details(Place p){
        String details="Category: "+p.type+"\n"+
             "Address: "+(p.address.isEmpty()?"Not provided in OSM":p.address)+"\n"+
             "Phone: "+(p.phone.isEmpty()?"Not provided in OSM":p.phone)+"\n"+
             "Coordinates: "+String.format(Locale.US,"%.6f, %.6f",p.lat,p.lon)+"\n"+
             "Source: "+p.source;
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(p.name).setMessage(details);
        b.setPositiveButton(p.saved?"★ Unsave":"☆ Save",(d,w)->{
            db.setSaved(p.id,!p.saved);search();});
        b.setNeutralButton("Map",(d,w)->map.focus(p.lat,p.lon));
        if(!p.phone.isEmpty()) b.setNegativeButton("Call",(d,w)->{
            Intent i=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+p.phone));
            try{startActivity(i);}catch(Exception ex){toast("Dialer not available");}
        });
        else b.setNegativeButton("Close",null);
        b.show();
    }

    private void sync(){
        if(busy)return;
        busy=true;syncButton.setEnabled(false);
        status.setText("Online download started…");
        executor.execute(()->{
            try{
                OsmClient.Result result=OsmClient.downloadAndImport(db,s->runOnUiThread(()->status.setText(s)));
                runOnUiThread(()->{
                    busy=false;syncButton.setEnabled(true);loadFromDisk();
                    toast("Synced "+result.roadCount+" road segments + "+result.placeCount+" places");
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    busy=false;syncButton.setEnabled(true);loadFromDisk();
                    new AlertDialog.Builder(this).setTitle("Sync failed")
                      .setMessage(e.getMessage()+"\n\nInternet or Overpass availability may be limited. Any previously downloaded map is retained.")
                      .setPositiveButton("OK",null).show();
                });
            }
        });
    }

    private void addManualPlace(){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18),0,dp(18),0);
        EditText name=field("ဆိုင်နာမည်", "");
        EditText kind=field("အမျိုးအစား (e.g. mobile_shop)", "shop");
        EditText address=field("လိပ်စာ", "");
        EditText phone=field("ဖုန်းနံပါတ်", "");
        EditText lat=field("Latitude", "21.977900");
        EditText lon=field("Longitude", "96.084500");
        for(EditText e:new EditText[]{name,kind,address,phone,lat,lon})fields.addView(e);
        ScrollView wrapper=new ScrollView(this);wrapper.addView(fields);
        new AlertDialog.Builder(this).setTitle("Add a local shop").setView(wrapper)
          .setPositiveButton("Save offline",(d,w)->{
              try{
                  double latitude=Double.parseDouble(lat.getText().toString()),longitude=Double.parseDouble(lon.getText().toString());
                  if(name.getText().toString().trim().isEmpty()||latitude < -90||latitude > 90||longitude < -180||longitude > 180) throw new IllegalArgumentException("Invalid place information");
                  String id="manual/"+UUID.randomUUID();
                  Place place=new Place(id,name.getText().toString().trim(),kind.getText().toString().trim(),
                      address.getText().toString().trim(),phone.getText().toString().trim(),"","User entry",latitude,longitude,true);
                  db.saveManualPlace(place);db.setSaved(id,true);updateCount();search();map.focus(latitude,longitude);
                  toast("Saved to this phone");
              }catch(Exception ex){toast("Please enter valid shop name and coordinates");}
          }).setNegativeButton("Cancel",null).show();
    }

    private void webSearch(){
        String q=searchBox.getText().toString().trim();
        if(q.isEmpty()){toast("Type shop name first");return;}
        try{openUrl("https://www.google.com/search?q="+URLEncoder.encode(q+" Mandalay Myanmar shop phone address","UTF-8"));}
        catch(Exception e){toast("Web search unavailable");}
    }

    private void locateMe(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},110);
            return;
        }
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location loc=null;
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                if(!lm.isProviderEnabled(provider))continue;
                Location got=lm.getLastKnownLocation(provider);
                if(got!=null && (loc==null||got.getTime()>loc.getTime()))loc=got;
            }
            if(loc!=null){map.focus(loc.getLatitude(),loc.getLongitude());toast("Last known location (may be old)");}
            else toast("No location fix yet. Enable GPS and retry.");
        }catch(Exception e){toast("Location not available");}
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==110 && results.length>0){
            boolean granted=false;for(int r:results)granted|=r==PackageManager.PERMISSION_GRANTED;
            if(granted)locateMe(); else toast("GPS permission denied");
        }
    }

    private void hideKeyboard(){
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null)imm.hideSoftInputFromWindow(searchBox.getWindowToken(),0);
    }
    private void openUrl(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){toast("No browser available");}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private EditText field(String hint,String def){
        EditText e=new EditText(this);e.setHint(hint);e.setText(def);e.setTextSize(14);e.setSingleLine(true);return e;
    }
    private LinearLayout.LayoutParams weightButton(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(39),1);p.rightMargin=dp(4);return p;
    }
    private Button makeButton(String text,boolean primary){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(11);
        b.setPadding(dp(5),0,dp(5),0);
        b.setTextColor(primary?Color.WHITE:BLUE);
        b.setBackground(bg(primary?BLUE:Color.WHITE,dp(10),primary?BLUE:Color.rgb(218,228,244)));
        return b;
    }
    private GradientDrawable bg(int color,int radius,int stroke){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);
        shape.setStroke(dp(1),stroke);return shape;
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();db.close();}
}
