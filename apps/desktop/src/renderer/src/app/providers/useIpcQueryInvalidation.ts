import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getTonezenApi } from "@/shared/api";
import { queryKeys } from "@/shared/api/queryKeys";

/** Bridge IPC push events → React Query invalidation. */
export function useIpcQueryInvalidation(enabled: boolean) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled) return;
    const api = getTonezenApi();

    const unsubCatalog = api.catalog.onUpdated(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.libraryBundleAll });
      void queryClient.invalidateQueries({ queryKey: queryKeys.progress });
    });

    let timer: ReturnType<typeof setTimeout> | undefined;
    const unsubQueue = api.download.onQueueState((state) => {
      if (!state.activeTrackId && state.queuedItems.length === 0) {
        clearTimeout(timer);
        timer = setTimeout(() => {
          void queryClient.invalidateQueries({ queryKey: queryKeys.libraryBundleAll });
          void queryClient.invalidateQueries({ queryKey: queryKeys.storageStats });
        }, 300);
      }
    });

    const unsubProgress = api.progress.onUpdated(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.libraryBundleAll });
      void queryClient.invalidateQueries({ queryKey: queryKeys.progress });
    });

    return () => {
      clearTimeout(timer);
      unsubCatalog();
      unsubQueue();
      unsubProgress();
    };
  }, [enabled, queryClient]);
}
