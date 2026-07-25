export const LOCAL_AUDIO_SCHEME = "tonezen-audio";

export function toAudioFileUrl(localPath: string): string {
  const normalized = localPath.replace(/\\/g, "/");
  return `${LOCAL_AUDIO_SCHEME}://local/${encodeURIComponent(normalized)}`;
}

export function localAudioPathFromUrl(url: string): string | null {
  const prefix = `${LOCAL_AUDIO_SCHEME}://local/`;
  if (!url.startsWith(prefix)) return null;
  return decodeURIComponent(url.slice(prefix.length));
}
