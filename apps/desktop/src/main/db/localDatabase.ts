import { CatalogDb } from "./catalogDb.js";
import { DownloadQueueDb } from "./downloadQueueDb.js";
import { initDatabase } from "./connection.js";
import { ProgressDb } from "./progressDb.js";

/** Facade over catalog and progress SQLite modules — keeps existing main-process call sites stable. */
export const LocalDatabase = {
  init: initDatabase,
  ...CatalogDb,
  ...ProgressDb,
  ...DownloadQueueDb,
};
