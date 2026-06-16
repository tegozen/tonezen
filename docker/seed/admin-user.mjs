/**
 * Ensure default GoTrue admin exists (idempotent).
 */

function serviceHeaders(serviceKey) {
  return {
    Authorization: `Bearer ${serviceKey}`,
    apikey: serviceKey,
    "Content-Type": "application/json",
  };
}

export async function waitForAuthReady({
  authUrl,
  fetchFn = fetch,
  maxAttempts = 60,
  delayMs = 2000,
}) {
  const base = authUrl.replace(/\/$/, "");

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const res = await fetchFn(`${base}/health`);
      if (res.ok) {
        return;
      }
    } catch {
      // Auth still starting.
    }

    if (attempt < maxAttempts) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }

  throw new Error(`Auth not ready after ${maxAttempts} attempts: ${authUrl}`);
}

export async function ensureAdminUser({
  authUrl,
  serviceKey,
  email,
  password,
  displayName,
  fetchFn = fetch,
}) {
  if (!email) {
    throw new Error("ADMIN_EMAIL is required");
  }
  if (!password) {
    throw new Error("ADMIN_PASSWORD is required");
  }

  const base = authUrl.replace(/\/$/, "");
  const listRes = await fetchFn(`${base}/admin/users?page=1&per_page=200`, {
    headers: serviceHeaders(serviceKey),
  });
  if (!listRes.ok) {
    const text = await listRes.text();
    throw new Error(`List users failed (${listRes.status}): ${text}`);
  }

  const data = await listRes.json();
  const users = data.users ?? [];
  if (users.some((user) => user.email === email)) {
    return { created: false, email };
  }

  const createRes = await fetchFn(`${base}/admin/users`, {
    method: "POST",
    headers: serviceHeaders(serviceKey),
    body: JSON.stringify({
      email,
      password,
      email_confirm: true,
      user_metadata: { role: "admin", full_name: displayName },
    }),
  });
  if (!createRes.ok) {
    const text = await createRes.text();
    throw new Error(`Create admin failed (${createRes.status}): ${text}`);
  }

  return { created: true, email };
}
