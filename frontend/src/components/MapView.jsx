import { useEffect, useRef } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN;


function MapView() {
  const mapContainer = useRef(null);

  useEffect(() => {
    const map = new mapboxgl.Map({
      container: mapContainer.current,
      style: "mapbox://styles/mapbox/dark-v11",
      center: [77.209, 28.6139], // Delhi
      zoom: 11,
    });

    new mapboxgl.Marker()
      .setLngLat([77.209, 28.6139])
      .addTo(map);

    map.on("load", () => map.resize());

    return () => map.remove();
  }, []);

  return (
    <div
      ref={mapContainer}
      style={{
        position: "absolute",
        inset: 0,
      }}
    />
  );
}

export default MapView;
