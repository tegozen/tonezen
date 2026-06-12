import type { AppConfig } from "./app.js";

export function loadConfig(): AppConfig & { port: number; databaseUrl: string } {
  const port = Number(process.env.PORT ?? "3001");
  const databaseUrl = process.env.DATABASE_URL;
  const jwtSecret = process.env.JWT_SECRET;
  const serviceRoleKey = process.env.SERVICE_ROLE_KEY;
  const storageUrl = process.env.STORAGE_INTERNAL_URL ?? "http://storage:5000";
  const storageBucket = process.env.STORAGE_BUCKET ?? "content";
  const storageSignExpiresIn = Number(process.env.STORAGE_SIGN_EXPIRES_IN ?? "900");
  const publicBaseUrl =
    process.env.TONEZEN_BASE_URL ?? process.env.STORAGE_PUBLIC_URL ?? "http://localhost:8000";

  if (!databaseUrl || !jwtSecret || !serviceRoleKey) {
    console.error("DATABASE_URL, JWT_SECRET, SERVICE_ROLE_KEY required");
    process.exit(1);
  }

  return {
    port,
    databaseUrl,
    jwtSecret,
    storage: {
      storageUrl,
      publicBaseUrl,
      bucket: storageBucket,
      serviceRoleKey,
      expiresIn: storageSignExpiresIn,
    },
  };
}
