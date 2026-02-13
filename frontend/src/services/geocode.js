// Bias to Delhi, India (SafeRoute demo) so "Connaught Place", "Karol Bagh" etc. resolve locally
const DELHI_PROXIMITY = "77.209,28.6139";
const COUNTRY_BIAS = "in";

export async function geocodeLocation(place) {
  const token = import.meta.env.VITE_MAPBOX_TOKEN;

  if (!token) {
    throw new Error("Mapbox token not configured. Add VITE_MAPBOX_TOKEN to .env");
  }

  const trimmed = (place || "").trim();
  if (!trimmed) {
    throw new Error("Please enter a location");
  }

  const params = new URLSearchParams({
    access_token: token,
    proximity: DELHI_PROXIMITY,
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
    throw new Error("Location not found. Try a more specific address or landmark in Delhi.");
  }

  const [lng, lat] = data.features[0].center;
  return { lat, lng };
}
