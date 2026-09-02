package com.saferoute.backend.service;

import com.saferoute.backend.model.RouteOptionDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteDeduplicationServiceTest {

    private final RouteDeduplicationService service = new RouteDeduplicationService();

    private static List<List<Double>> route(double... lonLatPairs) {
        List<List<Double>> coords = new ArrayList<>();
        for (int i = 0; i < lonLatPairs.length; i += 2) {
            coords.add(List.of(lonLatPairs[i], lonLatPairs[i + 1]));
        }
        return coords;
    }

    private static RouteOptionDTO option(String id, List<List<Double>> coords, double riskScore) {
        RouteOptionDTO dto = new RouteOptionDTO();
        dto.setId(id);
        dto.setCoordinates(coords);
        dto.setRiskScore(riskScore);
        return dto;
    }

    @Test
    void identicalRoutesAreDeduplicatedKeepingTheLowerRiskOne() {
        List<List<Double>> path = route(77.20, 28.60, 77.21, 28.61, 77.22, 28.62);
        RouteOptionDTO a = option("a", path, 40);
        RouteOptionDTO b = option("b", new ArrayList<>(path), 20); // identical geometry, lower risk

        List<RouteOptionDTO> result = service.filterDuplicateRoutes(List.of(a, b));
        assertEquals(1, result.size());
        assertEquals("b", result.get(0).getId());
    }

    @Test
    void clearlyDistinctRoutesAreNotDeduplicated() {
        RouteOptionDTO a = option("a", route(77.20, 28.60, 77.30, 28.70), 40);
        RouteOptionDTO b = option("b", route(77.20, 28.60, 76.90, 28.40), 20); // goes the opposite direction

        List<RouteOptionDTO> result = service.filterDuplicateRoutes(List.of(a, b));
        assertEquals(2, result.size());
    }

    @Test
    void aShortLocalizedDetourIsNotDiscardedAsADuplicate() {
        // Regression test for the audited bug: the OLD average-only similarity
        // measure could rate two routes as duplicates even when they differ by
        // ~150-200m over a short stretch (e.g. detouring around one dangerous
        // block) because that localized difference barely moves a 50-sample
        // average. Requiring peak deviation to also be small fixes this.
        List<List<Double>> straight = new ArrayList<>();
        List<List<Double>> detour = new ArrayList<>();
        int n = 40;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double lon = 77.20 + t * 0.02; // ~2.2km east-west path
            double lat = 28.60;
            straight.add(List.of(lon, lat));
            // Detour bulges north by ~180m for a narrow band in the middle 10% of the path.
            double bulge = (t > 0.45 && t < 0.55) ? 0.0016 : 0.0; // ~180m at this latitude
            detour.add(List.of(lon, lat + bulge));
        }

        RouteOptionDTO a = option("a", straight, 50);
        RouteOptionDTO b = option("b", detour, 15); // meaningfully safer because it avoids something

        RouteDeduplicationService.Deviation dev = service.computeDeviation(straight, detour);
        assertTrue(dev.avgM < 50.0, "average deviation should stay small for a short localized detour (sanity check on the scenario)");
        assertTrue(dev.peakM > 120.0, "peak deviation should be large enough to be detected (sanity check on the scenario)");

        List<RouteOptionDTO> result = service.filterDuplicateRoutes(List.of(a, b));
        assertEquals(2, result.size(), "a route differing by a real localized detour must NOT be discarded as a duplicate");
    }

    @Test
    void arcLengthSamplingIsRobustToUnevenVertexDensity() {
        // Same physical straight line, but one copy has many redundant collinear
        // vertices concentrated at the start. Vertex-index sampling would treat
        // "the same index fraction" as different physical points; arc-length
        // sampling should not.
        List<List<Double>> sparse = route(77.20, 28.60, 77.22, 28.60);
        List<List<Double>> denseAtStart = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0 * 0.1; // 21 points crammed into the first 10% of the path
            denseAtStart.add(List.of(77.20 + t * 0.02, 28.60));
        }
        denseAtStart.add(List.of(77.22, 28.60)); // then straight to the end

        RouteDeduplicationService.Deviation dev = service.computeDeviation(sparse, denseAtStart);
        assertTrue(dev.avgM < 5.0, "uneven vertex density along an identical physical path should not register as a real difference");
        assertTrue(dev.peakM < 5.0);
    }

    @Test
    void emptyOrSingletonInputsDoNotThrow() {
        assertEquals(0, service.filterDuplicateRoutes(null).size());
        assertEquals(0, service.filterDuplicateRoutes(List.of()).size());
        RouteOptionDTO single = option("a", route(77.20, 28.60, 77.21, 28.61), 30);
        assertEquals(1, service.filterDuplicateRoutes(List.of(single)).size());
    }
}
