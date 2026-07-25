import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getTonezenApi } from "@/shared/api";
import { queryKeys } from "@/shared/api/queryKeys";

function invalidateLibrary(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: queryKeys.libraryBundleAll });
  void queryClient.invalidateQueries({ queryKey: queryKeys.progress });
  void queryClient.invalidateQueries({ queryKey: queryKeys.storageStats });
  void queryClient.invalidateQueries({ queryKey: queryKeys.syncStatus });
}

export function useSaveProgressMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: { bookId: string; trackId: string; positionMs: number }) => {
      await getTonezenApi().progress.save(input.bookId, input.trackId, input.positionMs);
    },
    onSettled: () => invalidateLibrary(queryClient),
  });
}

export function useDeleteDownloadMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: { bookId: string; trackId: string }) => {
      await getTonezenApi().download.delete(input.bookId, input.trackId);
    },
    onSettled: (_data, _err, vars) => {
      invalidateLibrary(queryClient);
      void queryClient.invalidateQueries({ queryKey: queryKeys.tracks(vars.bookId) });
    },
  });
}

export function useDeleteAllDownloadsMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await getTonezenApi().download.deleteAll();
    },
    onSettled: () => invalidateLibrary(queryClient),
  });
}

export function useTriggerSyncMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await getTonezenApi().sync.trigger();
    },
    onSettled: () => invalidateLibrary(queryClient),
  });
}
