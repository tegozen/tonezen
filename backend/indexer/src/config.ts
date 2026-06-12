export interface IndexerConfig {
  contentRoot: string;
  databaseUrl: string;
  intervalSeconds: number;
}

export function loadConfig(): IndexerConfig {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    console.error("DATABASE_URL is required");
    process.exit(1);
  }

  return {
    contentRoot: process.env.CONTENT_ROOT ?? "/content",
    databaseUrl,
    intervalSeconds: Number(process.env.INDEXER_INTERVAL_SECONDS ?? "60"),
  };
}
