import { useCallback, useReducer } from "react";
import {
  beginBulkDownload,
  beginTrackDownload,
  emptyMusicDownloadState,
  finishTrackDownload,
  incrementBulkDownloaded,
  type MusicDownloadState,
  updateBulkDownload,
  updateTrackDownload,
} from "@core/downloads/musicDownloadState";

type MusicDownloadAction =
  | { type: "beginTrack"; trackId: string }
  | { type: "updateTrack"; trackId: string; progress: number }
  | { type: "finishTrack" }
  | { type: "beginBulk"; downloaded: number; total: number }
  | { type: "updateBulk"; downloaded: number; total: number; trackId: string; progress: number }
  | { type: "incrementBulk"; downloaded: number; total: number }
  | { type: "clear" };

function musicDownloadReducer(
  state: MusicDownloadState,
  action: MusicDownloadAction,
): MusicDownloadState {
  switch (action.type) {
    case "beginTrack":
      return beginTrackDownload(state, action.trackId);
    case "updateTrack":
      return updateTrackDownload(state, action.trackId, action.progress);
    case "finishTrack":
      return finishTrackDownload(state);
    case "beginBulk":
      return beginBulkDownload(state, action.downloaded, action.total);
    case "updateBulk":
      return updateBulkDownload(
        state,
        action.downloaded,
        action.total,
        action.trackId,
        action.progress,
      );
    case "incrementBulk":
      return incrementBulkDownloaded(state, action.downloaded, action.total);
    case "clear":
      return emptyMusicDownloadState();
    default:
      return state;
  }
}

export function useMusicDownload() {
  const [state, dispatch] = useReducer(musicDownloadReducer, undefined, emptyMusicDownloadState);

  return {
    state,
    beginTrack: useCallback((trackId: string) => dispatch({ type: "beginTrack", trackId }), []),
    updateTrack: useCallback(
      (trackId: string, progress: number) => dispatch({ type: "updateTrack", trackId, progress }),
      [],
    ),
    finishTrack: useCallback(() => dispatch({ type: "finishTrack" }), []),
    beginBulk: useCallback(
      (downloaded: number, total: number) => dispatch({ type: "beginBulk", downloaded, total }),
      [],
    ),
    updateBulk: useCallback(
      (downloaded: number, total: number, trackId: string, progress: number) =>
        dispatch({ type: "updateBulk", downloaded, total, trackId, progress }),
      [],
    ),
    incrementBulk: useCallback(
      (downloaded: number, total: number) => dispatch({ type: "incrementBulk", downloaded, total }),
      [],
    ),
    clear: useCallback(() => dispatch({ type: "clear" }), []),
  };
}
