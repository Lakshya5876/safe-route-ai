package com.saferoute.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One cell of a city's precomputed risk grid (see pipeline/src/build-risk-grid.js
 * for how these are produced). Categories are kept separate rather than
 * pre-summed so the backend can recombine them with a time-of-day multiplier
 * at request time without needing to rebuild the grid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskGridCell {
    private int latIdx;
    private int lonIdx;
    private double lat;
    private double lon;
    private double poorLighting;
    private double isolation;
    private double protective;
    private double crimePrior;
    private int sampleCount;
    private double confidence;

    public int getLatIdx() { return latIdx; }
    public void setLatIdx(int latIdx) { this.latIdx = latIdx; }

    public int getLonIdx() { return lonIdx; }
    public void setLonIdx(int lonIdx) { this.lonIdx = lonIdx; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public double getPoorLighting() { return poorLighting; }
    public void setPoorLighting(double poorLighting) { this.poorLighting = poorLighting; }

    public double getIsolation() { return isolation; }
    public void setIsolation(double isolation) { this.isolation = isolation; }

    public double getProtective() { return protective; }
    public void setProtective(double protective) { this.protective = protective; }

    public double getCrimePrior() { return crimePrior; }
    public void setCrimePrior(double crimePrior) { this.crimePrior = crimePrior; }

    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
