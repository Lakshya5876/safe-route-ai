package com.saferoute.backend.service;

import com.saferoute.backend.model.RouteOptionDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes route options that are structurally too similar (e.g. ORS returning
 * near-identical alternatives). Two fixes relative to the original
 * implementation, both found during the earlier engineering audit — and a
 * third bug found and fixed against this class's OWN first rewrite, during
 * testing (see the note on parameterization below):
 *
 * 1. Distance is measured as a SYMMETRIC NEAREST-POINT (Hausdorff-style)
 *    distance, not by comparing points at "the same fraction along each
 *    path." The first fix attempted here sampled both routes by cumulative
 *    ARC LENGTH instead of the original vertex-index fraction — correct as
 *    far as it went, but testing exposed a subtler problem: a real detour
 *    changes a route's TOTAL length, so "the same fraction of each route's
 *    own length" (or even "the same absolute distance from the shared start")
 *    silently drifts out of alignment for the entire remainder of the route
 *    past the point of divergence, inflating the measured difference far
 *    beyond the actual local detour. Comparing each sampled point against the
 *    CLOSEST point anywhere on the other polyline sidesteps parameterization
 *    entirely: points before/after a short detour still find a near-zero
 *    match on the other path, and only the genuinely offset stretch shows a
 *    real distance — which is what "how different are these two paths"
 *    should mean.
 *
 * 2. Similarity requires BOTH a low average deviation AND a low PEAK
 *    deviation. A short localized difference (e.g. a detour around one
 *    dangerous block) can be small on average across 50 samples while still
 *    being a real, safety-relevant divergence — requiring the peak to also be
 *    small ensures it is never discarded as a duplicate.
 */
@Service
public class RouteDeduplicationService {

    private static final double DEFAULT_AVG_THRESHOLD_METERS = 50.0;
    private static final double DEFAULT_PEAK_THRESHOLD_METERS = 120.0;
    private static final int SAMPLE_POINTS = 50;

    public List<RouteOptionDTO> filterDuplicateRoutes(
            List<RouteOptionDTO> routes,
            double avgThresholdMeters,
            double peakThresholdMeters
    ) {
        if (routes == null || routes.size() <= 1) return routes == null ? new ArrayList<>() : new ArrayList<>(routes);

        List<RouteOptionDTO> result = new ArrayList<>(routes);
        boolean[] remove = new boolean[result.size()];

        for (int i = 0; i < result.size(); i++) {
            if (remove[i]) continue;
            RouteOptionDTO a = result.get(i);
            List<List<Double>> coordsA = a.getCoordinates();
            if (coordsA == null || coordsA.size() < 2) continue;

            for (int j = i + 1; j < result.size(); j++) {
                if (remove[j]) continue;
                RouteOptionDTO b = result.get(j);
                List<List<Double>> coordsB = b.getCoordinates();
                if (coordsB == null || coordsB.size() < 2) continue;

                Deviation dev = computeDeviation(coordsA, coordsB);
                boolean isDuplicate = dev.avgM < avgThresholdMeters && dev.peakM < peakThresholdMeters;
                if (!isDuplicate) continue;

                double scoreA = a.getRiskScore();
                double scoreB = b.getRiskScore();
                if (scoreA <= scoreB) {
                    remove[j] = true;
                } else {
                    remove[i] = true;
                    break;
                }
            }
        }

        List<RouteOptionDTO> filtered = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            if (!remove[i]) filtered.add(result.get(i));
        }
        return filtered;
    }

    public List<RouteOptionDTO> filterDuplicateRoutes(List<RouteOptionDTO> routes) {
        return filterDuplicateRoutes(routes, DEFAULT_AVG_THRESHOLD_METERS, DEFAULT_PEAK_THRESHOLD_METERS);
    }

    public static final class Deviation {
        public final double avgM;
        public final double peakM;
        public Deviation(double avgM, double peakM) { this.avgM = avgM; this.peakM = peakM; }
    }

    /**
     * Symmetric nearest-point deviation: resample each path by its own arc
     * length, measure each sample's distance to the CLOSEST point anywhere on
     * the other polyline, and combine both directions (average of the two
     * directional averages; max of the two directional peaks).
     */
    public Deviation computeDeviation(List<List<Double>> coordsA, List<List<Double>> coordsB) {
        if (coordsA == null || coordsB == null || coordsA.size() < 2 || coordsB.size() < 2) {
            return new Deviation(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        double refLat = toDouble(coordsA.get(coordsA.size() / 2).get(1));

        double[] cumA = cumulativeLengths(coordsA);
        double[] cumB = cumulativeLengths(coordsB);
        List<double[]> samplesA = resample(coordsA, cumA, SAMPLE_POINTS);
        List<double[]> samplesB = resample(coordsB, cumB, SAMPLE_POINTS);

        double sumAB = 0, peakAB = 0;
        for (double[] p : samplesA) {
            double d = distanceToPolyline(p, coordsB, refLat);
            sumAB += d;
            peakAB = Math.max(peakAB, d);
        }
        double sumBA = 0, peakBA = 0;
        for (double[] p : samplesB) {
            double d = distanceToPolyline(p, coordsA, refLat);
            sumBA += d;
            peakBA = Math.max(peakBA, d);
        }

        double avgM = (sumAB / SAMPLE_POINTS + sumBA / SAMPLE_POINTS) / 2.0;
        double peakM = Math.max(peakAB, peakBA);
        return new Deviation(avgM, peakM);
    }

    /** Backward-compatible accessor used by callers that only need the average. */
    public double averageDeviationMeters(List<List<Double>> coordsA, List<List<Double>> coordsB) {
        return computeDeviation(coordsA, coordsB).avgM;
    }

    private static double[] cumulativeLengths(List<List<Double>> coords) {
        double[] cum = new double[coords.size()];
        cum[0] = 0;
        for (int i = 1; i < coords.size(); i++) {
            List<Double> a = coords.get(i - 1), b = coords.get(i);
            double d = 0;
            if (a != null && b != null && a.size() >= 2 && b.size() >= 2) {
                d = haversineMeters(toDouble(a.get(1)), toDouble(a.get(0)), toDouble(b.get(1)), toDouble(b.get(0)));
            }
            cum[i] = cum[i - 1] + d;
        }
        return cum;
    }

    /** Resample a path at n evenly-arc-length-spaced points. Returns [lon, lat] pairs. */
    private static List<double[]> resample(List<List<Double>> coords, double[] cum, int n) {
        double total = cum[cum.length - 1];
        List<double[]> points = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            double t = (n == 1) ? 0.5 : (double) k / (n - 1);
            double targetDist = t * total;
            int lo = 0, hi = cum.length - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                if (cum[mid] <= targetDist) lo = mid; else hi = mid - 1;
            }
            int i = Math.min(lo, coords.size() - 2);
            int j = i + 1;
            double segLen = cum[j] - cum[i];
            double frac = segLen > 0 ? (targetDist - cum[i]) / segLen : 0;
            List<Double> a = coords.get(i);
            List<Double> b = coords.get(j);
            double lon = toDouble(a.get(0)) + frac * (toDouble(b.get(0)) - toDouble(a.get(0)));
            double lat = toDouble(a.get(1)) + frac * (toDouble(b.get(1)) - toDouble(a.get(1)));
            points.add(new double[]{ lon, lat });
        }
        return points;
    }

    /** Minimum distance (meters) from a [lon,lat] point to any segment of a polyline, via a local flat projection. */
    private static double distanceToPolyline(double[] pointLonLat, List<List<Double>> polyline, double refLat) {
        double[] p = toLocalXY(pointLonLat[1], pointLonLat[0], refLat);
        double min = Double.MAX_VALUE;
        for (int i = 0; i < polyline.size() - 1; i++) {
            List<Double> a = polyline.get(i);
            List<Double> b = polyline.get(i + 1);
            if (a == null || b == null || a.size() < 2 || b.size() < 2) continue;
            double[] pa = toLocalXY(toDouble(a.get(1)), toDouble(a.get(0)), refLat);
            double[] pb = toLocalXY(toDouble(b.get(1)), toDouble(b.get(0)), refLat);
            min = Math.min(min, distancePointToSegment(p, pa, pb));
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    /** Equirectangular projection to local meters, accurate enough for city-scale route comparisons. */
    private static double[] toLocalXY(double lat, double lon, double refLat) {
        double mPerDegLat = 111320.0;
        double mPerDegLon = 111320.0 * Math.cos(Math.toRadians(refLat));
        return new double[]{ lon * mPerDegLon, lat * mPerDegLat };
    }

    private static double distancePointToSegment(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double len2 = dx * dx + dy * dy;
        double t = len2 > 0 ? ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / len2 : 0;
        t = Math.max(0, Math.min(1, t));
        double cx = a[0] + t * dx, cy = a[1] + t * dy;
        return Math.hypot(p[0] - cx, p[1] - cy);
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000; // meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o != null) try { return Double.parseDouble(o.toString()); } catch (Exception ignored) { }
        return 0;
    }
}
