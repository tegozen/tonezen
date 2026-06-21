import { loadConfig } from "./config.js";
import {
  createPool,
  CatalogRepository,
  loadIndexedTracks,
  readWatermark,
  writeWatermark,
  maxUpdatedAt,
} from "./db/index.js";
import { createHealthServer, type IndexerHealthState } from "./healthServer.js";
import { createFileProber, warmUpProber } from "./fileProber.js";
import {
  expandChangedAudiobookBookObjects,
  isIndexableAudioPath,
  scanStorageObjects,
} from "./scanner.js";
import { listChangedObjects, listContentObjects } from "./storage/listObjects.js";

const config = loadConfig();
const pool = createPool(config.databaseUrl);
const storageConfig = {
  storageUrl: config.storageUrl,
  bucket: config.storageBucket,
  serviceRoleKey: config.serviceRoleKey,
};
const repo = new CatalogRepository(pool, storageConfig);

const healthState: IndexerHealthState = {
  lastSuccessAt: null,
  lastFailureAt: null,
};

createHealthServer(config.healthPort, config.intervalSeconds * 2000, () => healthState);

async function runOnce(): Promise<void> {
  const watermark = await readWatermark(pool);
  const changed = await listChangedObjects(pool, watermark);

  if (changed.length === 0) {
    await repo.reconcileDeletions();
    console.log("[indexer] Done: 0 changed, 0 probed, 0 skipped");
    healthState.lastSuccessAt = Date.now();
    return;
  }

  console.log(`[indexer] ${changed.length} changed object(s) since watermark`);
  const scopedObjects = expandChangedAudiobookBookObjects(
    changed,
    await listContentObjects(pool),
  );
  const indexed = await loadIndexedTracks(pool);
  const prober = createFileProber({
    objects: scopedObjects,
    indexed,
    storage: storageConfig,
    concurrency: config.probeConcurrency,
  });

  const indexablePaths = changed.filter((object) => isIndexableAudioPath(object.name)).map((o) => o.name);
  await warmUpProber(prober, indexablePaths, config.probeConcurrency);

  const { cycles, musicAlbums } = await scanStorageObjects(scopedObjects, {
    probeTags: (path) => prober.probe(path).then((result) => result.tags),
  });

  const objectUpdatedAtByPath = new Map(
    scopedObjects.map((object) => [object.name, object.updatedAt]),
  );
  await repo.upsertPartialCatalog(scopedObjects, cycles, musicAlbums, {
    getMetadata: (path) => prober.probe(path).then((result) => result.metadata ?? null),
    objectUpdatedAtByPath,
  });

  await repo.reconcileDeletions();
  await writeWatermark(pool, maxUpdatedAt(changed));

  console.log(
    `[indexer] Done: ${changed.length} changed, ${prober.stats.probed} probed, ${prober.stats.skipped} skipped`,
  );
  healthState.lastSuccessAt = Date.now();
}

async function main(): Promise<void> {
  await runOnce();
  setInterval(() => {
    runOnce().catch((err) => {
      healthState.lastFailureAt = Date.now();
      console.error("[indexer] Error:", err);
    });
  }, config.intervalSeconds * 1000);
}

main().catch((err) => {
  healthState.lastFailureAt = Date.now();
  console.error(err);
  process.exit(1);
});
