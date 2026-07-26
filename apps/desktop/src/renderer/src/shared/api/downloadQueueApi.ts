import type { DownloadPriority } from "@core/downloads/downloadQueuePolicy";
import type {
  DownloadAwaitResult,
  DownloadQueueState,
  EnqueueDownloadRequest,
} from "@core/downloads/downloadQueueState";

/** Facade for download-queue actions used across features (matches useDownloadQueue). */
export interface DownloadQueueApi {
  state: DownloadQueueState;
  enqueue: (request: EnqueueDownloadRequest) => Promise<void>;
  enqueueBatch: (requests: EnqueueDownloadRequest[], batchId?: string) => Promise<void>;
  awaitTrack: (
    bookId: string,
    trackId: string,
    options?: {
      priority?: DownloadPriority;
      title?: string;
      subtitle?: string | null;
      contentType?: string;
    },
  ) => Promise<DownloadAwaitResult>;
  cancelTrack: (bookId: string, trackId: string) => Promise<void>;
  cancelBatch: (batchId: string) => Promise<void>;
  cancelAll: () => Promise<void>;
}
