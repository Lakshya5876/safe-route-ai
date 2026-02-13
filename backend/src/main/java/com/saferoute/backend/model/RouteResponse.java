package com.saferoute.backend.model;

import java.util.List;

public class RouteResponse {

    private List<List<Double>> route;
    private double safetyScore;
    private String riskLevel;
    private List<RiskBreakdown> alerts;

    public RouteResponse(
            List<List<Double>> route,
            double safetyScore,
            String riskLevel,
            List<RiskBreakdown> alerts
    ) {
        this.route = route;
        this.safetyScore = safetyScore;
        this.riskLevel = riskLevel;
        this.alerts = alerts;
    }

    public List<List<Double>> getRoute() {
        return route;
    }

    public double getSafetyScore() {
        return safetyScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public List<RiskBreakdown> getAlerts() {
        return alerts;
    }
}
