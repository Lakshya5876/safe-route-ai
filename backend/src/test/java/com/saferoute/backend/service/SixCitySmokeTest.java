package com.saferoute.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit acceptance-criteria coverage: every one of the six supported
 * cities' REAL, pipeline-built risk grid loads successfully, and every
 * supported travel mode produces a valid, in-bounds risk evaluation against
 * real data (as opposed to the synthetic grids used elsewhere for isolating
 * specific mathematical properties).
 */
class SixCitySmokeTest {

    private static final String[] CITIES = { "delhi", "mumbai", "bengaluru", "hyderabad", "pune", "chennai" };

    @Test
    void allSixCitiesLoadSuccessfullyAtStartup() {
        RiskModelService service = new RiskModelService();
        service.loadGrids();
        for (String city : CITIES) {
            assertTrue(service.isCitySupported(city), city + " should load successfully from its real risk grid");
        }
        assertEquals(6, service.supportedCities().size());
    }

    @ParameterizedTest
    @CsvSource({
            "delhi, DRIVING", "delhi, WALKING", "delhi, BIKE",
            "mumbai, DRIVING", "mumbai, WALKING", "mumbai, BIKE",
            "bengaluru, DRIVING", "bengaluru, WALKING", "bengaluru, BIKE",
            "hyderabad, DRIVING", "hyderabad, WALKING", "hyderabad, BIKE",
            "pune, DRIVING", "pune, WALKING", "pune, BIKE",
            "chennai, DRIVING", "chennai, WALKING", "chennai, BIKE",
    })
    void everyCityAndModeProducesAValidInBoundsEvaluation(String city, String mode) {
        RiskModelService service = new RiskModelService();
        service.loadGrids();

        // A short route around each city's own configured center (see cities.js),
        // guaranteed to fall inside that city's own bounding box.
        double[] center = CENTERS.get(city);
        List<List<Double>> route = List.of(
                List.of(center[1] - 0.005, center[0] - 0.005),
                List.of(center[1] + 0.005, center[0] + 0.005)
        );

        var result = service.evaluateRoute(city, route, "14:00", mode);
        assertNotNull(result.summary);
        assertTrue(result.summary.getRiskScore() >= 0 && result.summary.getRiskScore() <= 100,
                city + "/" + mode + ": risk score out of [0,100]");
        assertNotNull(result.summary.getCoverageTier());
        assertTrue(result.summary.getConfidence() >= 0 && result.summary.getConfidence() <= 1);
        assertFalse(result.segments.isEmpty(), city + "/" + mode + ": expected at least one scored segment");
    }

    // Must match pipeline/src/cities.js centers.
    private static final java.util.Map<String, double[]> CENTERS = java.util.Map.of(
            "delhi", new double[]{28.6139, 77.209},
            "mumbai", new double[]{19.076, 72.8777},
            "bengaluru", new double[]{12.9716, 77.5946},
            "hyderabad", new double[]{17.385, 78.4867},
            "pune", new double[]{18.5204, 73.8567},
            "chennai", new double[]{13.0827, 80.2707}
    );
}
