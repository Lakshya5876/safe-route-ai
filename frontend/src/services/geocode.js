// City centers must match pipeline/src/cities.js — biases geocoding results
// toward the selected city so e.g. "MG Road" resolves in Bengaluru, not Delhi.
const COUNTRY_BIAS = "in";
export const CITY_CENTERS = {
  delhi: "77.209,28.6139",
  mumbai: "72.8777,19.076",
  bengaluru: "77.5946,12.9716",
  hyderabad: "78.4867,17.385",
  pune: "73.8567,18.5204",
  chennai: "80.2707,13.0827",
};

export async function geocodeLocation(place, cityId) {
  const token = import.meta.env.VITE_MAPBOX_TOKEN;

  if (!token) {
    throw new Error("Mapbox token not configured. Add VITE_MAPBOX_TOKEN to .env");
  }

  const trimmed = (place || "").trim();
  if (!trimmed) {
    throw new Error("Please enter a location");
  }

  const proximity = CITY_CENTERS[cityId] ?? CITY_CENTERS.delhi;
  const params = new URLSearchParams({
    access_token: token,
    proximity,
    country: COUNTRY_BIAS,
    types: "address,place,locality,neighborhood,poi",
  });

  const url = `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(
    trimmed
  )}.json?${params}`;

  const res = await fetch(url);
  const data = await res.json();

  if (!res.ok) {
    throw new Error(data.message || "Geocoding request failed");
  }

  if (!data.features || data.features.length === 0) {
    throw new Error("Location not found. Try a more specific address or landmark.");
  }

  const [lng, lat] = data.features[0].center;
  return { lat, lng };
}
