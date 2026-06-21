import { isValidWaveformPeaks } from "./mediaProbe.js";
import type { IndexedTrackRow } from "./db/indexedTracks.js";
import type { StorageObjectRow } from "./storage/listObjects.js";

export function shouldProbe(object: StorageObjectRow, dbRow: IndexedTrackRow | undefined): boolean {
  if (!dbRow) {
    return true;
  }

  if (object.sizeBytes != null && dbRow.sizeBytes != null && object.sizeBytes !== dbRow.sizeBytes) {
    return true;
  }

  if (dbRow.storageObjectUpdatedAt == null) {
    return true;
  }

  if (object.updatedAt != null && object.updatedAt > dbRow.storageObjectUpdatedAt) {
    return true;
  }

  if (object.catalogUpdatedAt != null && object.catalogUpdatedAt > dbRow.storageObjectUpdatedAt) {
    return true;
  }

  if (!isValidWaveformPeaks(dbRow.waveformPeaks)) {
    return true;
  }

  if (!dbRow.checksum || dbRow.sizeBytes == null) {
    return true;
  }

  return false;
}
