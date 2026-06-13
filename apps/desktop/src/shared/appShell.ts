/** Mobile shell width used by renderer `.app-frame` and the Electron window. */
export const APP_SHELL_WIDTH_PX = 430;

/** Default window height (content area); user can still resize vertically. */
export const APP_SHELL_DEFAULT_HEIGHT_PX = 800;

/** Minimum content height for the main window. */
export const APP_SHELL_MIN_HEIGHT_PX = 640;

export function mainWindowContentSize(): { width: number; height: number } {
  return {
    width: APP_SHELL_WIDTH_PX,
    height: APP_SHELL_DEFAULT_HEIGHT_PX,
  };
}

export function needsMainWindowWidthEnforcement(contentWidth: number): boolean {
  return contentWidth !== APP_SHELL_WIDTH_PX;
}

export function normalizeMainWindowContentSize(contentHeight: number): { width: number; height: number } {
  return {
    width: APP_SHELL_WIDTH_PX,
    height: Math.max(contentHeight, APP_SHELL_MIN_HEIGHT_PX),
  };
}
