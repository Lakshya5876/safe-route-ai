import { useEffect, useRef } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN;

const BASE_WIDTH = 4;
const SEGMENT_WIDTH = 6;
const ACTIVE_COLOR = "#00ffa6";
const INACTIVE_COLOR = "rgba(100, 200, 255, 0.7)";

const SEGMENT_COLORS = {
  SAFE: "#22c55e",
  LOW: "#eab308",
  MODERATE: "#f97316",
  HIGH: "#ef4444",
};

function segmentColor(riskLevel) {
  return SEGMENT_COLORS[riskLevel] ?? SEGMENT_COLORS.SAFE;
}

// All ids keyed by route.id (string) for compatibility with Home selectedRouteId
function routeSourceId(id) {
  return `route-src-${id}`;
}
function routeBaseLayerId(id) {
  return `route-base-${id}`;
}
function routeHitLayerId(id) {
  return `route-hit-${id}`;
}
function segmentSourceId(routeId, segIndex) {
  return `segment-src-${routeId}-${segIndex}`;
}
function segmentLayerId(routeId, segIndex) {
  return `segment-${routeId}-${segIndex}`;
}

const PIN_START_SOURCE_ID = "route-pin-start-src";
const PIN_START_LAYER_ID = "route-pin-start";
const PIN_END_SOURCE_ID = "route-pin-end-src";
const PIN_END_LAYER_ID = "route-pin-end";

export default function MapView({ routes = [], selectedRouteId, onSelectRoute }) {
  const mapContainer = useRef(null);
  const mapRef = useRef(null);
  const layersRef = useRef(new Set());
  const sourcesRef = useRef(new Set());

  const primaryId = routes.find((r) => r.primary)?.id ?? routes[0]?.id;
  const selectedId = selectedRouteId ?? primaryId;
  const selectedRoute = routes.find((r) => r.id === selectedId);

  useEffect(() => {
    if (mapRef.current) return;
    if (!mapContainer.current) return;

    mapRef.current = new mapboxgl.Map({
      container: mapContainer.current,
      style: "mapbox://styles/mapbox/dark-v11",
      center: [79.1559, 12.9692],
      zoom: 14,
    });

    return () => {
      mapRef.current?.remove();
      mapRef.current = null;
    };
  }, []);

  /**
   * Single declarative effect: on routes or selectedRouteId change,
   * remove ALL route/segment layers and sources, then re-add in strict order.
   * Order (bottom to top): inactive bases (blue) → active base (green) → active segments (heat) → hit layers.
   * No setPaintProperty. No partial updates. Color derived only from selectedRouteId.
   */
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !routes.length) return;

    const run = () => {
      const layersToRemove = [...layersRef.current];
      const sourcesToRemove = [...sourcesRef.current];
      layersToRemove.forEach((id) => {
        if (map.getLayer(id)) map.removeLayer(id);
      });
      sourcesToRemove.forEach((id) => {
        if (map.getSource(id)) map.removeSource(id);
      });
      layersRef.current.clear();
      sourcesRef.current.clear();

      console.log("[MapView] full re-render, selectedRouteId:", selectedId);

      // 1) Add all sources (route geometry + segment geometry for active only)
      routes.forEach((route) => {
        if (!route.coordinates || route.coordinates.length < 2) return;
        const sid = routeSourceId(route.id);
        map.addSource(sid, {
          type: "geojson",
          data: {
            type: "Feature",
            geometry: { type: "LineString", coordinates: route.coordinates },
          },
        });
        sourcesRef.current.add(sid);
      });

      if (selectedRoute?.segments?.length) {
        selectedRoute.segments.forEach((seg, j) => {
          if (!seg.coordinates || seg.coordinates.length < 2) return;
          const segSrcId = segmentSourceId(selectedRoute.id, j);
          map.addSource(segSrcId, {
            type: "geojson",
            data: {
              type: "Feature",
              geometry: { type: "LineString", coordinates: seg.coordinates },
            },
          });
          sourcesRef.current.add(segSrcId);
        });
      }

      // 2) Add layers in draw order: inactive bases first, then active base, then active segments, then hits

      // 2a) Inactive route bases (dull blue) – drawn first so they sit under active
      routes.forEach((route) => {
        if (!route.coordinates || route.coordinates.length < 2) return;
        if (route.id === selectedId) return;

        const lid = routeBaseLayerId(route.id);
        map.addLayer({
          id: lid,
          type: "line",
          source: routeSourceId(route.id),
          layout: { "line-join": "round", "line-cap": "round" },
          paint: {
            "line-color": INACTIVE_COLOR,
            "line-width": BASE_WIDTH,
            "line-opacity": 1,
          },
        });
        layersRef.current.add(lid);
      });

      // 2b) Active route base (green only) – never orange; orange is segment overlay only
      if (selectedRoute?.coordinates?.length >= 2) {
        const lid = routeBaseLayerId(selectedRoute.id);
        map.addLayer({
          id: lid,
          type: "line",
          source: routeSourceId(selectedRoute.id),
          layout: { "line-join": "round", "line-cap": "round" },
          paint: {
            "line-color": ACTIVE_COLOR,
            "line-width": BASE_WIDTH,
            "line-opacity": 1,
          },
        });
        layersRef.current.add(lid);
      }

      // 2c) Active route segment overlays (heat) – on top of active base; red/orange/yellow by risk
      if (selectedRoute?.segments?.length) {
        const riskDistribution = {};
        selectedRoute.segments.forEach((seg, j) => {
          if (!seg.coordinates || seg.coordinates.length < 2) return;
          const level = seg.riskLevel ?? "SAFE";
          riskDistribution[level] = (riskDistribution[level] || 0) + 1;

          const segLid = segmentLayerId(selectedRoute.id, j);
          map.addLayer({
            id: segLid,
            type: "line",
            source: segmentSourceId(selectedRoute.id, j),
            layout: { "line-join": "round", "line-cap": "round" },
            paint: {
              "line-color": segmentColor(level),
              "line-width": SEGMENT_WIDTH,
              "line-opacity": 0.95,
            },
          });
          layersRef.current.add(segLid);
        });
        console.log("[MapView] active route segments:", selectedRoute.segments.length, "riskLevels:", riskDistribution);
      } else {
        console.log("[MapView] active route has no segments");
      }

      // 2d) Hit areas for all routes (on top, for clicks)
      routes.forEach((route) => {
        if (!route.coordinates || route.coordinates.length < 2) return;
        const hitLid = routeHitLayerId(route.id);
        map.addLayer({
          id: hitLid,
          type: "line",
          source: routeSourceId(route.id),
          layout: { "line-join": "round", "line-cap": "round" },
          paint: { "line-color": "transparent", "line-width": 20, "line-opacity": 0 },
        });
        layersRef.current.add(hitLid);
      });

      // 2e) Start and end pins for the active route (on top of everything)
      if (selectedRoute?.coordinates?.length >= 2) {
        const startCoord = selectedRoute.coordinates[0];
        const endCoord = selectedRoute.coordinates[selectedRoute.coordinates.length - 1];
        if (Array.isArray(startCoord) && startCoord.length >= 2) {
          map.addSource(PIN_START_SOURCE_ID, {
            type: "geojson",
            data: {
              type: "Feature",
              geometry: { type: "Point", coordinates: startCoord },
            },
          });
          sourcesRef.current.add(PIN_START_SOURCE_ID);
          map.addLayer({
            id: PIN_START_LAYER_ID,
            type: "circle",
            source: PIN_START_SOURCE_ID,
            paint: {
              "circle-radius": 8,
              "circle-color": "#ffffff",
              "circle-stroke-width": 2,
              "circle-stroke-color": "#6b7280",
            },
          });
          layersRef.current.add(PIN_START_LAYER_ID);
        }
        if (Array.isArray(endCoord) && endCoord.length >= 2) {
          map.addSource(PIN_END_SOURCE_ID, {
            type: "geojson",
            data: {
              type: "Feature",
              geometry: { type: "Point", coordinates: endCoord },
            },
          });
          sourcesRef.current.add(PIN_END_SOURCE_ID);
          map.addLayer({
            id: PIN_END_LAYER_ID,
            type: "circle",
            source: PIN_END_SOURCE_ID,
            paint: {
              "circle-radius": 10,
              "circle-color": "#dc2626",
              "circle-stroke-width": 2,
              "circle-stroke-color": "#991b1b",
            },
          });
          layersRef.current.add(PIN_END_LAYER_ID);
        }
      }

      let allBounds = null;
      routes.forEach((route) => {
        if (!route.coordinates || route.coordinates.length < 2) return;
        const b = route.coordinates.reduce(
          (bounds, coord) => {
            if (Array.isArray(coord) && coord.length >= 2) return bounds.extend(coord);
            return bounds;
          },
          new mapboxgl.LngLatBounds(route.coordinates[0], route.coordinates[0])
        );
        allBounds = allBounds ? allBounds.extend(b.getNorthWest()).extend(b.getSouthEast()) : b;
      });
      if (allBounds) map.fitBounds(allBounds, { padding: 50 });

      console.log("[MapView] layers:", layersRef.current.size, "sources:", sourcesRef.current.size);
    };

    if (map.isStyleLoaded()) run();
    else map.once("load", run);

    return () => {
      layersRef.current.forEach((id) => {
        if (map.getLayer(id)) map.removeLayer(id);
      });
      sourcesRef.current.forEach((id) => {
        if (map.getSource(id)) map.removeSource(id);
      });
      layersRef.current.clear();
      sourcesRef.current.clear();
    };
  }, [routes, selectedId]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded() || !routes.length || typeof onSelectRoute !== "function") return;

    const hitIds = routes.map((r) => routeHitLayerId(r.id));
    const baseIds = routes.map((r) => routeBaseLayerId(r.id));
    const layerIds = [...hitIds, ...baseIds];

    const handler = (e) => {
      const features = map.queryRenderedFeatures(e.point, { layers: layerIds });
      if (features.length === 0) return;
      const layerId = features[0].layer.id;
      const m = layerId.match(/^route-(?:base|hit)-(.+)$/);
      if (m) onSelectRoute(m[1]);
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
      style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
    />
  );
}
