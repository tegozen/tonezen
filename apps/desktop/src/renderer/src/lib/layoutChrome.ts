/** Scroll padding aligned with Android `TonezenLayout.kt` (1dp ≈ 1px). */
export const SCROLL_GAP_PX = 16;

export const LIBRARY_TOP_SCROLL_MUSIC_PX = 72;
export const LIBRARY_TOP_SCROLL_AUDIOBOOKS_PX = 144;
export const LIBRARY_TOP_SCROLL_OFFLINE_EXTRA_PX = 44;
export const PAGE_TITLE_TOP_SCROLL_PX = 81;
export const OVERLAY_BACK_TOP_SCROLL_PX = 81;

export function libraryScrollPaddingTop(isAudiobooks: boolean, offlineBanner: boolean): number {
  let top = isAudiobooks ? LIBRARY_TOP_SCROLL_AUDIOBOOKS_PX : LIBRARY_TOP_SCROLL_MUSIC_PX;
  if (isAudiobooks && offlineBanner) top += LIBRARY_TOP_SCROLL_OFFLINE_EXTRA_PX;
  return top;
}

export function scrollPadBottomCss(showMiniPlayer: boolean, showBottomNav: boolean): string {
  const gap = "1rem";
  if (showMiniPlayer && showBottomNav) return `calc(10.5rem + ${gap})`;
  if (showBottomNav) return `calc(5.5rem + ${gap})`;
  if (showMiniPlayer) return `calc(6rem + ${gap})`;
  return gap;
}
