export interface MusicDownloadState {
  activeTrackId: string | null;
  trackProgress: number | null;
  bulkDownloaded: number;
  bulkTotal: number;
}

export const emptyMusicDownloadState = (): MusicDownloadState => ({
  activeTrackId: null,
  trackProgress: null,
  bulkDownloaded: 0,
  bulkTotal: 0,
});

export function isTrackDownloading(state: MusicDownloadState): boolean {
  return state.activeTrackId != null && state.trackProgress != null;
}

export function isBulkDownloading(state: MusicDownloadState): boolean {
  return state.bulkTotal > 0 && state.bulkDownloaded < state.bulkTotal;
}

export function isMusicDownloadActive(state: MusicDownloadState): boolean {
  return isTrackDownloading(state) || isBulkDownloading(state);
}

export function bulkProgressFraction(state: MusicDownloadState): number | null {
  if (state.bulkTotal <= 0) return null;
  if (state.activeTrackId != null && state.trackProgress != null) {
    return Math.min((state.bulkDownloaded + state.trackProgress) / state.bulkTotal, 1);
  }
  return Math.min(state.bulkDownloaded / state.bulkTotal, 1);
}

export function progressForTrack(state: MusicDownloadState, trackId: string): number | null {
  if (state.activeTrackId === trackId && state.trackProgress != null) {
    return state.trackProgress;
  }
  return null;
}

export function beginTrackDownload(state: MusicDownloadState, trackId: string): MusicDownloadState {
  return { ...state, activeTrackId: trackId, trackProgress: 0 };
}

export function updateTrackDownload(
  state: MusicDownloadState,
  trackId: string,
  progress: number,
): MusicDownloadState {
  return {
    ...state,
    activeTrackId: trackId,
    trackProgress: Math.min(Math.max(progress, 0), 1),
  };
}

export function finishTrackDownload(state: MusicDownloadState): MusicDownloadState {
  return { ...state, activeTrackId: null, trackProgress: null };
}

export function beginBulkDownload(
  state: MusicDownloadState,
  downloaded: number,
  total: number,
): MusicDownloadState {
  return {
    activeTrackId: null,
    trackProgress: null,
    bulkDownloaded: downloaded,
    bulkTotal: total,
  };
}

export function updateBulkDownload(
  state: MusicDownloadState,
  downloaded: number,
  total: number,
  currentTrackId: string,
  currentTrackProgress: number,
): MusicDownloadState {
  return {
    bulkDownloaded: downloaded,
    bulkTotal: total,
    activeTrackId: currentTrackId,
    trackProgress: Math.min(Math.max(currentTrackProgress, 0), 1),
  };
}

export function incrementBulkDownloaded(
  state: MusicDownloadState,
  downloaded: number,
  total: number,
): MusicDownloadState {
  return {
    bulkDownloaded: downloaded,
    bulkTotal: total,
    activeTrackId: null,
    trackProgress: null,
  };
}
