package com.saferoute.backend.model;

public class RiskZone {

    private String name;
    private double latitude;
    private double longitude;
    private double radius;
    private double baseRisk;

    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getRadius() { return radius; }
    public double getBaseRisk() { return baseRisk; }

    public void setName(String name) { this.name = name; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setRadius(double radius) { this.radius = radius; }
    public void setBaseRisk(double baseRisk) { this.baseRisk = baseRisk; }
}
