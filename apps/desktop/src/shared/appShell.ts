/** Mobile shell width used by renderer `.app-frame` and the Electron window. */
export const APP_SHELL_WIDTH_PX = 430;

/** Default window height (content area); user can still resize vertically. */
export const APP_SHELL_DEFAULT_HEIGHT_PX = 800;

export function mainWindowContentSize(): { width: number; height: number } {
  return {
    width: APP_SHELL_WIDTH_PX,
    height: APP_SHELL_DEFAULT_HEIGHT_PX,
  };
}
