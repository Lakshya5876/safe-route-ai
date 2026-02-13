import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  MapPin,
  Navigation,
  ShieldCheck,
  Clock,
  Car,
  Footprints,
  Bike,
} from "lucide-react";

import MapView from "../components/MapView";
import GlobeScene from "../components/GlobeScene";
import LocationInput from "../components/LocationInput";
import { fetchSafeRoute } from "../services/api";
import { geocodeLocation } from "../services/geocode";

export default function Home() {
  const [showMap, setShowMap] = useState(false);
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [time, setTime] = useState("");
  const [travelMode, setTravelMode] = useState("DRIVING");

  const [routeData, setRouteData] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleRouteSearch() {
    try {
      if (!origin || !destination) {
        alert("Please enter both origin and destination.");
        return;
      }

      setLoading(true);

      // 🔹 1. Convert text → coordinates using Mapbox
      const originCoords = await geocodeLocation(origin);
      const destinationCoords = await geocodeLocation(destination);

      console.log("Geocoded origin:", originCoords);
      console.log("Geocoded destination:", destinationCoords);

      // 🔹 2. Send coordinates to backend
      const response = await fetchSafeRoute({
        origin: originCoords,
        destination: destinationCoords,
        time: time || "14:00",
        mode: travelMode,
      });

      console.log("Backend response:", response);

      setRouteData(response);
      setShowMap(true);

    } catch (error) {
      console.error("API error:", error);
      alert("Location not found or server error.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={page}>
      <AnimatePresence>
        {showMap && routeData && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            style={{
              ...fullScreenLayer,
              pointerEvents: "auto",
            }}
          >
            <MapView route={routeData.route} />
            <div style={mapOverlayFade} />
          </motion.div>
        )}
      </AnimatePresence>

      <div
        style={{
          ...contentWrapper,
          justifyContent: showMap ? "flex-start" : "center",
          paddingLeft: showMap ? "50px" : "0",
          pointerEvents: "none",
        }}
      >
        <motion.div
          layout
          transition={{ duration: 0.6, ease: "circOut" }}
          style={{
            ...card,
            pointerEvents: "auto",
          }}
        >
          <div style={header}>
            <div style={logoBox}>
              <ShieldCheck size={20} color="#00ffa6" />
            </div>
            <div>
              <div style={title}>SafeRoute AI</div>
              <div style={subtitle}>Safety-first navigation</div>
            </div>
          </div>

          <div style={form}>
            <div style={inputWrap}>
              <MapPin size={16} style={iconStyle} />
              <LocationInput
                placeholder="Start location"
                value={origin}
                setValue={setOrigin}
              />
            </div>

            <div style={inputWrap}>
              <Navigation size={16} style={iconStyle} />
              <LocationInput
                placeholder="Destination"
                value={destination}
                setValue={setDestination}
              />
            </div>

            <div style={inputWrap}>
              <Clock size={16} style={iconStyle} />
              <input
                type="time"
                value={time}
                onChange={(e) => setTime(e.target.value)}
                style={input}
              />
            </div>

            <div style={modeRow}>
              {["DRIVING", "WALKING", "BIKE"].map((m) => (
                <button
                  key={m}
                  onClick={() => setTravelMode(m)}
                  style={{
                    ...modeBtn,
                    border:
                      travelMode === m
                        ? "1px solid #00ffa6"
                        : "1px solid rgba(255,255,255,0.1)",
                    background:
                      travelMode === m
                        ? "rgba(0,255,166,0.1)"
                        : "transparent",
                  }}
                >
                  {m === "DRIVING" && <Car size={18} />}
                  {m === "WALKING" && <Footprints size={18} />}
                  {m === "BIKE" && <Bike size={18} />}
                </button>
              ))}
            </div>

            <button style={cta} onClick={handleRouteSearch}>
              {loading ? "Loading..." : "Find Safest Route"}
            </button>
          </div>
        </motion.div>

        {!showMap && (
          <div style={globeSidePanel}>
            <GlobeScene />
          </div>
        )}
      </div>
    </div>
  );
}

/* ================= STYLES ================= */

const page = {
  height: "100vh",
  width: "100vw",
  background: "transparent",
  overflow: "hidden",
  position: "relative",
};

const fullScreenLayer = {
  position: "absolute",
  inset: 0,
  zIndex: 1,
};

const mapOverlayFade = {
  position: "absolute",
  inset: 0,
  background:
    "linear-gradient(to right, #020617 0%, transparent 50%)",
  pointerEvents: "none",
};

const contentWrapper = {
  position: "relative",
  zIndex: 10,
  height: "100%",
  display: "flex",
  alignItems: "center",
  transition: "all 0.8s ease-in-out",
};

const card = {
  width: "400px",
  padding: "30px",
  borderRadius: "24px",
  background: "rgba(10, 20, 40, 0.9)",
  backdropFilter: "blur(20px)",
  border: "1px solid rgba(255,255,255,0.1)",
  boxShadow: "0 25px 50px rgba(0,0,0,0.5)",
  display: "flex",
  flexDirection: "column",
  gap: "20px",
  marginLeft: "5vw",
};

const globeSidePanel = {
  flex: 1,
  height: "100vh",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
};

const header = { display: "flex", alignItems: "center", gap: "12px" };

const logoBox = {
  background: "rgba(0,255,166,0.1)",
  padding: "8px",
  borderRadius: "8px",
};

const title = { fontSize: "20px", fontWeight: "bold", color: "white" };
const subtitle = { fontSize: "12px", color: "#94a3b8" };

const form = { display: "flex", flexDirection: "column", gap: "12px" };
const inputWrap = { position: "relative" };

const iconStyle = {
  position: "absolute",
  left: "12px",
  top: "50%",
  transform: "translateY(-50%)",
  color: "#64748b",
};

const input = {
  width: "100%",
  padding: "12px 44px 12px 40px",
  background: "rgba(255,255,255,0.05)",
  border: "1px solid rgba(255,255,255,0.1)",
  borderRadius: "10px",
  color: "white",
  outline: "none",
  boxSizing: "border-box",
};

const modeRow = { display: "flex", gap: "8px" };

const modeBtn = {
  flex: 1,
  height: "40px",
  borderRadius: "10px",
  color: "white",
  cursor: "pointer",
  display: "flex",
  justifyContent: "center",
  alignItems: "center",
};

const cta = {
  padding: "14px",
  borderRadius: "30px",
  border: "none",
  background: "#00ffa6",
  color: "#002b18",
  fontWeight: "bold",
  cursor: "pointer",
  marginTop: "10px",
};
