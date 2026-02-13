package com.saferoute.backend.model;

import java.util.List;

public class RouteOptionDTO {

    private String id;
    private List<List<Double>> coordinates;
    private List<RouteSegmentDTO> segments;
    private double duration;
    private double riskScore;
    private String riskLevel;
    private String description;
    private boolean primary;

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
}
