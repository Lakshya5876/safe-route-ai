import { useState } from "react";

export default function LocationInput({ placeholder, value, setValue }) {
  const [suggestions, setSuggestions] = useState([]);

  const fetchPlaces = async (query) => {
    if (!query) return setSuggestions([]);

    const res = await fetch(
      `https://api.mapbox.com/geocoding/v5/mapbox.places/${query}.json?access_token=${import.meta.env.VITE_MAPBOX_TOKEN}&autocomplete=true&limit=5`
    );

    const data = await res.json();
    setSuggestions(data.features);
  };

  const handleChange = (e) => {
    const val = e.target.value;
    setValue(val);
    fetchPlaces(val);
  };

  return (
    <div style={{ position: "relative", width: "100%" }}>
      <input
        value={value}
        onChange={handleChange}
        placeholder={placeholder}
        style={{
          width: "100%",
          padding: "12px 44px 12px 40px",   // 🔥 SAME AS HOME INPUT
          background: "rgba(255,255,255,0.05)",
          border: "1px solid rgba(255,255,255,0.1)",
          borderRadius: "10px",
          color: "white",
          outline: "none",
          boxSizing: "border-box"
        }}
      />

      {suggestions.length > 0 && (
        <div style={{
          position: "absolute",
          top: "44px",
          left: 0,
          right: 0,
          background: "#020617",
          border: "1px solid rgba(255,255,255,0.1)",
          borderRadius: "10px",
          zIndex: 999,
          overflow: "hidden"
        }}>
          {suggestions.map((place) => (
            <div
              key={place.id}
              onClick={() => {
                setValue(place.place_name);
                setSuggestions([]);
              }}
              style={{
                padding: "10px",
                cursor: "pointer",
                borderBottom: "1px solid rgba(255,255,255,0.05)"
              }}
            >
              {place.place_name}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
