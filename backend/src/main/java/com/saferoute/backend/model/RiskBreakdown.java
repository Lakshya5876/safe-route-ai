package com.saferoute.backend.model;

public class RiskBreakdown {

    private String factor;
    private double impact;

    public RiskBreakdown(String factor, double impact) {
        this.factor = factor;
        this.impact = impact;
    }

    public String getFactor() {
        return factor;
    }

    public double getImpact() {
        return impact;
    }
}
