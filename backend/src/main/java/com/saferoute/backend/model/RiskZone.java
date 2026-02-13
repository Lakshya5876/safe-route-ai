package com.saferoute.backend.model;

public class RiskZone {

    private String zoneName;
    private double baseRisk;

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public double getBaseRisk() {
        return baseRisk;
    }

    public void setBaseRisk(double baseRisk) {
        this.baseRisk = baseRisk;
    }
}
