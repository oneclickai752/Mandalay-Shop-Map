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
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Full user-owned, offline searchable web shop records. */
public final class SavedWebActivity extends Activity {
    private final int BLUE=Color.rgb(25,48,138),GRAY=Color.rgb(91,109,135);
    private WebPlacesDb db;
    private EditText filter;
    private LinearLayout rows;
    private TextView total;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        db=new WebPlacesDb(this);
        makeUi();
        String text=getIntent().getStringExtra("query");
        if(text!=null && !text.isEmpty()) filter.setText(text);
    }

    @Override protected void onResume(){super.onResume();refresh();}

    private void makeUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,249,254));
        root.setPadding(dp(12),dp(9),dp(12),dp(9));
        setContentView(root);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText("★ SAVED SHOPS");
        title.setTextColor(BLUE);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextSize(19);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button back=button("← Back",false);
        back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(85),dp(35)));
        root.addView(header);

        TextView hint=new TextView(this);
        hint.setText("Saved on this phone • Available without Internet");
        hint.setTextSize(12);hint.setTextColor(GRAY);root.addView(hint);

        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setPadding(0,dp(10),0,dp(7));
        filter=new EditText(this);
        filter.setSingleLine(true);filter.setTextSize(14);
        filter.setHint("ဆိုင်နာမည် / ဖုန်း / လိပ်စာ");
        filter.setPadding(dp(12),0,dp(6),0);
        filter.setBackground(shape(Color.WHITE,Color.rgb(215,227,241)));
        toolbar.addView(filter,new LinearLayout.LayoutParams(0,dp(44),1));
        Button online=button("+ Web Search",true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(116),dp(44));
        lp.leftMargin=dp(7);toolbar.addView(online,lp);
        online.setOnClickListener(v->startActivity(new Intent(this,WebSearchActivity.class)));
        root.addView(toolbar);
        total=new TextView(this);
        total.setTextColor(GRAY);total.setTextSize(12);
        total.setPadding(0,dp(5),0,dp(9));
        root.addView(total);

        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(true);
        rows=new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(rows);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){refresh();}
            @Override public void afterTextChanged(Editable e){}
        });
    }

    private void refresh(){
        if(db==null || rows==null || filter==null)return;
        List<SavedWebPlace> items=db.find(filter.getText().toString());
        total.setText(items.size()+" matching saved records • "+db.count()+" total");
        rows.removeAllViews();
        if(items.isEmpty()){
            TextView empty=new TextView(this);
            empty.setText("မသိမ်းရသေးပါ။ Web Search မှာ ဆိုင်ကိုဖွင့်ပြီး ☆ Save shop နှိပ်ပါ။");
            empty.setTextColor(GRAY);empty.setTextSize(14);
            empty.setPadding(dp(14),dp(22),dp(14),dp(22));
            rows.addView(empty);
            return;
        }
        for(SavedWebPlace place:items) addCard(place);
    }

    private void addCard(SavedWebPlace place){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11),dp(9),dp(11),dp(8));
        card.setBackground(shape(Color.WHITE,Color.rgb(220,229,244)));

        TextView title=new TextView(this);
        title.setText(place.name);
        title.setTextColor(BLUE);title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(title);

        TextView info=new TextView(this);
        String summary=(place.address.isEmpty()?"No address saved":place.address);
        if(!place.phone.isEmpty()) summary+="   ☎ "+place.phone;
        info.setText(summary);
        info.setTextColor(GRAY);info.setTextSize(12);info.setMaxLines(2);
        card.addView(info);

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button detail=button("Details",true);
        Button call=button("Call",false);
        Button source=button("Source",false);
        Button delete=button("Delete",false);
        actions.addView(detail,new LinearLayout.LayoutParams(0,dp(35),1.2f));
        if(!place.phone.isEmpty()) {
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(35),.85f);
            cp.leftMargin=dp(5);
            actions.addView(call,cp);
            call.setOnClickListener(v->{
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL,
                        Uri.parse("tel:"+place.phone)));
                } catch(Exception e){toast("Dialer not available");}
            });
        }
        LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(0,dp(35),1);
        ml.leftMargin=dp(5);actions.addView(source,ml);
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,dp(35),.95f);
        dl.leftMargin=dp(5);actions.addView(delete,dl);
        card.addView(actions);
        detail.setOnClickListener(v->showDetails(place));
        source.setOnClickListener(v->openSource(place.url));
        delete.setOnClickListener(v->
            new AlertDialog.Builder(this).setTitle("Delete saved record?")
                .setMessage(place.name)
                .setPositiveButton("Delete",(d,w)->{db.delete(place.id);refresh();})
                .setNegativeButton("Cancel",null).show());

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(8);
        rows.addView(card,p);
    }

    private void showDetails(SavedWebPlace p){
        String details="Name: "+p.name+"\n"
           +"Address: "+(p.address.isEmpty()?"Not saved":p.address)+"\n"
           +"Phone: "+(p.phone.isEmpty()?"Not saved":p.phone)+"\n"
           +"Notes: "+(p.notes.isEmpty()?"None":p.notes)+"\n"
           +"Source URL: "+(p.url.isEmpty()?"None":p.url);
        if(p.latitude!=null && p.longitude!=null)details+="\nMap: "+p.latitude+", "+p.longitude;
        final String savedDetails=details;
        new AlertDialog.Builder(this)
            .setTitle(p.name)
            .setMessage(details)
            .setPositiveButton("Copy details",(d,w)->{
                ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(
                    ClipData.newPlainText("Saved Mandalay shop", savedDetails));
                toast("Copied saved details");
            })
            .setNeutralButton("Open source",(d,w)->openSource(p.url))
            .setNegativeButton("Close",null)
            .show();
    }

    private void openSource(String source){
        if(source==null||source.isEmpty()){
            toast("No source link saved");
            return;
        }
        Intent intent=new Intent(this,WebSearchActivity.class);
        intent.putExtra("url",source);
        startActivity(intent);
    }

    private GradientDrawable shape(int color,int border){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(10));g.setStroke(dp(1),border);
        return g;
    }

    private Button button(String label,boolean primary){
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(primary?Color.WHITE:BLUE);
        b.setBackground(shape(primary?BLUE:Color.WHITE,
            primary?BLUE:Color.rgb(216,225,240)));
        return b;
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){db.close();super.onDestroy();}
}
