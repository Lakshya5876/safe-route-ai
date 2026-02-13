import { useEffect, useRef } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN;

export default function MapView({ route }) {
  const mapContainer = useRef(null);
  const mapRef = useRef(null);

  useEffect(() => {
    if (mapRef.current) return;

    mapRef.current = new mapboxgl.Map({
      container: mapContainer.current,
      style: "mapbox://styles/mapbox/dark-v11",
      center: [77.209, 28.6139],
      zoom: 12,
      attributionControl: false,
    });

    mapRef.current.on("load", () => {
      mapRef.current.resize();
    });

    return () => {
      mapRef.current.remove();
      mapRef.current = null;
    };
  }, []);

  // Draw route when route changes
  useEffect(() => {
    if (!mapRef.current || !route || route.length === 0) return;

    const map = mapRef.current;

    const geojson = {
      type: "Feature",
      geometry: {
        type: "LineString",
        coordinates: route,
      },
    };

    if (map.getSource("route")) {
      map.getSource("route").setData(geojson);
    } else {
      map.addSource("route", {
        type: "geojson",
        data: geojson,
      });

      map.addLayer({
        id: "route",
        type: "line",
        source: "route",
        paint: {
          "line-color": "#00ffa6",
          "line-width": 5,
        },
      });
    }

    // Auto-fit route
    const bounds = route.reduce(
      (b, coord) => b.extend(coord),
      new mapboxgl.LngLatBounds(route[0], route[0])
    );

    map.fitBounds(bounds, { padding: 80 });
  }, [route]);

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