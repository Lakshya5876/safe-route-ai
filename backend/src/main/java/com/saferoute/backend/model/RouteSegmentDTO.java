package com.saferoute.backend.model;

import java.util.List;

public class RouteSegmentDTO {

    private List<List<Double>> coordinates;
    private String riskLevel;

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
}
