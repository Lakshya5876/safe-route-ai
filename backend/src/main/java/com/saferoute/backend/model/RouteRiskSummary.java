package com.saferoute.backend.model;

import java.util.List;

/** Result of route risk calculation: score, level, and metadata for description. */
public class RouteRiskSummary {
    private double riskScore;
    private String riskLevel;
    private List<String> zoneNames;
    private List<String> zoneCategories;
    private double totalLengthKm;
    private boolean nightTravel;
    private boolean eveningTravel;

    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public List<String> getZoneNames() { return zoneNames; }
    public void setZoneNames(List<String> zoneNames) { this.zoneNames = zoneNames; }

    public List<String> getZoneCategories() { return zoneCategories; }
    public void setZoneCategories(List<String> zoneCategories) { this.zoneCategories = zoneCategories; }

    public double getTotalLengthKm() { return totalLengthKm; }
    public void setTotalLengthKm(double totalLengthKm) { this.totalLengthKm = totalLengthKm; }

    public boolean isNightTravel() { return nightTravel; }
    public void setNightTravel(boolean nightTravel) { this.nightTravel = nightTravel; }

    public boolean isEveningTravel() { return eveningTravel; }
    public void setEveningTravel(boolean eveningTravel) { this.eveningTravel = eveningTravel; }
}
