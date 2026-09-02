package com.saferoute.backend.service;

import com.saferoute.backend.model.RiskGrid;
import com.saferoute.backend.model.RiskGridCell;
import com.saferoute.backend.model.RiskGridManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit + property + synthetic-scenario tests for the coherent risk model.
 * Grids are built directly (via loadGridForTesting) rather than loaded from
 * the real ~10k-cell classpath resources, so each test isolates one
 * mathematical property with an exact, hand-checkable synthetic city.
 *
 * All coordinates in test routes are given as [lon, lat] pairs, matching the
 * GeoJSON/ORS convention used throughout the production code.
 */
class RiskModelServiceTest {

    private static final String CITY = "testcity";
    private static final double[] BBOX = { 28.60, 77.20, 28.61, 77.21 }; // ~1.1km x 1.1km near Delhi's latitude
    private static final double CELL_METERS = 150.0;
    private static final double REFERENCE_SCALE = 1.0;

    private RiskModelService service;
    private double cellLatDeg;
    private double cellLonDeg;
    private int latCells;
    private int lonCells;

    @BeforeEach
    void setUp() {
        service = new RiskModelService();
        double latRad = Math.toRadians((BBOX[0] + BBOX[2]) / 2);
        cellLatDeg = CELL_METERS / 111320.0;
        cellLonDeg = CELL_METERS / (111320.0 * Math.cos(latRad));
        latCells = (int) Math.ceil((BBOX[2] - BBOX[0]) / cellLatDeg);
        lonCells = (int) Math.ceil((BBOX[3] - BBOX[1]) / cellLonDeg);
    }

    // ---- test grid construction helpers -------------------------------------------------

    private int[] indexOf(double lat, double lon) {
        int latIdx = (int) Math.floor((lat - BBOX[0]) / cellLatDeg);
        int lonIdx = (int) Math.floor((lon - BBOX[1]) / cellLonDeg);
        return new int[] { latIdx, lonIdx };
    }

    private double[] centroidOf(int latIdx, int lonIdx) {
        return new double[] {
                BBOX[0] + (latIdx + 0.5) * cellLatDeg,
                BBOX[1] + (lonIdx + 0.5) * cellLonDeg,
        };
    }

    /** An empty grid: every cell present, all values zero, confidence zero (matches production's pre-fill). */
    private RiskGrid emptyGrid(double referenceScale) {
        RiskGridManifest manifest = new RiskGridManifest();
        manifest.setCityId(CITY);
        manifest.setBbox(BBOX);
        manifest.setCellSizeLatDeg(cellLatDeg);
        manifest.setCellSizeLonDeg(cellLonDeg);
        manifest.setLatCells(latCells);
        manifest.setLonCells(lonCells);
        manifest.setReferenceScale(referenceScale);

        List<RiskGridCell> cells = new ArrayList<>();
        for (int i = 0; i < latCells; i++) {
            for (int j = 0; j < lonCells; j++) {
                RiskGridCell c = new RiskGridCell();
                c.setLatIdx(i);
                c.setLonIdx(j);
                double[] centroid = centroidOf(i, j);
                c.setLat(centroid[0]);
                c.setLon(centroid[1]);
                cells.add(c);
            }
        }
        RiskGrid grid = new RiskGrid();
        grid.setManifest(manifest);
        grid.setCells(cells);
        return grid;
    }

    private RiskGridCell cellAt(RiskGrid grid, double lat, double lon) {
        int[] idx = indexOf(lat, lon);
        return grid.getCells().stream()
                .filter(c -> c.getLatIdx() == idx[0] && c.getLonIdx() == idx[1])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no cell at given lat/lon in test grid"));
    }

    private void setCell(RiskGrid grid, double lat, double lon, double poorLighting, double isolation,
                          double protective, double crimePrior, int sampleCount, double confidence) {
        RiskGridCell c = cellAt(grid, lat, lon);
        c.setPoorLighting(poorLighting);
        c.setIsolation(isolation);
        c.setProtective(protective);
        c.setCrimePrior(crimePrior);
        c.setSampleCount(sampleCount);
        c.setConfidence(confidence);
    }

    private static List<List<Double>> route(double... lonLatPairs) {
        List<List<Double>> coords = new ArrayList<>();
        for (int i = 0; i < lonLatPairs.length; i += 2) {
            coords.add(List.of(lonLatPairs[i], lonLatPairs[i + 1]));
        }
        return coords;
    }

    // ---- basic contract ------------------------------------------------------------------

    @Test
    void unsupportedCityReturnsUnknownNotFabricatedSafe() {
        var result = service.evaluateRoute("nonexistent-city", route(77.205, 28.605, 77.206, 28.606), "14:00", "DRIVING");
        assertEquals("UNKNOWN", result.summary.getRiskLevel());
        assertEquals("insufficient", result.summary.getCoverageTier());
        assertEquals(0.0, result.summary.getConfidence());
    }

    @Test
    void tooFewCoordinatesIsHandledGracefully() {
        var result = service.evaluateRoute(CITY, route(77.20, 28.60), "14:00", "DRIVING");
        assertEquals("UNKNOWN", result.summary.getRiskLevel());
        assertTrue(result.segments.isEmpty());
    }

    // ---- spatial correctness: distance sensitivity ----------------------------------------

    @Test
    void routeThroughAKnownRiskCellScoresHigherThanRouteThroughAnEmptyCell() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] riskCentroid = centroidOf(2, 2);
        double[] safeCentroid = centroidOf(5, 5);
        setCell(grid, riskCentroid[0], riskCentroid[1], 0.9, 0, 0, 0, 5, 0.8);
        service.loadGridForTesting(CITY, grid);

        var riskyRoute = service.evaluateRoute(CITY,
                route(riskCentroid[1], riskCentroid[0], riskCentroid[1] + 0.0002, riskCentroid[0] + 0.0002),
                "14:00", "DRIVING");
        var safeRoute = service.evaluateRoute(CITY,
                route(safeCentroid[1], safeCentroid[0], safeCentroid[1] + 0.0002, safeCentroid[0] + 0.0002),
                "14:00", "DRIVING");

        assertTrue(riskyRoute.summary.getRiskScore() > safeRoute.summary.getRiskScore(),
                "a route through a known risk cell must score higher than one through an empty cell");
    }

    // ---- severity sensitivity --------------------------------------------------------------

    @Test
    void higherCellSeverityNeverProducesLowerRisk() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] c = centroidOf(2, 2);
        setCell(grid, c[0], c[1], 0.3, 0, 0, 0, 5, 0.8);
        service.loadGridForTesting(CITY, grid);
        var lowSeverity = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");

        RiskGrid grid2 = emptyGrid(REFERENCE_SCALE);
        setCell(grid2, c[0], c[1], 0.6, 0, 0, 0, 5, 0.8); // doubled severity
        service.loadGridForTesting(CITY, grid2);
        var highSeverity = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");

        assertTrue(highSeverity.summary.getRiskScore() > lowSeverity.summary.getRiskScore(),
                "doubling a cell's severity must not reduce or leave unchanged the route's risk score");
    }

    // ---- route-length / segment-density bias -----------------------------------------------

    @Test
    void addingRedundantCollinearVerticesDoesNotChangeTheScore() {
        // Regression test for the audited bug: the old model sampled by vertex
        // index, so inserting extra points along an identical physical path
        // changed the score. The new model resamples by arc length, so it must not.
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] c = centroidOf(3, 3);
        setCell(grid, c[0], c[1], 0.5, 0, 0, 0, 4, 0.7);
        service.loadGridForTesting(CITY, grid);

        double lonA = c[1] - 0.002, latA = c[0];
        double lonB = c[1] + 0.002, latB = c[0];

        var sparse = service.evaluateRoute(CITY, route(lonA, latA, lonB, latB), "14:00", "DRIVING");

        // Same straight line, but with 9 redundant collinear midpoints inserted.
        List<Double> denseFlat = new ArrayList<>();
        List<List<Double>> denseCoords = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            denseCoords.add(List.of(lonA + t * (lonB - lonA), latA + t * (latB - latA)));
        }
        var dense = service.evaluateRoute(CITY, denseCoords, "14:00", "DRIVING");

        assertEquals(sparse.summary.getRiskScore(), dense.summary.getRiskScore(), 0.5,
                "resampling by arc length should make vertex density irrelevant to the score");
    }

    @Test
    void appendingALongSafeStretchDoesNotDiluteThePeakRiskButDoesLowerTheAverage() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] hot = centroidOf(1, 1);
        setCell(grid, hot[0], hot[1], 0.9, 0, 0, 0, 5, 0.8);
        service.loadGridForTesting(CITY, grid);

        var shortDangerous = service.evaluateRoute(CITY,
                route(hot[1] - 0.0003, hot[0], hot[1] + 0.0003, hot[0]), "14:00", "DRIVING");

        // Same dangerous stretch, plus a long empty-cell detour appended after it.
        double[] farSafe = centroidOf(latCells - 1, lonCells - 1);
        var withLongSafeDetour = service.evaluateRoute(CITY,
                route(hot[1] - 0.0003, hot[0], hot[1] + 0.0003, hot[0], farSafe[1], farSafe[0]),
                "14:00", "DRIVING");

        assertEquals(shortDangerous.summary.getPeakRiskScore(), withLongSafeDetour.summary.getPeakRiskScore(), 0.5,
                "appending a long safe stretch must not dilute the reported PEAK risk");
        assertTrue(withLongSafeDetour.summary.getRiskScore() < shortDangerous.summary.getRiskScore(),
                "the distance-weighted AVERAGE score should legitimately drop once mostly-safe distance is added");
    }

    // ---- uncertainty / coverage -------------------------------------------------------------

    @Test
    void zeroSampleCellsYieldLowConfidenceNotFabricatedSafety() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE); // every cell has sampleCount=0, confidence=0 by construction
        service.loadGridForTesting(CITY, grid);
        double[] a = centroidOf(1, 1), b = centroidOf(1, 2);
        var result = service.evaluateRoute(CITY, route(a[1], a[0], b[1], b[0]), "14:00", "DRIVING");

        assertTrue(result.summary.getConfidence() < 0.35,
                "a route through entirely zero-sample cells must have low confidence");
        assertNotEquals("high", result.summary.getCoverageTier());
    }

    @Test
    void extremeOutlierCellClampsAtMaximumInsteadOfOverflowing() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] c = centroidOf(2, 2);
        setCell(grid, c[0], c[1], 1000.0, 0, 0, 0, 50, 1.0); // absurdly high relative to referenceScale=1.0
        service.loadGridForTesting(CITY, grid);
        var result = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");

        assertTrue(result.summary.getRiskScore() <= 100.0 + 1e-9, "risk score must clamp at 100");
        assertFalse(Double.isNaN(result.summary.getRiskScore()));
        assertFalse(Double.isInfinite(result.summary.getRiskScore()));
    }

    // ---- protective factors ------------------------------------------------------------------

    @Test
    void protectivePresenceReducesRiskAndNeverProducesNegativeScore() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] c = centroidOf(2, 2);
        setCell(grid, c[0], c[1], 0.5, 0, 10.0, 0, 5, 0.8); // protective term dwarfs the risk term
        service.loadGridForTesting(CITY, grid);
        var result = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");

        assertTrue(result.summary.getRiskScore() >= 0.0, "risk score must never go negative");
        assertEquals(0.0, result.summary.getRiskScore(), 0.01, "overwhelming protective presence should floor risk at 0, not negative");
    }

    // ---- temporal modelling -------------------------------------------------------------------

    @Test
    void nightTimeScoresHigherThanDayForALightingDominatedRoute() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] c = centroidOf(2, 2);
        setCell(grid, c[0], c[1], 0.7, 0, 0, 0, 5, 0.8);
        service.loadGridForTesting(CITY, grid);

        var day = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");
        var night = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "23:30", "DRIVING");

        assertTrue(night.summary.getRiskScore() > day.summary.getRiskScore(),
                "night travel should score higher risk for a lighting-dominated cell");
        assertTrue(night.summary.isNightTravel());
        assertFalse(day.summary.isNightTravel());
    }

    // ---- mode sensitivity ---------------------------------------------------------------------

    @Test
    void walkingScoresAtLeastAsHighAsBikeWhichScoresAtLeastAsHighAsDriving() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] c = centroidOf(2, 2);
        setCell(grid, c[0], c[1], 0.5, 0, 0, 0, 5, 0.8);
        service.loadGridForTesting(CITY, grid);

        var walking = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "WALKING");
        var bike = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "BIKE");
        var driving = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");

        assertTrue(walking.summary.getRiskScore() >= bike.summary.getRiskScore());
        assertTrue(bike.summary.getRiskScore() >= driving.summary.getRiskScore());
    }

    // ---- normalization independence (the core audited bug) -----------------------------------

    @Test
    void anotherCitysGridNeverAffectsThisCitysScore() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] c = centroidOf(2, 2);
        setCell(grid, c[0], c[1], 0.5, 0.2, 0, 0.1, 5, 0.8);
        service.loadGridForTesting(CITY, grid);
        var before = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");

        // Load a second, unrelated city with a totally different reference scale.
        RiskGrid otherGrid = emptyGrid(999.0);
        service.loadGridForTesting("othercity", otherGrid);
        var after = service.evaluateRoute(CITY, route(c[1], c[0], c[1] + 0.0001, c[0]), "14:00", "DRIVING");

        assertEquals(before.summary.getRiskScore(), after.summary.getRiskScore(), 1e-9,
                "loading another city's grid must never change this city's scores (per-city normalization)");
    }

    // ---- consistency between segment coloring and route aggregate ----------------------------

    @Test
    void segmentScoresAggregateConsistentlyWithTheRouteLevelAverage() {
        RiskGrid grid = emptyGrid(REFERENCE_SCALE);
        double[] a = centroidOf(1, 1), b = centroidOf(1, 4);
        setCell(grid, a[0], a[1], 0.8, 0, 0, 0, 5, 0.8);
        service.loadGridForTesting(CITY, grid);

        var result = service.evaluateRoute(CITY, route(a[1], a[0], b[1], b[0]), "14:00", "DRIVING");
        assertFalse(result.segments.isEmpty());
        // Every segment's score must come from the same [0,100] scale as the route score —
        // this is a basic sanity/consistency check that both paths share one formula.
        for (var seg : result.segments) {
            assertTrue(seg.getRiskScore() >= 0 && seg.getRiskScore() <= 100);
        }
        double maxSegmentScore = result.segments.stream().mapToDouble(s -> s.getRiskScore()).max().orElse(0);
        assertTrue(maxSegmentScore >= result.summary.getRiskScore() - 1e-6,
                "no segment average should be lower than the overall route average by construction of a distance-weighted mean with a peak segment present");
    }
}
