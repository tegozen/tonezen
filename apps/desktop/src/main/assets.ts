import appIconIcoPath from "../../resources/app-icon.ico?asset";
import appIconPngPath from "../../resources/app-icon.png?asset";
import splashImagePath from "../../resources/splash.png?asset";
import trayIconPath from "../../resources/tray-icon.png?asset";

const appIconPath = process.platform === "win32" ? appIconIcoPath : appIconPngPath;

export { appIconPath, appIconPngPath, splashImagePath, trayIconPath };
