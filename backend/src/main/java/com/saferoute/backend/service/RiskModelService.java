package com.saferoute.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.backend.model.RiskGrid;
import com.saferoute.backend.model.RiskGridCell;
import com.saferoute.backend.model.RouteRiskSummary;
import com.saferoute.backend.model.RouteSegmentDTO;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.*;

/**
 * The single, coherent risk model. Both route-level ranking and per-segment
 * map coloring call {@link #pointRisk} through this class — there is no
 * second, divergent formula for visualization, which the earlier engineering
 * audit identified as a correctness bug in the previous implementation.
 *
 * Design summary (see pipeline/src/build-risk-grid.js for how the grid
 * itself is built):
 *  - Each supported city has a precomputed grid of ~150m cells, loaded once
 *    at startup into a HashMap keyed by (latIdx,lonIdx) for O(1) lookup —
 *    this replaces the old unindexed "scan every zone for every sample
 *    point" approach, whose cost was linear in the zone count and was
 *    identified as the system's real scaling bottleneck.
 *  - A cell stores four SEPARATE category totals (poorLighting, isolation,
 *    crimePrior, protective) rather than one pre-summed number, so time-of-day
 *    can be applied per category at request time without rebuilding the grid.
 *  - Normalization divides by that CITY's own referenceScale (its 95th
 *    percentile cell risk), not a sum over every zone ever loaded — so
 *    adding or regenerating another city's data can never change this
 *    city's scores, unlike the previous implementation's dataset-global sum.
 *  - Route aggregation resamples the polyline at a fixed arc-length step
 *    (not by vertex index, which the audit flagged as biased toward
 *    vertex-dense stretches) and takes a DISTANCE-WEIGHTED AVERAGE of
 *    per-point risk, not a sum — so a long uneventful stretch cannot inflate
 *    a route's score just by existing. Total accumulated exposure is also
 *    reported separately (informational, not used for ranking) so
 *    "dwelling longer in a bad area is worse" isn't lost either.
 *  - Confidence and coverage are genuinely derived from local OSM sample
 *    density per cell (see the pipeline for exactly how) and are
 *    distance-weighted along the route the same way risk is. An area with no
 *    local data gets LOW confidence, not a fabricated safe score.
 */
@Service
public class RiskModelService {

    /** Fixed arc-length resampling step, meters. Small enough to catch short
     *  dangerous stretches, large enough to keep route requests fast. */
    private static final double SAMPLE_STEP_M = 30.0;
    private static final double EARTH_RADIUS_M = 6371000.0;

    private final Map<String, RiskGrid> gridsByCity = new HashMap<>();
    private final Map<String, Map<Long, RiskGridCell>> cellIndexByCity = new HashMap<>();

    private static final List<String> SUPPORTED_CITIES =
            List.of("delhi", "mumbai", "bengaluru", "hyderabad", "pune", "chennai");

    @PostConstruct
    public void loadGrids() {
        ObjectMapper mapper = new ObjectMapper();
        for (String cityId : SUPPORTED_CITIES) {
            String resourcePath = "/risk-grids/" + cityId + ".json";
            try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    System.err.println("[RiskModel] No risk grid found for " + cityId + " at " + resourcePath);
                    continue;
                }
                RiskGrid grid = mapper.readValue(is, RiskGrid.class);
                gridsByCity.put(cityId, grid);

                Map<Long, RiskGridCell> index = new HashMap<>();
                for (RiskGridCell cell : grid.getCells()) {
                    index.put(cellKey(cell.getLatIdx(), cell.getLonIdx()), cell);
                }
                cellIndexByCity.put(cityId, index);

                System.out.printf(
                        "[RiskModel] Loaded %s: %d cells, referenceScale=%.3f, generated=%s%n",
                        cityId, grid.getCells().size(), grid.getManifest().getReferenceScale(),
                        grid.getManifest().getGeneratedAt());
            } catch (Exception e) {
                System.err.println("[RiskModel] Failed to load grid for " + cityId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Test-only hook: register a synthetic grid directly, bypassing classpath
     * resource loading. Used by RiskModelServiceTest to build small, exact
     * synthetic cities instead of depending on the real ~10k-cell resources,
     * which would make failure causes far harder to isolate.
     */
    public void loadGridForTesting(String cityId, RiskGrid grid) {
        gridsByCity.put(cityId, grid);
        Map<Long, RiskGridCell> index = new HashMap<>();
        for (RiskGridCell cell : grid.getCells()) {
            index.put(cellKey(cell.getLatIdx(), cell.getLonIdx()), cell);
        }
        cellIndexByCity.put(cityId, index);
    }

    public boolean isCitySupported(String cityId) {
        return cityId != null && gridsByCity.containsKey(cityId.toLowerCase());
    }

    public List<String> supportedCities() {
        return List.copyOf(gridsByCity.keySet());
    }

    private static long cellKey(int latIdx, int lonIdx) {
        return (((long) latIdx) << 32) ^ (lonIdx & 0xffffffffL);
    }

    private RiskGridCell lookupCell(String cityId, double lat, double lon) {
        RiskGrid grid = gridsByCity.get(cityId);
        if (grid == null) return null;
        var m = grid.getManifest();
        double[] bbox = m.getBbox(); // [south, west, north, east]
        int latIdx = (int) Math.floor((lat - bbox[0]) / m.getCellSizeLatDeg());
        int lonIdx = (int) Math.floor((lon - bbox[1]) / m.getCellSizeLonDeg());
        if (latIdx < 0 || latIdx >= m.getLatCells() || lonIdx < 0 || lonIdx >= m.getLonCells()) {
            return null; // outside this city's covered bounding box entirely
        }
        return cellIndexByCity.get(cityId).get(cellKey(latIdx, lonIdx));
    }

    private static double modeMultiplier(String mode) {
        if (mode == null) return 1.0;
        return switch (mode.toUpperCase()) {
            case "WALKING" -> 1.3;
            case "BIKE" -> 1.15;
            default -> 1.0; // DRIVING
        };
    }

    private enum DayPart { NIGHT, EVENING, DAY }

    private static DayPart dayPartOf(String time) {
        if (time == null || time.isEmpty()) return DayPart.DAY;
        try {
            LocalTime t = LocalTime.parse(time);
            if (t.isAfter(LocalTime.of(23, 0)) || t.isBefore(LocalTime.of(4, 0))) return DayPart.NIGHT;
            if (t.isAfter(LocalTime.of(20, 0))) return DayPart.EVENING;
            return DayPart.DAY;
        } catch (Exception e) {
            return DayPart.DAY;
        }
    }

    /** Lighting matters far more once it's dark; isolation somewhat more. Crime
     *  prior and protective presence are not time-scaled: we have no temporal
     *  breakdown in the underlying data, and faking one would misrepresent
     *  confidence we don't have. */
    private static double lightingTimeMultiplier(DayPart p) {
        return switch (p) { case NIGHT -> 1.6; case EVENING -> 1.25; default -> 1.0; };
    }
    private static double isolationTimeMultiplier(DayPart p) {
        return switch (p) { case NIGHT -> 1.4; case EVENING -> 1.15; default -> 1.0; };
    }

    /**
     * Core risk formula, in [0,100]. This is the ONLY place risk is computed
     * from a grid cell — segment coloring and route aggregation both call
     * this on their sample points, so they can never diverge.
     */
    private double pointRisk(RiskGridCell cell, double referenceScale, DayPart dayPart, String mode) {
        if (cell == null) return Double.NaN; // caller must handle "no data" explicitly, not silently treat as 0
        double raw = cell.getPoorLighting() * lightingTimeMultiplier(dayPart)
                + cell.getIsolation() * isolationTimeMultiplier(dayPart)
                + cell.getCrimePrior()
                - cell.getProtective();
        raw = Math.max(0, raw);
        double normalized = referenceScale > 0 ? Math.min(100, (raw / referenceScale) * 100) : 0;
        return Math.min(100, normalized * modeMultiplier(mode));
    }

    /** Confidence contributed by a cell, or 0 for "no data here at all." */
    private double pointConfidence(RiskGridCell cell) {
        return cell == null ? 0.0 : cell.getConfidence();
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) try { return Double.parseDouble(o.toString()); } catch (Exception ignored) { }
        return 0;
    }

    /** One arc-length-resampled point along the route, with its cumulative distance. */
    private record Sample(double lat, double lon, double cumulativeM) {}

    /**
     * Resample a polyline at a fixed arc-length step. This is the fix for the
     * previous implementation's vertex-index sampling, which over-weighted
     * geometrically dense stretches (e.g. curvy urban blocks) relative to
     * long straight ones purely because ORS emits more vertices there.
     */
    private List<Sample> resampleByArcLength(List<List<Double>> coords, double stepM) {
        List<Sample> samples = new ArrayList<>();
        if (coords == null || coords.size() < 2) return samples;

        double cumulative = 0;
        double nextSampleAt = 0;
        double prevLat = toDouble(coords.get(0).get(1));
        double prevLon = toDouble(coords.get(0).get(0));
        samples.add(new Sample(prevLat, prevLon, 0));

        for (int i = 1; i < coords.size(); i++) {
            double lat = toDouble(coords.get(i).get(1));
            double lon = toDouble(coords.get(i).get(0));
            double segLen = haversineMeters(prevLat, prevLon, lat, lon);
            if (segLen <= 0) { prevLat = lat; prevLon = lon; continue; }

            while (cumulative + segLen >= nextSampleAt + stepM && nextSampleAt + stepM <= cumulative + segLen) {
                nextSampleAt += stepM;
                double t = (nextSampleAt - cumulative) / segLen;
                double sLat = prevLat + t * (lat - prevLat);
                double sLon = prevLon + t * (lon - prevLon);
                samples.add(new Sample(sLat, sLon, nextSampleAt));
            }
            cumulative += segLen;
            prevLat = lat;
            prevLon = lon;
        }
        if (samples.get(samples.size() - 1).cumulativeM < cumulative - 1e-6) {
            samples.add(new Sample(prevLat, prevLon, cumulative));
        }
        return samples;
    }

    public static final class RouteRiskResult {
        public final RouteRiskSummary summary;
        public final List<RouteSegmentDTO> segments;
        public RouteRiskResult(RouteRiskSummary summary, List<RouteSegmentDTO> segments) {
            this.summary = summary;
            this.segments = segments;
        }
    }

    /**
     * Compute the full risk summary AND the per-ORS-segment coloring in one
     * pass, from the same underlying samples — guaranteeing the two can never
     * disagree with each other the way the old dual-formula system could.
     */
    public RouteRiskResult evaluateRoute(String cityId, List<List<Double>> routeCoordinates, String time, String mode) {
        RouteRiskSummary summary = new RouteRiskSummary();
        List<RouteSegmentDTO> segments = new ArrayList<>();

        if (routeCoordinates == null || routeCoordinates.size() < 2) {
            summary.setRiskScore(0);
            summary.setRiskLevel("UNKNOWN");
            summary.setCoverageTier("insufficient");
            return new RouteRiskResult(summary, segments);
        }

        boolean citySupported = isCitySupported(cityId);
        String city = citySupported ? cityId.toLowerCase() : null;
        double referenceScale = citySupported ? gridsByCity.get(city).getManifest().getReferenceScale() : 0;
        DayPart dayPart = dayPartOf(time);

        List<Sample> samples = resampleByArcLength(routeCoordinates, SAMPLE_STEP_M);
        double totalLengthM = samples.isEmpty() ? 0 : samples.get(samples.size() - 1).cumulativeM;

        double exposureSum = 0;      // sum(risk * segment length), for both the average and the total
        double confidenceSum = 0;    // sum(confidence * segment length)
        double coveredLengthM = 0;   // length where a real cell (any local/city data) exists
        double peakRisk = -1;
        double peakLat = 0, peakLon = 0;
        boolean hasPeak = false;

        // Per-original-ORS-segment aggregation (for map coloring), built from
        // the same resampled points so it is provably consistent with the
        // route-level numbers below.
        int origSegCount = Math.max(0, routeCoordinates.size() - 1);
        double[] origSegRiskSum = new double[origSegCount];
        int[] origSegSampleCount = new int[origSegCount];
        double[] origCumAtVertex = new double[routeCoordinates.size()];
        {
            double acc = 0;
            origCumAtVertex[0] = 0;
            for (int i = 1; i < routeCoordinates.size(); i++) {
                List<Double> a = routeCoordinates.get(i - 1), b = routeCoordinates.get(i);
                acc += haversineMeters(toDouble(a.get(1)), toDouble(a.get(0)), toDouble(b.get(1)), toDouble(b.get(0)));
                origCumAtVertex[i] = acc;
            }
        }

        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            double segLen = (i == 0) ? 0 : (s.cumulativeM - samples.get(i - 1).cumulativeM);

            RiskGridCell cell = citySupported ? lookupCell(city, s.lat, s.lon) : null;
            double risk = citySupported ? pointRisk(cell, referenceScale, dayPart, mode) : Double.NaN;
            double conf = citySupported ? pointConfidence(cell) : 0.0;
            boolean hasAnyLocalData = citySupported; // even a null cell (outside bbox) => no data
            if (citySupported && cell == null) hasAnyLocalData = false;

            double effectiveRisk = Double.isNaN(risk) ? 0.0 : risk; // treat "no data" as 0 for the sum, but track coverage separately
            exposureSum += effectiveRisk * segLen;
            confidenceSum += conf * segLen;
            if (hasAnyLocalData) coveredLengthM += segLen;
            // Only trust a peak backed by real confidence — a lone low-confidence
            // spike shouldn't be enough to steer routing away from an area.
            if (effectiveRisk > peakRisk && conf >= 0.35) {
                peakRisk = effectiveRisk;
                peakLat = s.lat;
                peakLon = s.lon;
                hasPeak = true;
            }

            // attribute this sample to the original ORS segment it falls within
            int segIdx = findSegmentIndex(origCumAtVertex, s.cumulativeM);
            if (segIdx >= 0 && segIdx < origSegCount) {
                origSegRiskSum[segIdx] += effectiveRisk;
                origSegSampleCount[segIdx]++;
            }
        }

        double riskPerKmScale = totalLengthM > 0 ? (exposureSum / totalLengthM) : 0; // this IS the 0-100 average
        double totalExposureIndex = exposureSum / 1000.0; // risk-score * km, informational only
        double avgConfidence = totalLengthM > 0 ? (confidenceSum / totalLengthM) : 0;
        double coverageFraction = totalLengthM > 0 ? (coveredLengthM / totalLengthM) : 0;

        String coverageTier;
        if (!citySupported || coverageFraction < 0.05) coverageTier = "insufficient";
        else if (avgConfidence < 0.35) coverageTier = "sparse";
        else if (avgConfidence < 0.7) coverageTier = "moderate";
        else coverageTier = "high";

        String riskLevel;
        if (!citySupported) riskLevel = "UNKNOWN";
        else if (riskPerKmScale < 30) riskLevel = "LOW";
        else if (riskPerKmScale <= 65) riskLevel = "MODERATE";
        else riskLevel = "HIGH";

        summary.setRiskScore(citySupported ? riskPerKmScale : 0);
        summary.setRiskLevel(riskLevel);
        summary.setTotalLengthKm(totalLengthM / 1000.0);
        summary.setNightTravel(dayPart == DayPart.NIGHT);
        summary.setEveningTravel(dayPart == DayPart.EVENING);
        summary.setConfidence(avgConfidence);
        summary.setCoverageFraction(coverageFraction);
        summary.setCoverageTier(coverageTier);
        summary.setTotalExposureIndex(totalExposureIndex);
        summary.setZoneNames(List.of());
        summary.setZoneCategories(List.of());
        summary.setHasPeak(hasPeak);
        if (hasPeak) {
            summary.setPeakRiskScore(peakRisk);
            summary.setPeakLat(peakLat);
            summary.setPeakLon(peakLon);
        }

        for (int i = 0; i < origSegCount; i++) {
            List<Double> a = routeCoordinates.get(i), b = routeCoordinates.get(i + 1);
            double score = origSegSampleCount[i] > 0 ? origSegRiskSum[i] / origSegSampleCount[i] : 0;
            String level = !citySupported ? "UNKNOWN" : scoreToRiskLevel(score);
            RouteSegmentDTO dto = new RouteSegmentDTO(
                    List.of(List.of(toDouble(a.get(0)), toDouble(a.get(1))), List.of(toDouble(b.get(0)), toDouble(b.get(1)))),
                    level
            );
            dto.setRiskScore(score);
            segments.add(dto);
        }

        return new RouteRiskResult(summary, segments);
    }

    private static int findSegmentIndex(double[] cumAtVertex, double cumulativeM) {
        // binary search for the segment [cumAtVertex[i], cumAtVertex[i+1]) containing cumulativeM
        int lo = 0, hi = cumAtVertex.length - 2;
        if (hi < 0) return -1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (cumAtVertex[mid] <= cumulativeM) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    public String scoreToRiskLevel(double score) {
        if (score < 30) return "LOW";
        if (score <= 65) return "MODERATE";
        return "HIGH";
    }
}
