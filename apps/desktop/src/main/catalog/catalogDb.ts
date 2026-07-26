import {
  getBooks,
  hydrateCycleBooks,
  upsertBooks,
} from "./catalogBooksDb.js";
import { createCatalogCyclesDb, type LibrarySnapshotOptions } from "./catalogCyclesDb.js";
import { createCatalogLocalPaths } from "./catalogLocalPaths.js";
import {
  getAllTracks,
  getTrackById,
  getTracks,
  upsertTracks,
} from "./catalogTracksDb.js";

export type { LibrarySnapshotOptions };

const localPaths = createCatalogLocalPaths({
  getTracks,
  getTrackById,
});

const cycles = createCatalogCyclesDb({
  hydrateCycleBooks,
  getBooks,
  getAllTracks,
  reconcileLocalDownloadPaths: localPaths.reconcileLocalDownloadPaths,
});

export const CatalogDb = {
  hydrateCycleBooks,
  upsertBooks,
  upsertTracks,
  setTrackLocalPath: localPaths.setTrackLocalPath,
  getTrackById,
  markTrackDownloaded: localPaths.markTrackDownloaded,
  resolveLocalTrackPath: localPaths.resolveLocalTrackPath,
  resolveLocalTrackPathForBook: localPaths.resolveLocalTrackPathForBook,
  upsertCycles: cycles.upsertCycles,
  getCycles: cycles.getCycles,
  getLibrarySnapshot: cycles.getLibrarySnapshot,
  reconcileLocalDownloadPaths: localPaths.reconcileLocalDownloadPaths,
  buildCycles: cycles.buildCycles,
  getAllTracks,
  getBooks,
  getTracks,
  clearAllLocalPaths: localPaths.clearAllLocalPaths,
};
