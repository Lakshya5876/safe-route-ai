package com.saferoute.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class RouteOptionDTO {

    private String id;
    private List<List<Double>> coordinates;
    private List<RouteSegmentDTO> segments;
    private double duration;
    private double distanceKm;
    private double riskScore;
    private String riskLevel;
    private double confidence;
    private String coverageTier;
    private double totalExposureIndex;
    private String description;
    private boolean primary;
    /** True if no other candidate is both safer and no slower (see RouteSelectionService). */
    private boolean paretoOptimal;
    /** How this candidate was produced: "ORS_DIRECT" or "ORS_RISK_AVOIDANCE". */
    private String generationMethod;

    /** Used server-side for description generation; not sent to client. */
    @JsonIgnore
    private RouteRiskSummary riskSummary;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<List<Double>> getCoordinates() { return coordinates; }
    public void setCoordinates(List<List<Double>> coordinates) { this.coordinates = coordinates; }

    public List<RouteSegmentDTO> getSegments() { return segments; }
    public void setSegments(List<RouteSegmentDTO> segments) { this.segments = segments; }

    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }

    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    public RouteRiskSummary getRiskSummary() { return riskSummary; }
    public void setRiskSummary(RouteRiskSummary riskSummary) { this.riskSummary = riskSummary; }

    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getCoverageTier() { return coverageTier; }
    public void setCoverageTier(String coverageTier) { this.coverageTier = coverageTier; }

    public double getTotalExposureIndex() { return totalExposureIndex; }
    public void setTotalExposureIndex(double totalExposureIndex) { this.totalExposureIndex = totalExposureIndex; }

    public boolean isParetoOptimal() { return paretoOptimal; }
    public void setParetoOptimal(boolean paretoOptimal) { this.paretoOptimal = paretoOptimal; }

    public String getGenerationMethod() { return generationMethod; }
    public void setGenerationMethod(String generationMethod) { this.generationMethod = generationMethod; }
}
