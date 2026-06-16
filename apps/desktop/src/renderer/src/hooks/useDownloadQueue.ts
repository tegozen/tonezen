import { useCallback, useEffect, useState } from "react";
import {
  emptyDownloadQueueState,
  type DownloadQueueState,
} from "@shared/downloadQueueState";

export function useDownloadQueue() {
  const [state, setState] = useState<DownloadQueueState>(emptyDownloadQueueState);

  useEffect(() => {
    let active = true;
    void window.tonezen.download.getQueueState().then((snapshot) => {
      if (active) setState(snapshot);
    });
    const unsubscribe = window.tonezen.download.onQueueState((snapshot) => {
      setState(snapshot);
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const enqueue = useCallback(
    (request: Parameters<typeof window.tonezen.download.enqueue>[0]) =>
      window.tonezen.download.enqueue(request),
    [],
  );

  const enqueueBatch = useCallback(
    (
      requests: Parameters<typeof window.tonezen.download.enqueueBatch>[0],
      batchId?: string,
    ) => window.tonezen.download.enqueueBatch(requests, batchId),
    [],
  );

  const awaitTrack = useCallback(
    (
      bookId: string,
      trackId: string,
      options?: Parameters<typeof window.tonezen.download.awaitTrack>[2],
    ) => window.tonezen.download.awaitTrack(bookId, trackId, options),
    [],
  );

  const cancelTrack = useCallback(
    (bookId: string, trackId: string) => window.tonezen.download.cancelTrack(bookId, trackId),
    [],
  );

  const cancelBatch = useCallback(
    (batchId: string) => window.tonezen.download.cancelBatch(batchId),
    [],
  );

  const cancelAll = useCallback(() => window.tonezen.download.cancelAll(), []);

  return {
    state,
    enqueue,
    enqueueBatch,
    awaitTrack,
    cancelTrack,
    cancelBatch,
    cancelAll,
  };
}
