package com.saferoute.backend.model;

public class RouteRequest {

    private Location origin;
    private Location destination;
    private String time;
    private String mode;
    /** One of the six supported city ids (delhi, mumbai, bengaluru, hyderabad, pune, chennai). */
    private String city;

    public static class Location {
        private double lat;
        private double lng;

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }

        public void setLat(double lat) {
            this.lat = lat;
        }

        public void setLng(double lng) {
            this.lng = lng;
        }
    }

    public Location getOrigin() {
        return origin;
    }

    public Location getDestination() {
        return destination;
    }

    public String getTime() {
        return time;
    }

    public String getMode() {
        return mode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setOrigin(Location origin) {
        this.origin = origin;
    }

    public void setDestination(Location destination) {
        this.destination = destination;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
