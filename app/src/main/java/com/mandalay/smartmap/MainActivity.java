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
import android.location.LocationListener;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.content.res.ColorStateList;
import android.view.inputmethod.EditorInfo;
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
    private Button syncButton, savedButton, modeOffline, modeOnline;
    private TextView progressText;
    private ProgressBar syncProgress;
    private boolean activeOnline=false, onlineBusy=false;
    private List<Place> onlineResults=new ArrayList<>();
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
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,249,254));
        root.setPadding(dp(12),dp(5),dp(12),dp(6));
        setContentView(root);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleGroup=new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(this);
        title.setText("MANDALAY SMART MAP");
        title.setTextSize(18);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextColor(BLUE);titleGroup.addView(title);
        TextView subtitle=new TextView(this);
        subtitle.setText("Mandalay • Online + Offline • v0.3");
        subtitle.setTextSize(11);subtitle.setTextColor(MID);
        titleGroup.addView(subtitle);
        header.addView(titleGroup,new LinearLayout.LayoutParams(0,dp(57),1));
        Button near=makeButton("◎ GPS",false);
        near.setOnClickListener(v->locateMe());
        header.addView(near,new LinearLayout.LayoutParams(dp(70),dp(37)));
        root.addView(header);

        LinearLayout searchRow=new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchBox=new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextSize(14);
        searchBox.setHint("ဆိုင် / Hotpot / 41 လမ်း 83 လမ်း");
        searchBox.setPadding(dp(12),0,dp(10),0);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setBackground(bg(Color.WHITE,dp(11),Color.rgb(218,225,239)));
        searchRow.addView(searchBox,new LinearLayout.LayoutParams(0,dp(45),1));
        Button find=makeButton("ရှာမည်",true);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(dp(77),dp(45));
        fp.leftMargin=dp(7);
        searchRow.addView(find,fp);
        find.setOnClickListener(v->search());
        searchBox.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){ search();return true; }
            return false;
        });
        root.addView(searchRow);

        LinearLayout modeRow=new LinearLayout(this);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        modeRow.setPadding(0,dp(6),0,dp(4));
        modeOffline=makeButton("● Offline",true);
        modeOnline=makeButton("○ Online OSM",false);
        modeRow.addView(modeOffline,new LinearLayout.LayoutParams(0,dp(34),1));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(34),1);
        mp.leftMargin=dp(6);modeRow.addView(modeOnline,mp);
        modeOffline.setOnClickListener(v->selectMode(false));
        modeOnline.setOnClickListener(v->selectMode(true));
        root.addView(modeRow);

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        syncButton=makeButton("↻ Sync",true);
        savedButton=makeButton("☆ Saved",false);
        Button add=makeButton("+ ဆိုင်ထည့်",false);
        Button web=makeButton("🌐 Web",false);
        actions.addView(syncButton,weightButton());
        actions.addView(savedButton,weightButton());
        actions.addView(add,weightButton());
        actions.addView(web,weightButton());
        root.addView(actions);
        syncButton.setOnClickListener(v->sync());
        savedButton.setOnClickListener(v->{
            favoritesOnly=!favoritesOnly;
            savedButton.setText(favoritesOnly?"★ All":"☆ Saved");
            selectMode(false);
        });
        add.setOnClickListener(v->addManualPlace());
        web.setOnClickListener(v->webSearch());

        status=new TextView(this);
        status.setTextSize(11);
        status.setTextColor(MID);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPadding(dp(2),dp(6),dp(2),dp(4));
        root.addView(status);

        progressText=new TextView(this);
        progressText.setTextSize(11);progressText.setTextColor(BLUE);
        progressText.setSingleLine(true);
        progressText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        progressText.setVisibility(View.GONE);
        root.addView(progressText);

        syncProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        syncProgress.setIndeterminate(false);
        syncProgress.setMax(100);
        syncProgress.setProgressTintList(ColorStateList.valueOf(BLUE));
        syncProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(224,232,244)));
        syncProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(6));
        pp.bottomMargin=dp(6);
        root.addView(syncProgress,pp);

        map=new MapCanvas(this);
        map.setMarkerListener(this::details);
        LinearLayout.LayoutParams mapParams=new LinearLayout.LayoutParams(-1,0,1);
        mapParams.topMargin=dp(1);
        root.addView(map,mapParams);

        LinearLayout resultBar=new LinearLayout(this);
        resultBar.setGravity(Gravity.CENTER_VERTICAL);
        resultHeader=new TextView(this);
        resultHeader.setTextColor(BLUE);resultHeader.setTextSize(15);
        resultHeader.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        resultBar.addView(resultHeader,new LinearLayout.LayoutParams(0,dp(36),1));
        TextView label=new TextView(this);
        label.setText("Map + List");
        label.setTextSize(11);label.setTextColor(MID);
        resultBar.addView(label);
        root.addView(resultBar);

        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        results=new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(results);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(190)));

        TextView credit=new TextView(this);
        credit.setText("© OpenStreetMap contributors · ODbL • OSM Online results may be incomplete");
        credit.setTextSize(9);credit.setSingleLine(true);
        credit.setEllipsize(android.text.TextUtils.TruncateAt.END);
        credit.setTextColor(MID);
        credit.setOnClickListener(v->openUrl("https://www.openstreetmap.org/copyright"));
        root.addView(credit);
    }

    private void selectMode(boolean online) {
        activeOnline=online;
        modeOffline.setText(online?"○ Offline":"● Offline");
        modeOnline.setText(online?"● Online OSM":"○ Online OSM");
        modeOffline.setTextColor(online?BLUE:Color.WHITE);
        modeOffline.setBackground(bg(online?Color.WHITE:BLUE,dp(9),
            online?Color.rgb(218,228,244):BLUE));
        modeOnline.setTextColor(online?Color.WHITE:BLUE);
        modeOnline.setBackground(bg(online?BLUE:Color.WHITE,dp(9),
            online?BLUE:Color.rgb(218,228,244)));
        if(online) {
            renderResults(onlineResults,true);
        } else {
            searchOffline();
        }
    }

    private void updateSyncProgress(String stage,int completed,int total) {
        int percent=Math.max(0,Math.min(100,(int)Math.round(completed*100.0/Math.max(1,total))));
        syncProgress.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        syncProgress.setProgress(percent);
        progressText.setText(percent+"% • "+stage);
    }

    private void loadFromDisk(){
        roads=db.allRoads();map.setRoads(roads);
        updateCount();
        if(!activeOnline) searchOffline();
    }

    private void updateCount(){
        status.setText("Offline: " + db.roadCount() + " road segments · " + db.placeCount() +
            " shops/places · downtown pilot zone");
    }

    private void search() {
        hideKeyboard();
        if(activeOnline) {
            searchOnline();
        } else {
            searchOffline();
        }
    }

    private void searchOffline() {
        String keyword=searchBox.getText().toString().trim();
        if(!keyword.isEmpty()) {
            Coordinate point=StreetFinder.find(roads,keyword);
            if(point!=null) {
                map.focus(point.lat,point.lon);
                toast("လမ်းဆုံတည်နေရာ တွေ့ပါပြီ");
            }
        }
        renderResults(db.searchPlaces(keyword,favoritesOnly,100),false);
    }

    private void searchOnline() {
        final String keyword=searchBox.getText().toString().trim();
        if(keyword.isEmpty()) {
            toast("ရှာချင်တဲ့ ဆိုင်နာမည်ကို ရိုက်ပါ");
            return;
        }
        if(onlineBusy) return;
        onlineBusy=true;
        resultHeader.setText("Searching OpenStreetMap online…");
        results.removeAllViews();
        showMessage("Searching OSM online. This is not Google/Facebook/TikTok search.");
        executor.execute(()->{
            try {
                List<Place> found=OnlinePlaceSearch.search(keyword);
                runOnUiThread(()->{
                    onlineBusy=false;
                    onlineResults=found;
                    if(activeOnline) renderResults(found,true);
                    if(found.isEmpty())toast("OSM online: မတွေ့ပါ။ 🌐 Web ကိုစမ်းကြည့်နိုင်ပါတယ်။");
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    onlineBusy=false;
                    if(activeOnline) {
                        resultHeader.setText("Online search unavailable");
                        results.removeAllViews();
                        showMessage("OSM online search unavailable. Try again later or tap 🌐 Web.");
                    }
                    toast("Online search error: "+e.getMessage());
                });
            }
        });
    }

    private void showMessage(String message) {
        TextView empty=new TextView(this);
        empty.setText(message);
        empty.setTextSize(12);
        empty.setTextColor(MID);
        empty.setPadding(dp(12),dp(14),dp(10),dp(14));
        empty.setBackground(bg(Color.WHITE,dp(10),Color.rgb(230,236,245)));
        results.addView(empty);
    }

    private void renderResults(List<Place> found, boolean fromOnline) {
        map.setPlaces(found);
        resultHeader.setText((fromOnline?"Online OSM":"Offline")+" • Results ("+found.size()+")"
            +(favoritesOnly&&!fromOnline?" • Saved":""));
        results.removeAllViews();
        if(found.isEmpty()) {
            showMessage(fromOnline
                ? "OSM online မှာ ဒီဆိုင်ကို မတွေ့သေးပါ။ 🌐 Web Search ကိုစမ်းကြည့်ပါ။"
                : "Offline ထဲမှာ မတွေ့ပါ။ Online OSM သို့မဟုတ် 🌐 Web ကိုစမ်းကြည့်ပါ။ Sync လည်းလိုနိုင်ပါတယ်။");
            return;
        }
        for(Place place:found) {
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(11),dp(6),dp(6),dp(6));
            card.setBackground(bg(Color.WHITE,dp(10),Color.rgb(227,233,243)));

            LinearLayout labels=new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView name=new TextView(this);
            name.setText(place.name);
            name.setTextColor(BLUE);
            name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            name.setTextSize(13);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(name);
            TextView summary=new TextView(this);
            String line=place.type;
            if(!place.address.isEmpty())line+=" • "+place.address;
            if(!place.phone.isEmpty())line+=" • ☎ "+place.phone;
            summary.setText(line);
            summary.setTextColor(MID);
            summary.setTextSize(11);
            summary.setSingleLine(true);
            summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(summary);
            card.addView(labels,new LinearLayout.LayoutParams(0,dp(43),1));
            Button bookmark=makeButton(place.saved?"★":"☆",false);
            bookmark.setTextSize(17);
            card.addView(bookmark,new LinearLayout.LayoutParams(dp(38),dp(35)));
            bookmark.setOnClickListener(v->saveToggle(place,fromOnline));
            labels.setOnClickListener(v->{
                map.focus(place.lat,place.lon);
                details(place);
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.bottomMargin=dp(5);
            results.addView(card,lp);
        }
    }

    private void saveToggle(Place place, boolean fromOnline) {
        boolean save=!place.saved;
        if(fromOnline) {
            if(save) db.saveManualPlace(place);
            db.setSaved(place.id,save);
            place.saved=save;
            updateCount();
            renderResults(onlineResults,true);
        } else {
            db.setSaved(place.id,save);
            searchOffline();
        }
        toast(save?"Saved offline":"Removed from Saved");
    }

    private void details(Place place) {
        boolean fromOnline=activeOnline && onlineResults.contains(place);
        String content="Category: "+place.type+
            "\nAddress: "+(place.address.isEmpty()?"Not listed":place.address)+
            "\nPhone: "+(place.phone.isEmpty()?"Not listed":place.phone)+
            "\nCoordinates: "+String.format(Locale.US,"%.6f, %.6f",place.lat,place.lon)+
            "\nSource: "+place.source;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this)
            .setTitle(place.name).setMessage(content);
        dialog.setPositiveButton(place.saved?"★ Unsave":"☆ Save offline",
            (d,w)->saveToggle(place,fromOnline));
        dialog.setNeutralButton("Map",(d,w)->map.focus(place.lat,place.lon));
        if(!place.phone.isEmpty()) {
            dialog.setNegativeButton("Call",(d,w)->{
                Intent intent=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+place.phone));
                try{startActivity(intent);}catch(Exception e){toast("Dialer not available");}
            });
        } else {
            dialog.setNegativeButton("Close",null);
        }
        dialog.show();
    }

    private void sync() {
        if(busy)return;
        busy=true;
        syncButton.setEnabled(false);
        updateSyncProgress("Starting download (8 area/type steps)…",0,8);
        executor.execute(()->{
            try {
                OsmClient.Result result=OsmClient.downloadAndImport(db,
                    (stage,completed,total)->runOnUiThread(()->{
                        updateSyncProgress(stage,completed,total);
                        if(completed>0 && completed%2==0) loadFromDisk();
                    }));
                runOnUiThread(()->{
                    busy=false;
                    syncButton.setEnabled(true);
                    loadFromDisk();
                    updateSyncProgress(result.isPartial()
                        ?"Finished 8 steps, some areas missing — retry later"
                        :"All 8 steps completed; offline data saved",8,8);
                    toast("Saved "+result.roadCount+" roads + "+result.placeCount+
                        " places"+(result.isPartial()?" (partial)":""));
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    busy=false;syncButton.setEnabled(true);loadFromDisk();
                    progressText.setText("Download stopped • saved data retained");
                    new AlertDialog.Builder(this).setTitle("Sync failed")
                        .setMessage(e.getMessage()+"\\nAlready downloaded data, if any, is preserved.")
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
            final LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(lm==null){ toast("Location service unavailable"); return; }
            Location recent=null;
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                if(!lm.isProviderEnabled(provider)) continue;
                Location last=lm.getLastKnownLocation(provider);
                if(last!=null && (recent==null || last.getTime()>recent.getTime())) recent=last;
            }
            if(recent!=null && System.currentTimeMillis()-recent.getTime()<5*60*1000){
                map.focus(recent.getLatitude(),recent.getLongitude());
                toast("Location found");
                return;
            }
            final String provider=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER
                : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:null);
            if(provider==null){toast("Turn on Location in phone settings");return;}
            toast("Searching for current location — keep Location on…");
            final boolean[] finished={false};
            final Handler handler=new Handler(Looper.getMainLooper());
            final LocationListener listener=new LocationListener(){
                @Override public void onLocationChanged(Location location){
                    if(finished[0])return;
                    finished[0]=true;
                    map.focus(location.getLatitude(),location.getLongitude());
                    toast("Location found");
                }
            };
            lm.requestSingleUpdate(provider,listener,Looper.getMainLooper());
            handler.postDelayed(()->{
                if(!finished[0]){
                    finished[0]=true;
                    lm.removeUpdates(listener);
                    toast("No GPS fix. Try outside with Location enabled.");
                }
            },20000);
        }catch(Exception e){toast("Location unavailable. Check permissions and settings.");}
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
