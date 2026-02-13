export async function fetchSafeRoute(payload) {
  const response = await fetch("http://localhost:8080/api/route", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error("Failed to fetch route");
  }

  return await response.json();
}