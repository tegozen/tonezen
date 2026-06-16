import { CatalogDb } from "./db/catalogDb.js";
import { DownloadQueueDb } from "./db/downloadQueueDb.js";
import { initDatabase } from "./db/connection.js";
import { ProgressDb } from "./db/progressDb.js";

/** Facade over catalog and progress SQLite modules — keeps existing main-process call sites stable. */
export const LocalDatabase = {
  init: initDatabase,
  ...CatalogDb,
  ...ProgressDb,
  ...DownloadQueueDb,
};
