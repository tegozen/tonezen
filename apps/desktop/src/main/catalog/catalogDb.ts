import {
  deleteBooksNotIn,
  getBooks,
  hydrateCycleBooks,
  upsertBooks,
} from "./catalogBooksDb.js";
import {
  createCatalogCyclesDb,
  deleteCyclesNotIn,
  type LibrarySnapshotOptions,
} from "./catalogCyclesDb.js";
import { createCatalogLocalPaths } from "./catalogLocalPaths.js";
import {
  deleteTracksForBooksNotIn,
  deleteTracksNotIn,
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
  deleteBooksNotIn,
  upsertTracks,
  deleteTracksForBooksNotIn,
  deleteTracksNotIn,
  setTrackLocalPath: localPaths.setTrackLocalPath,
  getTrackById,
  markTrackDownloaded: localPaths.markTrackDownloaded,
  resolveLocalTrackPath: localPaths.resolveLocalTrackPath,
  resolveLocalTrackPathForBook: localPaths.resolveLocalTrackPathForBook,
  upsertCycles: cycles.upsertCycles,
  deleteCyclesNotIn,
  getCycles: cycles.getCycles,
  getLibrarySnapshot: cycles.getLibrarySnapshot,
  reconcileLocalDownloadPaths: localPaths.reconcileLocalDownloadPaths,
  buildCycles: cycles.buildCycles,
  getAllTracks,
  getBooks,
  getTracks,
  clearAllLocalPaths: localPaths.clearAllLocalPaths,
};
