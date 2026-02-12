package com.saferoute.backend.model;

import java.util.List;

public class RouteResponse {

    private String polyline;
    private double totalRiskScore;
    private String riskLevel;
    private List<RiskBreakdown> breakdown;

    public RouteResponse(String polyline, double totalRiskScore, String riskLevel, List<RiskBreakdown> breakdown) {
        this.polyline = polyline;
        this.totalRiskScore = totalRiskScore;
        this.riskLevel = riskLevel;
        this.breakdown = breakdown;
    }

    public String getPolyline() {
        return polyline;
    }

    public double getTotalRiskScore() {
        return totalRiskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public List<RiskBreakdown> getBreakdown() {
        return breakdown;
    }
}
