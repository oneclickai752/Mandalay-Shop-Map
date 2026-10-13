package com.mandalay.smartmap;

import java.util.List;

final class Place {
    String id, name, type, address, phone, website, source;
    double lat, lon;
    boolean saved;

    Place(String id, String name, String type, String address, String phone, String website,
          String source, double lat, double lon, boolean saved) {
        this.id = id; this.name = name; this.type = type; this.address = address;
        this.phone = phone; this.website = website; this.source = source;
        this.lat = lat; this.lon = lon; this.saved = saved;
    }
}

final class Road {
    final String id, name, highway;
    final double[] latitudes, longitudes;
    Road(String id, String name, String highway, double[] latitudes, double[] longitudes) {
        this.id = id; this.name = name; this.highway = highway;
        this.latitudes = latitudes; this.longitudes = longitudes;
    }
}

final class Coordinate {
    final double lat, lon;
    Coordinate(double lat, double lon) { this.lat = lat; this.lon = lon; }
}
