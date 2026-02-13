export async function geocodeLocation(place) {
  const token = import.meta.env.VITE_MAPBOX_TOKEN;

  const res = await fetch(
    `https://api.mapbox.com/geocoding/v5/mapbox.places/${encodeURIComponent(
      place
    )}.json?access_token=${token}`
  );

  const data = await res.json();

  if (!data.features || data.features.length === 0) {
    throw new Error("Location not found");
  }

  const [lng, lat] = data.features[0].center;

  return { lat, lng };
}
