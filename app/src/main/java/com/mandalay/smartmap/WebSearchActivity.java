package com.mandalay.smartmap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Real in-app web browser. No automated scraping of Google/Facebook/TikTok results.
 * The user explicitly reviews and saves individual public business records.
 */
public final class WebSearchActivity extends Activity {
    private final int NAVY=Color.rgb(25,48,138),GRAY=Color.rgb(90,109,137);
    private WebView web;
    private EditText query;
    private ProgressBar loading;
    private TextView locationLabel;
    private String engine="Google";
    private WebPlacesDb db;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db=new WebPlacesDb(this);
        makeUi();
        String initialUrl=getIntent().getStringExtra("url");
        String initialQuery=getIntent().getStringExtra("query");
        if(initialQuery!=null)query.setText(initialQuery);
        if(safeHttpUrl(initialUrl))web.loadUrl(initialUrl);
        else if(initialQuery!=null&&!initialQuery.trim().isEmpty())search();
        else searchHome();
    }

    private static boolean safeHttpUrl(String url) {
        if(url==null)return false;
        try {
            Uri parsed=Uri.parse(url);
            String scheme=parsed.getScheme();
            return scheme!=null&&(scheme.equalsIgnoreCase("http")||scheme.equalsIgnoreCase("https"));
        }catch(Exception e){return false;}
    }

    private void makeUi() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(5),dp(10),dp(7));
        root.setBackgroundColor(Color.rgb(248,250,254));
        setContentView(root);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=new TextView(this);
        brand.setText("WEB SEARCH • MANDALAY");
        brand.setTextColor(NAVY);brand.setTextSize(15);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(41),1));
        Button library=btn("★ Saved",false);
        library.setOnClickListener(v->startActivity(new Intent(this,SavedWebActivity.class)));
        header.addView(library,new LinearLayout.LayoutParams(dp(97),dp(35)));
        root.addView(header);

        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        query=new EditText(this);query.setSingleLine(true);query.setTextSize(14);
        query.setHint("ဆိုင် / ဖုန်း / လိပ်စာ ရှာရန်");
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setPadding(dp(12),0,dp(10),0);
        query.setBackground(shape(Color.WHITE,Color.rgb(213,225,242)));
        query.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){search();return true;}
            return false;
        });
        bar.addView(query,new LinearLayout.LayoutParams(0,dp(43),1));
        Button go=btn("Search",true);
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(80),dp(43));
        gp.leftMargin=dp(6);bar.addView(go,gp);
        go.setOnClickListener(v->search());
        root.addView(bar);

        LinearLayout sources=new LinearLayout(this);
        sources.setPadding(0,dp(5),0,dp(5));
        for(String source:new String[]{"Google","Facebook","TikTok"}) {
            Button b=btn(source,source.equals("Google"));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(32),1);
            p.rightMargin=dp(5);
            sources.addView(b,p);
            b.setOnClickListener(v->{
                engine=source;
                for(int i=0;i<sources.getChildCount();i++){
                    Button child=(Button)sources.getChildAt(i);
                    boolean selected=child.getText().toString().equals(engine);
                    child.setTextColor(selected?Color.WHITE:NAVY);
                    child.setBackground(shape(selected?NAVY:Color.WHITE,
                        selected?NAVY:Color.rgb(213,225,242)));
                }
                search();
            });
        }
        root.addView(sources);

        LinearLayout controls=new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Button back=btn("‹ Back",false);
        Button save=btn("☆ Save shop",true);
        Button external=btn("↗ Open",false);
        controls.addView(back,new LinearLayout.LayoutParams(0,dp(36),1));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(36),1.4f);
        sp.leftMargin=dp(5);controls.addView(save,sp);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(36),1);
        ep.leftMargin=dp(5);controls.addView(external,ep);
        back.setOnClickListener(v->{if(web.canGoBack())web.goBack();else finish();});
        save.setOnClickListener(v->saveCurrentPage());
        external.setOnClickListener(v->{
            String url=web.getUrl();
            if(safeHttpUrl(url)) {
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
                catch(Exception e){toast("No browser available");}
            }
        });
        root.addView(controls);

        locationLabel=new TextView(this);
        locationLabel.setSingleLine(true);
        locationLabel.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        locationLabel.setTextSize(10);
        locationLabel.setTextColor(GRAY);
        locationLabel.setPadding(dp(3),dp(4),dp(3),dp(3));
        root.addView(locationLabel);

        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);loading.setVisibility(View.GONE);
        root.addView(loading,new LinearLayout.LayoutParams(-1,dp(3)));

        web=new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if(android.os.Build.VERSION.SDK_INT>=26)web.getSettings().setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();
                String scheme=u.getScheme();
                if(scheme!=null&&(scheme.equals("https")||scheme.equals("http")))return false;
                return true;
            }
            @Override public void onPageFinished(WebView view,String url){
                locationLabel.setText(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int value){
                loading.setProgress(value);
                loading.setVisibility(value>=100?View.GONE:View.VISIBLE);
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));

        TextView hint=new TextView(this);
        hint.setText("Open a shop page → ☆ Save shop → check phone/address → Save offline. " +
            "Google/Facebook/TikTok pages are not automatically imported.");
        hint.setTextSize(10);hint.setTextColor(GRAY);
        hint.setPadding(dp(3),dp(4),dp(3),dp(1));
        root.addView(hint);
    }

    private void searchHome() {
        web.loadUrl("https://www.google.com/");
    }

    private void search() {
        String input=query.getText().toString().trim();
        if(input.isEmpty()){toast("ဆိုင်နာမည်ကို အရင်ရိုက်ပါ");return;}
        String phrase=input+" Mandalay Myanmar";
        try {
            String encoded=URLEncoder.encode(phrase,"UTF-8");
            String url;
            if(engine.equals("Facebook")) {
                url="https://www.google.com/search?q="+URLEncoder.encode(
                    "site:facebook.com "+phrase,"UTF-8");
            } else if(engine.equals("TikTok")) {
                url="https://www.google.com/search?q="+URLEncoder.encode(
                    "site:tiktok.com "+phrase,"UTF-8");
            } else {
                url="https://www.google.com/search?q="+encoded;
            }
            web.loadUrl(url);
        } catch(Exception e){toast("Search unavailable");}
    }

    private void saveCurrentPage() {
        String url=web.getUrl();
        if(!safeHttpUrl(url)){toast("အရင် Website တစ်ခုဖွင့်ပါ");return;}
        // On a normal business website, try standardized LocalBusiness JSON-LD metadata.
        // Search engines and social pages are NOT parsed/scraped.
        Uri uri=Uri.parse(url);
        String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
        String initial=web.getTitle()==null?"":web.getTitle();
        String[] blocked={"google.","facebook.","instagram.","tiktok.","bing."};
        boolean canReadMetadata=true;
        for(String b:blocked)if(host.contains(b))canReadMetadata=false;
        if(!canReadMetadata) {
            showSaveDialog(initial,url,"","","");
            return;
        }
        final String fallback=initial;
        final String source=url;
        String script="(function(){var a={name:document.title||'',phone:'',address:'',notes:''};"
           +"try{var m=document.querySelector('meta[name=description]');if(m)a.notes=(m.content||'').slice(0,500);"
           +"var els=document.querySelectorAll('script[type=\\\"application/ld+json\\\"]');"
           +"for(var i=0;i<els.length;i++){try{var ob=JSON.parse(els[i].textContent);"
           +"var nodes=Array.isArray(ob)?ob:(ob['@graph']||[ob]);"
           +"for(var j=0;j<nodes.length;j++){var o=nodes[j],t=o['@type']||'',"
           +"type=Array.isArray(t)?t.join(' '):String(t);"
           +"if(/LocalBusiness|Store|Restaurant|Organization/i.test(type)){"
           +"a.name=o.name||a.name;a.phone=o.telephone||a.phone;"
           +"if(typeof o.address==='string')a.address=o.address;"
           +"else if(o.address)a.address=[o.address.streetAddress,o.address.addressLocality,o.address.addressRegion].filter(Boolean).join(', ');"
           +"break;}}}catch(e){}}}catch(e){}return JSON.stringify(a);})()";
        web.evaluateJavascript(script,value->{
            String name=fallback,phone="",address="",notes="";
            try {
                String decoded=new JSONArray("["+value+"]").getString(0);
                JSONObject data=new JSONObject(decoded);
                name=data.optString("name",fallback);
                phone=data.optString("phone","");
                address=data.optString("address","");
                notes=data.optString("notes","");
            }catch(Exception ignored){}
            showSaveDialog(name,source,phone,address,notes);
        });
    }

    private void showSaveDialog(String name,String url,String phone,String address,String notes) {
        LinearLayout fields=new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18),0,dp(18),0);
        EditText title=field("ဆိုင်နာမည် *",name);
        EditText tel=field("ဖုန်းနံပါတ် (စစ်ဆေးပြီးဖြည့်ပါ)",phone);
        EditText addressField=field("လိပ်စာ",address);
        EditText note=field("မှတ်စု / ကူးယူထားသော အချက်အလက်",notes);
        note.setSingleLine(false);note.setMinLines(2);
        EditText link=field("Source website link",url);
        EditText lat=field("Latitude (optional, for map pin)","");
        EditText lon=field("Longitude (optional, for map pin)","");
        for(EditText e:new EditText[]{title,tel,addressField,note,link,lat,lon})fields.addView(e);
        TextView help=new TextView(this);
        help.setText("Automatic metadata may be missing or incorrect. Review details before saving. " +
            "Copy relevant text from the website and paste it into Notes if needed.");
        help.setTextSize(12);help.setTextColor(GRAY);
        fields.addView(help);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("☆ Save shop offline")
            .setView(scroll)
            .setPositiveButton("Save",null)
            .setNegativeButton("Cancel",null)
            .create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=title.getText().toString().trim();
            if(n.isEmpty()){title.setError("Required");return;}
            try{
                Double latitude=null,longitude=null;
                String a=lat.getText().toString().trim(),b=lon.getText().toString().trim();
                if(!a.isEmpty()||!b.isEmpty()){
                    latitude=Double.parseDouble(a);longitude=Double.parseDouble(b);
                    if(latitude< -90||latitude>90||longitude< -180||longitude>180)throw new Exception("Invalid coordinates");
                }
                String linkValue=link.getText().toString().trim();
                if(!linkValue.isEmpty()&&!safeHttpUrl(linkValue))throw new Exception("Link must be http(s)");
                db.save(n,addressField.getText().toString(),tel.getText().toString(),
                    note.getText().toString(),linkValue,latitude,longitude);
                toast("Saved offline. Open ★ Saved to view without internet.");
                dialog.dismiss();
            }catch(Exception ex){toast("Please check coordinates or URL");}
        }));
        dialog.show();
    }

    private EditText field(String hint,String value){
        EditText e=new EditText(this);
        e.setHint(hint);e.setText(value==null?"":value);
        e.setTextSize(14);e.setSingleLine(true);
        e.setPadding(dp(2),dp(8),dp(2),dp(8));
        return e;
    }

    private GradientDrawable shape(int fill,int stroke){
        GradientDrawable g=new GradientDrawable();
        g.setColor(fill);g.setCornerRadius(dp(9));g.setStroke(dp(1),stroke);
        return g;
    }

    private Button btn(String text,boolean primary){
        Button b=new Button(this);
        b.setText(text);b.setAllCaps(false);
        b.setTextSize(12);b.setPadding(dp(3),0,dp(3),0);
        b.setTextColor(primary?Color.WHITE:NAVY);
        b.setBackground(shape(primary?NAVY:Color.WHITE,
            primary?NAVY:Color.rgb(216,225,240)));
        return b;
    }

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){
        if(web!=null && web.canGoBack())web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(web!=null){web.stopLoading();web.destroy();}
        if(db!=null)db.close();
        super.onDestroy();
    }
}
