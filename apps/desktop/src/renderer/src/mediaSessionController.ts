export interface MediaSessionTrackInfo {
  title: string;
  artist?: string;
  album: string;
  artworkUrl?: string;
}

export interface MediaSessionHandlers {
  play: () => void;
  pause: () => void;
  nextTrack: () => void;
  previousTrack: () => void;
  seekTo: (timeSeconds: number) => void;
  seekBackward: (offsetSeconds: number) => void;
  seekForward: (offsetSeconds: number) => void;
  getTiming: () => { duration: number; position: number; playbackRate: number } | null;
}

export function isMediaSessionSupported(): boolean {
  return typeof navigator !== "undefined" && "mediaSession" in navigator;
}

export function setupMediaSession(track: MediaSessionTrackInfo, handlers: MediaSessionHandlers): void {
  if (!isMediaSessionSupported()) return;

  const artwork: MediaImage[] = [];
  if (track.artworkUrl) {
    artwork.push({ src: track.artworkUrl, sizes: "512x512", type: "image/png" });
  }

  navigator.mediaSession.metadata = new MediaMetadata({
    title: track.title,
    artist: track.artist ?? "TPlayer",
    album: track.album,
    artwork,
  });

  navigator.mediaSession.setActionHandler("play", handlers.play);
  navigator.mediaSession.setActionHandler("pause", handlers.pause);
  navigator.mediaSession.setActionHandler("previoustrack", handlers.previousTrack);
  navigator.mediaSession.setActionHandler("nexttrack", handlers.nextTrack);
  navigator.mediaSession.setActionHandler("seekto", (details) => {
    if (details.seekTime != null) handlers.seekTo(details.seekTime);
  });
  navigator.mediaSession.setActionHandler("seekbackward", (details) => {
    handlers.seekBackward(details.seekOffset ?? 15);
  });
  navigator.mediaSession.setActionHandler("seekforward", (details) => {
    handlers.seekForward(details.seekOffset ?? 30);
  });
}

export function setMediaPlaybackState(state: MediaSessionPlaybackState): void {
  if (!isMediaSessionSupported()) return;
  navigator.mediaSession.playbackState = state;
}

export function updateMediaPositionState(
  timing: { duration: number; position: number; playbackRate: number } | null,
): void {
  if (!isMediaSessionSupported()) return;
  if (typeof navigator.mediaSession.setPositionState !== "function") return;
  if (!timing || timing.duration <= 0 || !Number.isFinite(timing.duration)) return;

  navigator.mediaSession.setPositionState({
    duration: timing.duration,
    playbackRate: timing.playbackRate,
    position: Math.min(Math.max(0, timing.position), timing.duration),
  });
}

export function clearMediaSession(): void {
  if (!isMediaSessionSupported()) return;

  navigator.mediaSession.metadata = null;
  navigator.mediaSession.playbackState = "none";

  for (const action of [
    "play",
    "pause",
    "previoustrack",
    "nexttrack",
    "seekto",
    "seekbackward",
    "seekforward",
  ] as const) {
    navigator.mediaSession.setActionHandler(action, null);
  }
}
