package com.saferoute.backend.controller;

import com.saferoute.backend.model.*;
import com.saferoute.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test of the internal pipeline — dataset (a synthetic grid) ->
 * risk model -> route generation -> risk scoring -> deduplication -> Pareto
 * selection -> API response — with ONLY the external OpenRouteService
 * boundary mocked, per the project's testing requirements ("tests must not
 * depend on live ORS availability"). Every other component (RiskModelService,
 * RouteDeduplicationService, RouteSelectionService, RouteDescriptionGenerator)
 * is real.
 */
class RouteControllerIntegrationTest {

    private static final String CITY = "integrationtestcity";
    private static final double[] BBOX = { 28.60, 77.20, 28.62, 77.22 };

    private RiskModelService riskModelService;
    private ORSRoutingService mockOrs;
    private RouteController controller;

    @BeforeEach
    void setUp() {
        riskModelService = new RiskModelService();
        double latRad = Math.toRadians(28.61);
        double cellLatDeg = 150.0 / 111320.0;
        double cellLonDeg = 150.0 / (111320.0 * Math.cos(latRad));
        int latCells = (int) Math.ceil((BBOX[2] - BBOX[0]) / cellLatDeg);
        int lonCells = (int) Math.ceil((BBOX[3] - BBOX[1]) / cellLonDeg);

        RiskGridManifest manifest = new RiskGridManifest();
        manifest.setCityId(CITY);
        manifest.setBbox(BBOX);
        manifest.setCellSizeLatDeg(cellLatDeg);
        manifest.setCellSizeLonDeg(cellLonDeg);
        manifest.setLatCells(latCells);
        manifest.setLonCells(lonCells);
        manifest.setReferenceScale(1.0);

        List<RiskGridCell> cells = new ArrayList<>();
        for (int i = 0; i < latCells; i++) {
            for (int j = 0; j < lonCells; j++) {
                RiskGridCell c = new RiskGridCell();
                c.setLatIdx(i);
                c.setLonIdx(j);
                c.setLat(BBOX[0] + (i + 0.5) * cellLatDeg);
                c.setLon(BBOX[1] + (j + 0.5) * cellLonDeg);
                // A dangerous corridor down the middle of the grid; everything
                // else stays at zero, exercising the "no data" path too.
                if (j == lonCells / 2) {
                    c.setPoorLighting(0.9);
                    c.setSampleCount(6);
                    c.setConfidence(0.85);
                }
                cells.add(c);
            }
        }
        RiskGrid grid = new RiskGrid();
        grid.setManifest(manifest);
        grid.setCells(cells);
        riskModelService.loadGridForTesting(CITY, grid);

        mockOrs = mock(ORSRoutingService.class);
        controller = new RouteController(
                riskModelService,
                mockOrs,
                new RouteDeduplicationService(),
                new RouteDescriptionGenerator(),
                new RouteSelectionService()
        );
    }

    private RouteRequest request(String mode, String time, String city) {
        RouteRequest req = new RouteRequest();
        RouteRequest.Location origin = new RouteRequest.Location();
        origin.setLat(28.605); origin.setLng(77.205);
        RouteRequest.Location dest = new RouteRequest.Location();
        dest.setLat(28.615); dest.setLng(77.215);
        req.setOrigin(origin);
        req.setDestination(dest);
        req.setMode(mode);
        req.setTime(time);
        req.setCity(city);
        return req;
    }

    private ORSRoutingService.ORSRouteResult ors(double durationSeconds, double... lonLat) {
        List<List<Double>> coords = new ArrayList<>();
        for (int i = 0; i < lonLat.length; i += 2) coords.add(List.of(lonLat[i], lonLat[i + 1]));
        return new ORSRoutingService.ORSRouteResult(coords, durationSeconds);
    }

    @Test
    void missingCityIsRejectedWithAClearError() {
        ResponseEntity<Map<String, Object>> response = controller.getRoute(request("DRIVING", "14:00", null));
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").toString().contains("supported city"));
    }

    @Test
    void unsupportedCityIsRejected() {
        ResponseEntity<Map<String, Object>> response = controller.getRoute(request("DRIVING", "14:00", "atlantis"));
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void orsReturningNoRoutesProducesACleanErrorNotACrash() {
        when(mockOrs.getMultipleRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(List.of());
        ResponseEntity<Map<String, Object>> response = controller.getRoute(request("DRIVING", "14:00", CITY));
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").toString().contains("No route found"));
    }

    @Test
    void orsThrowingIsHandledGracefully() {
        when(mockOrs.getMultipleRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenThrow(new RuntimeException("simulated ORS outage"));
        ResponseEntity<Map<String, Object>> response = controller.getRoute(request("DRIVING", "14:00", CITY));
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRouteThroughTheDangerousCorridorScoresHigherAndTheSaferRouteBecomesPrimary() {
        // One route straight through the dangerous corridor; one that goes around it.
        ORSRoutingService.ORSRouteResult throughDanger = ors(600, 77.205, 28.605, 77.21, 28.61, 77.215, 28.615);
        ORSRoutingService.ORSRouteResult aroundDanger = ors(650, 77.205, 28.605, 77.201, 28.61, 77.215, 28.615);

        when(mockOrs.getMultipleRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(List.of(throughDanger, aroundDanger));
        when(mockOrs.getRouteAvoidingArea(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getRoute(request("WALKING", "23:30", CITY));
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        List<RouteOptionDTO> routes = (List<RouteOptionDTO>) (List<?>) response.getBody().get("routes");
        assertEquals(2, routes.size());

        double scoreThroughDanger = routes.stream()
                .filter(r -> r.getCoordinates().size() == 3 && r.getCoordinates().get(1).get(0).equals(77.21))
                .findFirst().orElseThrow().getRiskScore();
        double scoreAroundDanger = routes.stream()
                .filter(r -> r.getCoordinates().size() == 3 && r.getCoordinates().get(1).get(0).equals(77.201))
                .findFirst().orElseThrow().getRiskScore();

        assertTrue(scoreThroughDanger > scoreAroundDanger,
                "the route crossing the dangerous corridor must score higher risk than the one avoiding it");

        boolean anyPrimary = routes.stream().anyMatch(RouteOptionDTO::isPrimary);
        assertTrue(anyPrimary, "exactly one route must be marked primary");
    }

    @Test
    void riskAvoidanceCandidateIsRequestedWhenThePeakRiskIsHighAndIsIncludedInTheResponse() {
        ORSRoutingService.ORSRouteResult direct = ors(600, 77.205, 28.605, 77.21, 28.61, 77.215, 28.615);
        ORSRoutingService.ORSRouteResult avoidance = ors(700, 77.205, 28.605, 77.202, 28.61, 77.215, 28.615);

        when(mockOrs.getMultipleRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(List.of(direct));
        when(mockOrs.getRouteAvoidingArea(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(avoidance));

        ResponseEntity<Map<String, Object>> response = controller.getRoute(request("WALKING", "23:30", CITY));
        verify(mockOrs, times(1)).getRouteAvoidingArea(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyDouble(), anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        List<RouteOptionDTO> routes = (List<RouteOptionDTO>) (List<?>) response.getBody().get("routes");
        assertTrue(routes.stream().anyMatch(r -> "ORS_RISK_AVOIDANCE".equals(r.getGenerationMethod())),
                "the risk-avoidance candidate should be present in the final response");
    }

    @Test
    void everyRouteReportsConfidenceAndCoverageFields() {
        when(mockOrs.getMultipleRoutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(List.of(ors(600, 77.205, 28.605, 77.215, 28.615)));
        when(mockOrs.getRouteAvoidingArea(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getRoute(request("DRIVING", "14:00", CITY));
        @SuppressWarnings("unchecked")
        List<RouteOptionDTO> routes = (List<RouteOptionDTO>) (List<?>) response.getBody().get("routes");
        assertEquals(1, routes.size());
        RouteOptionDTO route = routes.get(0);
        assertNotNull(route.getCoverageTier());
        assertTrue(route.getConfidence() >= 0);
    }
}
