package com.mandalay.smartmap;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Find local intersections by testing segments of similarly numbered OSM street names. */
final class StreetFinder {
    private static final Pattern NUMBERS=Pattern.compile("(?<![0-9])([0-9]{1,3})(?![0-9])");

    static Coordinate find(List<Road> roads, String search) {
        Matcher m=NUMBERS.matcher(search);
        if(!m.find())return null;
        String a=m.group(1);
        if(!m.find())return null;
        String b=m.group(1);
        if(a.equals(b))return null;
        List<Road> first=new ArrayList<>(), second=new ArrayList<>();
        for(Road r:roads){
            if(r.name==null || r.name.isEmpty())continue;
            Matcher rMatcher=NUMBERS.matcher(r.name);
            while(rMatcher.find()){
                if(rMatcher.group(1).equals(a)){first.add(r);break;}
            }
            rMatcher=NUMBERS.matcher(r.name);
            while(rMatcher.find()){
                if(rMatcher.group(1).equals(b)){second.add(r);break;}
            }
        }
        for(Road ra:first)for(Road rb:second){
            Coordinate p=intersection(ra,rb);
            if(p!=null)return p;
        }
        return null;
    }

    // x=longitude, y=latitude. An approximation on short city street segments is sufficient here.
    private static Coordinate intersection(Road a,Road b){
        for(int i=1;i<a.latitudes.length;i++) for(int j=1;j<b.latitudes.length;j++){
            double ax=a.longitudes[i-1],ay=a.latitudes[i-1];
            double bx=a.longitudes[i],by=a.latitudes[i];
            double cx=b.longitudes[j-1],cy=b.latitudes[j-1];
            double dx=b.longitudes[j],dy=b.latitudes[j];
            double det=(bx-ax)*(dy-cy)-(by-ay)*(dx-cx);
            if(Math.abs(det)<1e-13)continue;
            double u=((cx-ax)*(dy-cy)-(cy-ay)*(dx-cx))/det;
            double v=((cx-ax)*(by-ay)-(cy-ay)*(bx-ax))/det;
            if(u>=0 && u<=1 && v>=0 && v<=1)return new Coordinate(ay+u*(by-ay), ax+u*(bx-ax));
        }
        return null;
    }
}
