package com.saferoute.backend.model;

import java.util.List;

public class RouteSegmentDTO {

    private List<List<Double>> coordinates;
    private String riskLevel;
    private double riskScore;

    public RouteSegmentDTO(List<List<Double>> coordinates, String riskLevel) {
        this.coordinates = coordinates;
        this.riskLevel = riskLevel;
    }

    public List<List<Double>> getCoordinates() {
        return coordinates;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }
}
