package com.mandalay.smartmap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Lightweight native offline street renderer, without web maps or tile scraping. */
final class MapCanvas extends View {
    interface MarkerListener { void onMarker(Place p); }
    private List<Road> roads=new ArrayList<>();
    private List<Place> places=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleGesture;
    private MarkerListener markerListener;
    private double centerLat=21.9779,centerLon=96.0845, zoom=1.0;
    private double focusLat=Double.NaN,focusLon=Double.NaN;
    private float downX,downY,lastX,lastY;
    private boolean moved;

    MapCanvas(Context context){
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        scaleGesture=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                zoom=Math.max(0.7,Math.min(30, zoom*detector.getScaleFactor()));invalidate();return true;
            }
        });
    }
    void setMarkerListener(MarkerListener listener){ markerListener=listener; }
    void setRoads(List<Road> r){roads=r;invalidate();}
    void setPlaces(List<Place> p){places=p;invalidate();}
    void focus(double lat,double lon){centerLat=lat;centerLon=lon;zoom=Math.max(zoom,5);focusLat=lat;focusLon=lon;invalidate();}
    void showCity(){centerLat=21.9779;centerLon=96.0845;zoom=1.1;focusLat=Double.NaN;invalidate();}

    private double pxd(){return (Math.max(1,getWidth())/0.105)*zoom;}
    private float sx(double lon){return (float)(getWidth()/2.0+(lon-centerLon)*pxd()*0.928);}
    private float sy(double lat){return (float)(getHeight()/2.0-(lat-centerLat)*pxd());}
    private double latAt(float y){return centerLat-(y-getHeight()/2.0)/pxd();}
    private double lonAt(float x){return centerLon+(x-getWidth()/2.0)/(pxd()*0.928);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        c.drawColor(Color.rgb(234,244,251));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        for(Road road:roads){
            String h=road.highway;
            boolean major=h.equals("primary")||h.equals("secondary")||h.equals("trunk");
            paint.setColor(major?Color.rgb(240,194,122):Color.rgb(255,255,255));
            paint.setStrokeWidth(dp(major?4.8f:2.8f));
            Path path=new Path();boolean visible=false;
            for(int i=0;i<road.latitudes.length;i++){
                float x=sx(road.longitudes[i]), y=sy(road.latitudes[i]);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
                if(x>-80&&x<getWidth()+80&&y>-80&&y<getHeight()+80)visible=true;
            }
            if(visible)c.drawPath(path,paint);
        }
        if(zoom>2.0){
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(67,91,108));
            paint.setTextSize(dp(10));int n=0;
            for(Road road:roads){
                if(road.name.isEmpty()||road.latitudes.length==0)continue;
                if((n++%3)!=0)continue;
                int index=road.latitudes.length/2;
                float x=sx(road.longitudes[index]),y=sy(road.latitudes[index]);
                if(x>=10&&x<getWidth()-60&&y>=15&&y<getHeight()-15)
                    c.drawText(road.name.length()>20?road.name.substring(0,20):road.name,x,y,paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        int drawn=0;
        for(Place p:places){
            float x=sx(p.lon),y=sy(p.lat);
            if(x<0||x>getWidth()||y<0||y>getHeight())continue;
            paint.setColor(Color.WHITE); c.drawCircle(x,y,dp(7),paint);
            paint.setColor(p.saved?Color.rgb(233,153,29):Color.rgb(32,99,219)); c.drawCircle(x,y,dp(5),paint);
            if(zoom>4 && drawn<25){
                paint.setTextSize(dp(10));paint.setColor(Color.rgb(23,51,89));
                c.drawText(p.name.length()>14?p.name.substring(0,14):p.name,x+dp(9),y,paint);
                drawn++;
            }
        }
        if(!Double.isNaN(focusLat)){
            float x=sx(focusLon),y=sy(focusLat);
            paint.setColor(Color.rgb(235,64,72));c.drawCircle(x,y,dp(8),paint);
            paint.setColor(Color.WHITE);c.drawCircle(x,y,dp(3),paint);
        }
        paint.setTextSize(dp(11));paint.setColor(Color.rgb(34,66,100));
        c.drawText("© OpenStreetMap contributors · ODbL",dp(9),getHeight()-dp(9),paint);
        if(roads.isEmpty()){
            paint.setTextSize(dp(15));paint.setColor(Color.rgb(41,70,113));
            c.drawText("Mandalay map data not downloaded",dp(16),getHeight()/2f,paint);
            paint.setTextSize(dp(12));c.drawText("Tap Sync while online to enable offline maps",dp(16),getHeight()/2f+dp(22),paint);
        }
    }

    private float dp(float f){return f*getResources().getDisplayMetrics().density;}

    @Override public boolean onTouchEvent(MotionEvent e){
        scaleGesture.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=lastX=e.getX();downY=lastY=e.getY();moved=false;return true;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()==1&&!scaleGesture.isInProgress()){
                    float dx=e.getX()-lastX,dy=e.getY()-lastY;
                    if(Math.abs(e.getX()-downX)+Math.abs(e.getY()-downY)>dp(7))moved=true;
                    centerLon-=dx/(pxd()*0.928);centerLat+=dy/pxd();
                    lastX=e.getX();lastY=e.getY();invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(!moved&&!scaleGesture.isInProgress()){
                    Place nearest=null;float best=dp(24)*dp(24);
                    for(Place p:places){float dx=sx(p.lon)-e.getX(),dy=sy(p.lat)-e.getY();float d=dx*dx+dy*dy;
                        if(d<best){best=d;nearest=p;}}
                    if(nearest!=null&&markerListener!=null)markerListener.onMarker(nearest);
                }
                performClick();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
