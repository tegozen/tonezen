import { useCallback } from "react";
import {
  isBulkDownloading,
  isTrackQueued,
  progressForTrack as downloadProgressForTrack,
} from "@core/downloads/downloadQueueState";
import type { MusicListTrack } from "@core/catalog/musicList";
import type { MusicDownloadActionsDeps } from "./musicPlaybackDeps";

export function useMusicDownloadActions({
  sessionState,
  musicTracks,
  downloadQueue,
  refreshLibrary,
  stopPlayback,
  currentTrack,
  setMusicError,
  setMusicMode,
  setMusicPlaybackBook,
  setMusicQueue,
  musicQueueRef,
  deletingTrackIdRef,
  prefetchJobRef,
}: MusicDownloadActionsDeps) {
  const downloadAllMusic = useCallback(async () => {
    if (sessionState !== "AuthenticatedOnline") return;
    if (isBulkDownloading(downloadQueue.state)) {
      if (downloadQueue.state.activeBatchId) {
        await downloadQueue.cancelBatch(downloadQueue.state.activeBatchId);
      }
      return;
    }
    const pending = musicTracks.filter((track) => !track.isDownloaded);
    if (pending.length === 0) return;
    const batchId = crypto.randomUUID();
    await downloadQueue.enqueueBatch(
      pending.map((track) => ({
        bookId: track.bookId,
        trackId: track.trackId,
        priority: "USER" as const,
        batchId,
        title: track.trackTitle,
        subtitle: track.artist,
        contentType: "music",
      })),
      batchId,
    );
  }, [downloadQueue, musicTracks, sessionState]);

  const downloadMusicTrack = useCallback(
    async (listTrack: MusicListTrack) => {
      if (sessionState !== "AuthenticatedOnline" || listTrack.isDownloaded) return;
      setMusicError(null);
      const queued =
        isTrackQueued(downloadQueue.state, listTrack.trackId) ||
        downloadProgressForTrack(downloadQueue.state, listTrack.trackId) != null;
      if (queued) {
        await downloadQueue.cancelTrack(listTrack.bookId, listTrack.trackId);
        return;
      }
      await downloadQueue.enqueue({
        bookId: listTrack.bookId,
        trackId: listTrack.trackId,
        priority: "USER",
        title: listTrack.trackTitle,
        subtitle: listTrack.artist,
        contentType: "music",
      });
    },
    [downloadQueue, sessionState],
  );

  const deleteMusicTrack = useCallback(
    async (listTrack: MusicListTrack) => {
      const trackId = listTrack.trackId;
      deletingTrackIdRef.current = trackId;
      prefetchJobRef.current += 1;

      try {
        await downloadQueue.cancelTrack(listTrack.bookId, listTrack.trackId);
        if (currentTrack?.id === trackId) {
          stopPlayback();
          setMusicMode(false);
          setMusicPlaybackBook(null);
          musicQueueRef.current = [];
          setMusicQueue([]);
          await new Promise((resolve) => setTimeout(resolve, 50));
        }

        await window.tonezen.download.delete(listTrack.bookId, trackId);
        await refreshLibrary();

        setMusicQueue((queue) =>
          queue.map((item) => (item.trackId === trackId ? { ...item, isDownloaded: false } : item)),
        );
        musicQueueRef.current = musicQueueRef.current.map((item) =>
          item.trackId === trackId ? { ...item, isDownloaded: false } : item,
        );
      } finally {
        deletingTrackIdRef.current = null;
      }
    },
    [currentTrack?.id, downloadQueue, refreshLibrary, stopPlayback],
  );

  return {
    downloadAllMusic,
    downloadMusicTrack,
    deleteMusicTrack,
  };
}
