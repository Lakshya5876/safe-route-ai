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
    /** Distance-weighted average confidence in [0,1], derived from local data density along the route. */
    private double confidence;
    /** Fraction of the route's length (0-1) that falls inside a supported city's covered area. */
    private double coverageFraction;
    /** "high" | "moderate" | "sparse" | "insufficient" — see RiskModelService for thresholds. */
    private String coverageTier;
    /** risk-score * km, an absolute accumulated-exposure figure (informational; NOT used for ranking). */
    private double totalExposureIndex;
    /** Highest single-point risk score (0-100) found along the route, and where — used to target
     *  an avoidance candidate at the worst spot rather than the route as a whole. */
    private double peakRiskScore;
    private double peakLat;
    private double peakLon;
    private boolean hasPeak;

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

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public double getCoverageFraction() { return coverageFraction; }
    public void setCoverageFraction(double coverageFraction) { this.coverageFraction = coverageFraction; }

    public String getCoverageTier() { return coverageTier; }
    public void setCoverageTier(String coverageTier) { this.coverageTier = coverageTier; }

    public double getTotalExposureIndex() { return totalExposureIndex; }
    public void setTotalExposureIndex(double totalExposureIndex) { this.totalExposureIndex = totalExposureIndex; }

    public double getPeakRiskScore() { return peakRiskScore; }
    public void setPeakRiskScore(double peakRiskScore) { this.peakRiskScore = peakRiskScore; }

    public double getPeakLat() { return peakLat; }
    public void setPeakLat(double peakLat) { this.peakLat = peakLat; }

    public double getPeakLon() { return peakLon; }
    public void setPeakLon(double peakLon) { this.peakLon = peakLon; }

    public boolean isHasPeak() { return hasPeak; }
    public void setHasPeak(boolean hasPeak) { this.hasPeak = hasPeak; }
}
