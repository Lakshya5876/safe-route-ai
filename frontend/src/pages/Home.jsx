// import { useState } from "react";
// import MapView from "../components/MapView";

// function Home() {
//   const [showMap, setShowMap] = useState(false);

//   const [origin, setOrigin] = useState("");
//   const [destination, setDestination] = useState("");
//   const [time, setTime] = useState("");
//   const [travelMode, setTravelMode] = useState("WALKING");
//   const [safetyPreference, setSafetyPreference] = useState(50);

//   return (
//     <div
//       style={{
//         height: "100vh",
//         width: "100vw",
//         background: "#0b0f14",
//         display: "flex",
//         alignItems: "center",
//         justifyContent: "center",
//         position: "relative",
//         overflow: "hidden",
//       }}
//     >
//       {/* MAP BACKGROUND */}
//       {showMap && <MapView />}

//       {/* FORM CARD */}
//       <div
//         style={{
//           width: showMap ? "340px" : "460px",
//           padding: "32px",
//           background: "rgba(12,18,30,0.92)",
//           backdropFilter: "blur(18px)",
//           borderRadius: "20px",
//           color: "white",
//           display: "flex",
//           flexDirection: "column",
//           gap: "16px",
//           zIndex: 2,
//           boxShadow: "0 40px 80px rgba(0,0,0,0.6)",
//           transform: showMap ? "translateX(-32vw)" : "scale(1)",
//           transition: "0.5s ease",
//         }}
//       >
//         <h1 style={{ fontSize: "30px", marginBottom: "8px" }}>
//           SafeRoute AI
//         </h1>

//         <input
//           placeholder="Start location"
//           value={origin}
//           onChange={(e) => setOrigin(e.target.value)}
//           style={inputStyle}
//         />

//         <input
//           placeholder="Destination"
//           value={destination}
//           onChange={(e) => setDestination(e.target.value)}
//           style={inputStyle}
//         />

//         <input
//           type="time"
//           value={time}
//           onChange={(e) => setTime(e.target.value)}
//           style={inputStyle}
//         />

//         <select
//           value={travelMode}
//           onChange={(e) => setTravelMode(e.target.value)}
//           style={inputStyle}
//         >
//           <option value="WALKING">Walking</option>
//           <option value="DRIVING">Driving</option>
//           <option value="BIKE">Two-wheeler</option>
//         </select>

//         <div>
//           <label>Safety Priority</label>
//           <input
//             type="range"
//             min="0"
//             max="100"
//             value={safetyPreference}
//             onChange={(e) => setSafetyPreference(e.target.value)}
//             style={{ width: "100%" }}
//           />
//           <div>{safetyPreference}%</div>
//         </div>

//         <button
//           style={buttonStyle}
//           onClick={() => setShowMap(true)}
//         >
//           Find Safest Route
//         </button>
//       </div>
//     </div>
//   );
// }

// const inputStyle = {
//   padding: "14px",
//   borderRadius: "10px",
//   border: "1px solid rgba(255,255,255,0.08)",
//   background: "#141c2b",
//   color: "white",
//   fontSize: "15px",
// };

// const buttonStyle = {
//   padding: "16px",
//   background: "linear-gradient(135deg,#00c853,#00e676)",
//   border: "none",
//   borderRadius: "12px",
//   color: "white",
//   fontWeight: "bold",
//   fontSize: "16px",
//   cursor: "pointer",
// };

// export default Home;

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { 
  MapPin, Navigation, ShieldCheck, MoveRight, 
  Zap, Clock, Car, Footprints, Bike, AlertTriangle 
} from "lucide-react";
import MapView from "../components/MapView";

function Home() {
  const [showMap, setShowMap] = useState(false);
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [travelMode, setTravelMode] = useState("DRIVING");
  const [time, setTime] = useState("");
  const [riskScore] = useState(12);

  return (
    <div style={pageContainer}>
      {/* 1. MAP BACKGROUND */}
      <div style={mapLayer}>
        <MapView />
        <div style={{
            ...mapOverlay,
            background: showMap 
                ? "linear-gradient(to right, rgba(11,15,26,0.95) 0%, rgba(11,15,26,0) 50%)" 
                : "rgba(0,0,0,0.4)"
        }} />
      </div>

      {/* 2. STABLE ALIGNMENT WRAPPER */}
      <div style={{
          position: "absolute",
          inset: 0,
          display: "flex",
          alignItems: "center", // Vertically centers the card ALWAYS
          justifyContent: showMap ? "flex-start" : "center", // Horizontally moves it
          paddingLeft: showMap ? "30px" : "0",
          pointerEvents: "none", 
          zIndex: 10,
          transition: "all 0.6s cubic-bezier(0.16, 1, 0.3, 1)"
      }}>
          
        {/* 3. THE CARD */}
        <motion.div
          layout
          initial={false}
          style={{
            ...sidebarStyle,
            pointerEvents: "auto",
            width: showMap ? "380px" : "440px",
            maxHeight: "90vh", // Prevents moving out of screen
          }}
        >
          {/* Header */}
          <div style={header}>
            <div style={logoWrapper}>
              <ShieldCheck size={22} color="#00e676" />
            </div>
            <div>
              <h1 style={title}>SafeRoute AI</h1>
              <p style={subtitle}>Safety-first navigation</p>
            </div>
          </div>

          {/* Scrollable Content Area */}
          <div style={scrollArea}>
            <div style={contentStack}>
              <div style={field}>
                <MapPin size={16} style={fieldIcon} />
                <input placeholder="Start location" style={inputStyle} value={origin} onChange={(e) => setOrigin(e.target.value)} />
              </div>
              
              <div style={field}>
                <Navigation size={16} style={fieldIcon} />
                <input placeholder="Destination" style={inputStyle} value={destination} onChange={(e) => setDestination(e.target.value)} />
              </div>

              <div style={field}>
                <Clock size={16} style={fieldIcon} />
                <input type="time" style={inputStyle} value={time} onChange={(e) => setTime(e.target.value)} />
              </div>

              <div style={modeToggleRow}>
                {['DRIVING', 'WALKING', 'BIKE'].map((mode) => (
                  <button
                    key={mode}
                    onClick={() => setTravelMode(mode)}
                    style={{
                      ...modeButton,
                      backgroundColor: travelMode === mode ? "rgba(0, 230, 118, 0.12)" : "rgba(255,255,255,0.03)",
                      border: travelMode === mode ? "1px solid #00e676" : "1px solid rgba(255,255,255,0.05)"
                    }}
                  >
                    {mode === 'DRIVING' && <Car size={18} color={travelMode === mode ? "#00e676" : "#64748b"} />}
                    {mode === 'WALKING' && <Footprints size={18} color={travelMode === mode ? "#00e676" : "#64748b"} />}
                    {mode === 'BIKE' && <Bike size={18} color={travelMode === mode ? "#00e676" : "#64748b"} />}
                  </button>
                ))}
              </div>

              <motion.button
                whileHover={{ scale: 1.02 }}
                whileTap={{ scale: 0.98 }}
                onClick={() => setShowMap(true)}
                style={primaryButton}
              >
                Find Safest Route
              </motion.button>
            </div>

            
          </div>
        </motion.div>
      </div>
    </div>
  );
}

// --- UPDATED STYLES FOR SCREEN SAFETY ---

const pageContainer = { height: "100vh", width: "100vw", background: "#0b0f1a", position: "relative", overflow: "hidden" };
const mapLayer = { position: "absolute", inset: 0, zIndex: 1 };
const mapOverlay = { position: "absolute", inset: 0, transition: "all 0.5s ease", pointerEvents: "none", zIndex: 2 };

const sidebarStyle = {
  background: "rgba(17, 25, 40, 0.85)",
  backdropFilter: "blur(24px)",
  borderRadius: "24px",
  border: "1px solid rgba(255,255,255,0.1)",
  color: "white",
  display: "flex", flexDirection: "column",
  padding: "24px", // Decreased padding to save height
  boxShadow: "0 40px 100px -20px rgba(0,0,0,0.8)",
  boxSizing: "border-box",
  overflow: "hidden"
};

const scrollArea = {
  overflowY: "auto",
  marginTop: "20px",
  paddingRight: "4px",
  display: "flex",
  flexDirection: "column",
  gap: "20px",
  scrollbarWidth: "none", // For Firefox
};

const header = { display: 'flex', alignItems: 'center', gap: '14px', flexShrink: 0 };
const logoWrapper = { background: 'rgba(0, 230, 118, 0.15)', padding: '8px', borderRadius: '10px' };
const title = { fontSize: '20px', fontWeight: '700', margin: 0 };
const subtitle = { fontSize: '12px', color: '#94a3b8', margin: '2px 0 0 0' };

const contentStack = { display: 'flex', flexDirection: 'column', gap: '10px' };
const field = { position: 'relative' };
const fieldIcon = { position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)', color: '#64748b' };
const inputStyle = { width: '100%', padding: '12px 14px 12px 42px', background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '10px', color: 'white', fontSize: '13px', outline: 'none', boxSizing: 'border-box' };

const modeToggleRow = { display: 'flex', gap: '8px' };
const modeButton = { flex: 1, height: '42px', borderRadius: '10px', cursor: 'pointer', display: 'flex', justifyContent: 'center', alignItems: 'center', transition: '0.2s' };
const primaryButton = { padding: '14px', borderRadius: '50px', border: 'none', background: '#00e676', color: '#000', fontWeight: '800', fontSize: '14px', cursor: 'pointer', marginTop: '5px' };

const summarySection = { padding: '16px', background: 'rgba(0,0,0,0.2)', borderRadius: '16px', border: '1px solid rgba(255,255,255,0.05)' };
const summaryLabel = { fontSize: '14px', fontWeight: '600', marginBottom: '16px' };
const summaryFlex = { display: 'flex', alignItems: 'center', gap: '15px' };
const riskCircleContainer = { position: 'relative', width: '50px', height: '50px' };
const centerIcon = { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' };
const scoreText = { fontSize: '13px', fontWeight: '600' };
const statusBadge = { background: '#064e3b', color: '#00e676', padding: '2px 6px', borderRadius: '4px', fontSize: '9px', fontWeight: 'bold' };
const subScoreText = { fontSize: '11px', color: '#64748b' };
const breakdownList = { margin: '15px 0 0 0', padding: 0, listStyle: 'none' };
const listItem = { display: 'flex', alignItems: 'center', gap: '10px', fontSize: '11px', color: '#94a3b8', marginBottom: '6px' };
const dot = { width: '4px', height: '4px', background: '#00e676', borderRadius: '50%' };

export default Home;