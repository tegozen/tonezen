export interface IndexerConfig {
  databaseUrl: string;
  storageUrl: string;
  storageBucket: string;
  serviceRoleKey: string;
  intervalSeconds: number;
  healthPort: number;
}

export function loadConfig(): IndexerConfig {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error("DATABASE_URL is required");
    process.exit(1);
  }

  const serviceRoleKey = process.env.SERVICE_ROLE_KEY;
  if (!serviceRoleKey) {
    console.error("SERVICE_ROLE_KEY is required");
    process.exit(1);
  }

  return {
    databaseUrl,
    storageUrl: process.env.STORAGE_URL ?? "http://storage:5000",
    storageBucket: process.env.STORAGE_BUCKET ?? "content",
    serviceRoleKey,
    intervalSeconds: Number(process.env.INDEXER_INTERVAL_SECONDS ?? "60"),
    healthPort: Number(process.env.HEALTH_PORT ?? "3020"),
  };
}
