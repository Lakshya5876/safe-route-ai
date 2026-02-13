import { useEffect, useRef, useCallback } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN;

const SEGMENT_COLORS = {
  SAFE: "#22c55e",
  LOW: "#eab308",
  MODERATE: "#f97316",
  HIGH: "#ef4444",
};

export default function MapView({ routes = [], selectedRouteId, onSelectRoute }) {
  const mapContainer = useRef(null);
  const mapRef = useRef(null);
  const layersRef = useRef(new Set());
  const sourcesRef = useRef(new Set());
  const animRef = useRef(null);

  const primaryId = routes.find((r) => r.primary)?.id ?? routes[0]?.id;
  const selectedId = selectedRouteId ?? primaryId;
  const selectedRoute = routes.find((r) => r.id === selectedId);

  const removeRouteLayers = useCallback((map) => {
    if (!map || !map.getStyle()) return;
    layersRef.current.forEach((id) => {
      if (map.getLayer(id)) map.removeLayer(id);
    });
    layersRef.current.clear();
    sourcesRef.current.forEach((id) => {
      if (map.getSource(id)) map.removeSource(id);
    });
    sourcesRef.current.clear();
  }, []);

  useEffect(() => {
    if (mapRef.current) return;

    if (!mapContainer.current) {
      console.error("MapView: mapContainer ref is null");
      return;
    }

    mapRef.current = new mapboxgl.Map({
      container: mapContainer.current,
      style: "mapbox://styles/mapbox/dark-v11",
      center: [77.209, 28.6139],
      zoom: 12,
    });

    return () => {
      if (animRef.current) cancelAnimationFrame(animRef.current);
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    if (!routes.length) {
      removeRouteLayers(map);
      return;
    }

    const setup = () => {
      try {
        removeRouteLayers(map);

        let allBounds = null;

        routes.forEach((route) => {
          if (!route.coordinates || route.coordinates.length < 2) {
            console.warn(`Route ${route.id} has invalid coordinates`);
            return;
          }

          const sid = `src-${route.id}`;
          const lid = `line-${route.id}`;
          // Primary route always green; secondary is green only when selected
          const isPrimary = route.primary === true;
          const isSelected = route.id === selectedId;
          const useGreen = isPrimary || isSelected;

          // Remove existing source/layer if present
          if (map.getLayer(lid)) map.removeLayer(lid);
          if (map.getSource(sid)) map.removeSource(sid);

          map.addSource(sid, {
            type: "geojson",
            data: {
              type: "Feature",
              geometry: { type: "LineString", coordinates: route.coordinates },
            },
          });
          sourcesRef.current.add(sid);

          const hitLid = `hit-${route.id}`;
          map.addLayer({
            id: hitLid,
            type: "line",
            source: sid,
            layout: { "line-join": "round", "line-cap": "round" },
            paint: { "line-color": "transparent", "line-width": 20, "line-opacity": 0 },
          });
          layersRef.current.add(hitLid);
          map.addLayer({
            id: lid,
            type: "line",
            source: sid,
            layout: { "line-join": "round", "line-cap": "round" },
            paint: {
              "line-color": useGreen ? "#00ffa6" : "rgba(100, 200, 255, 0.6)",
              "line-width": useGreen ? 5 : 3,
              "line-opacity": 0,
            },
          });
          layersRef.current.add(lid);

          const b = route.coordinates.reduce(
            (bounds, coord) => {
              if (Array.isArray(coord) && coord.length >= 2) {
                return bounds.extend(coord);
              }
              return bounds;
            },
            new mapboxgl.LngLatBounds(route.coordinates[0], route.coordinates[0])
          );
          allBounds = allBounds ? allBounds.extend(b.getNorthWest()).extend(b.getSouthEast()) : b;
        });

        if (allBounds) {
          map.fitBounds(allBounds, { padding: 50 });
        }

        // Route draw animation (opacity fade-in)
        if (animRef.current) cancelAnimationFrame(animRef.current);
        const start = performance.now();
        const duration = 800;
        const animate = (time) => {
          const t = Math.min((time - start) / duration, 1);
          const ease = 1 - Math.pow(1 - t, 2);
          routes.forEach((route) => {
            const lid = `line-${route.id}`;
            if (map.getLayer(lid)) {
              const useGreen = route.primary || route.id === selectedId;
              const base = useGreen ? 1 : 0.6;
              map.setPaintProperty(lid, "line-opacity", ease * base);
            }
          });
          if (t < 1) {
            animRef.current = requestAnimationFrame(animate);
          }
        };
        animRef.current = requestAnimationFrame(animate);
      } catch (err) {
        console.error("Error setting up routes:", err);
      }
    };

    if (map.isStyleLoaded()) {
      setup();
    } else {
      map.once("load", setup);
    }

    return () => {
      if (animRef.current) cancelAnimationFrame(animRef.current);
      removeRouteLayers(map);
    };
  }, [routes, removeRouteLayers]);

  // When selection changes: primary always green; secondary green only when selected
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded() || !routes.length) return;

    routes.forEach((route) => {
      const lid = `line-${route.id}`;
      if (!map.getLayer(lid)) return;
      const useGreen = route.primary === true || route.id === selectedId;
      map.setPaintProperty(lid, "line-color", useGreen ? "#00ffa6" : "rgba(100, 200, 255, 0.6)");
      map.setPaintProperty(lid, "line-width", useGreen ? 5 : 3);
      map.setPaintProperty(lid, "line-opacity", useGreen ? 1 : 0.6);
    });
  }, [selectedId, routes]);

  // Segment heat (only for selected route)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded() || !selectedRoute?.segments?.length) return;

    const segSourceId = "segments-heat";
    const segLayerId = "segments-heat-line";

    if (map.getSource(segSourceId)) {
      map.removeLayer(segLayerId);
      map.removeSource(segSourceId);
    }

    const features = selectedRoute.segments.map((seg) => ({
      type: "Feature",
      properties: { riskLevel: seg.riskLevel },
      geometry: { type: "LineString", coordinates: seg.coordinates },
    }));

    map.addSource(segSourceId, {
      type: "geojson",
      data: { type: "FeatureCollection", features },
    });

    map.addLayer({
      id: segLayerId,
      type: "line",
      source: segSourceId,
      layout: { "line-join": "round", "line-cap": "round" },
      paint: {
        "line-color": [
          "match",
          ["get", "riskLevel"],
          "HIGH",
          SEGMENT_COLORS.HIGH,
          "MODERATE",
          SEGMENT_COLORS.MODERATE,
          "LOW",
          SEGMENT_COLORS.LOW,
          SEGMENT_COLORS.SAFE,
        ],
        "line-width": 6,
        "line-opacity": 0.9,
      },
    });

    return () => {
      if (map.getLayer(segLayerId)) map.removeLayer(segLayerId);
      if (map.getSource(segSourceId)) map.removeSource(segSourceId);
    };
  }, [selectedRoute]);

  // Click to select route (query both hit and line layers so dull routes are easy to click)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded() || !routes.length || !onSelectRoute) return;

    const lineIds = routes.map((r) => `line-${r.id}`);
    const hitIds = routes.map((r) => `hit-${r.id}`);
    const layerIds = [...hitIds, ...lineIds];
    const handler = (e) => {
      const features = map.queryRenderedFeatures(e.point, { layers: layerIds });
      if (features.length > 0) {
        const layerId = features[0].layer.id;
        const id = layerId.replace("line-", "").replace("hit-", "");
        onSelectRoute(id);
      }
    };
    const cursorHandler = (e) => {
      const features = map.queryRenderedFeatures(e.point, { layers: layerIds });
      map.getCanvas().style.cursor = features.length > 0 ? "pointer" : "";
    };
    map.on("click", handler);
    map.on("mousemove", cursorHandler);
    return () => {
      map.off("click", handler);
      map.off("mousemove", cursorHandler);
      map.getCanvas().style.cursor = "";
    };
  }, [routes, onSelectRoute]);

  return (
    <div
      ref={mapContainer}
      style={{
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
      }}
    />
  );
}
