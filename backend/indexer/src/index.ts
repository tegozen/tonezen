import { loadConfig } from "./config.js";
import { createPool, CatalogRepository } from "./db/index.js";
import { probeAudioTags } from "./mediaProbe.js";
import { scanStorageObjects } from "./scanner.js";
import { downloadObjectToTemp, removeTempFile } from "./storage/download.js";
import { listContentObjects } from "./storage/listObjects.js";

const config = loadConfig();
const pool = createPool(config.databaseUrl);
const storageConfig = {
  storageUrl: config.storageUrl,
  bucket: config.storageBucket,
  serviceRoleKey: config.serviceRoleKey,
};
const repo = new CatalogRepository(pool, storageConfig);

async function probeTagsFromStorage(storagePath: string) {
  let tempPath: string | null = null;
  try {
    tempPath = await downloadObjectToTemp(storagePath, storageConfig);
    return await probeAudioTags(tempPath);
  } catch {
    return null;
  } finally {
    if (tempPath) {
      await removeTempFile(tempPath);
    }
  }
}

async function runOnce(): Promise<void> {
  console.log("[indexer] Listing storage.objects for bucket content...");
  const objects = await listContentObjects(pool);
  repo.setObjectSizes(objects);
  const { cycles, musicAlbums } = await scanStorageObjects(objects, {
    probeTags: probeTagsFromStorage,
  });
  await repo.upsertCatalog(cycles, musicAlbums);
  console.log(
    `[indexer] Done: ${cycles.length} cycles, ${musicAlbums.length} music albums (${objects.length} objects)`,
  );
}

async function main(): Promise<void> {
  await runOnce();
  setInterval(() => {
    runOnce().catch((err) => console.error("[indexer] Error:", err));
  }, config.intervalSeconds * 1000);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
