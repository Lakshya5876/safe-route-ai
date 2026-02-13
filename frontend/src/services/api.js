export async function fetchSafeRoute(payload) {
  let response;
  try {
    response = await fetch("http://localhost:8080/api/route", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
  } catch (err) {
    throw new Error(
      "Cannot reach the server. Is the backend running on http://localhost:8080?"
    );
  }

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const msg = data?.error || data?.message || "Failed to fetch route";
    throw new Error(msg);
  }

  return data;
}