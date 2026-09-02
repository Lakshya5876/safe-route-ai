package com.saferoute.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Metadata for one city's risk grid: provenance, geometry, and normalization constant. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskGridManifest {
    private String cityId;
    private String cityName;
    private String pipelineVersion;
    private String generatedAt;
    private double[] bbox; // [south, west, north, east]
    private double cellSizeMeters;
    private double cellSizeLatDeg;
    private double cellSizeLonDeg;
    private int latCells;
    private int lonCells;
    private int totalCells;
    private double referenceScale;
    private int inputEventCount;

    public String getCityId() { return cityId; }
    public void setCityId(String cityId) { this.cityId = cityId; }

    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }

    public String getPipelineVersion() { return pipelineVersion; }
    public void setPipelineVersion(String pipelineVersion) { this.pipelineVersion = pipelineVersion; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }

    public double[] getBbox() { return bbox; }
    public void setBbox(double[] bbox) { this.bbox = bbox; }

    public double getCellSizeMeters() { return cellSizeMeters; }
    public void setCellSizeMeters(double cellSizeMeters) { this.cellSizeMeters = cellSizeMeters; }

    public double getCellSizeLatDeg() { return cellSizeLatDeg; }
    public void setCellSizeLatDeg(double cellSizeLatDeg) { this.cellSizeLatDeg = cellSizeLatDeg; }

    public double getCellSizeLonDeg() { return cellSizeLonDeg; }
    public void setCellSizeLonDeg(double cellSizeLonDeg) { this.cellSizeLonDeg = cellSizeLonDeg; }

    public int getLatCells() { return latCells; }
    public void setLatCells(int latCells) { this.latCells = latCells; }

    public int getLonCells() { return lonCells; }
    public void setLonCells(int lonCells) { this.lonCells = lonCells; }

    public int getTotalCells() { return totalCells; }
    public void setTotalCells(int totalCells) { this.totalCells = totalCells; }

    public double getReferenceScale() { return referenceScale; }
    public void setReferenceScale(double referenceScale) { this.referenceScale = referenceScale; }

    public int getInputEventCount() { return inputEventCount; }
    public void setInputEventCount(int inputEventCount) { this.inputEventCount = inputEventCount; }
}
