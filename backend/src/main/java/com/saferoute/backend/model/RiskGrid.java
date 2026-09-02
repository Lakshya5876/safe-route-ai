package com.saferoute.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Deserialization target for backend/src/main/resources/risk-grids/&lt;city&gt;.json. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskGrid {
    private RiskGridManifest manifest;
    private List<RiskGridCell> cells;

    public RiskGridManifest getManifest() { return manifest; }
    public void setManifest(RiskGridManifest manifest) { this.manifest = manifest; }

    public List<RiskGridCell> getCells() { return cells; }
    public void setCells(List<RiskGridCell> cells) { this.cells = cells; }
}
