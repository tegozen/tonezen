import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function read(relativePath) {
  const absolutePath = path.join(root, relativePath);
  if (!existsSync(absolutePath)) {
    throw new Error(`Missing required file: ${relativePath}`);
  }
  return readFileSync(absolutePath, "utf8");
}

function assertIncludes(content, expected, label) {
  if (!content.includes(expected)) {
    throw new Error(`${label} must include ${expected}`);
  }
}

function assertNotIncludes(content, unexpected, label) {
  if (content.includes(unexpected)) {
    throw new Error(`${label} must not include ${unexpected}`);
  }
}

function assertFile(relativePath) {
  if (!existsSync(path.join(root, relativePath))) {
    throw new Error(`Missing required file: ${relativePath}`);
  }
}

const compose = read("docker-compose.yml");
assertIncludes(compose, "landing:", "docker-compose.yml");
assertIncludes(compose, "image: nginx:1.27-alpine", "landing service");
assertIncludes(compose, "./docker/landing/public:/usr/share/nginx/html:ro", "landing service");

const kong = read("docker/kong/kong.yml");
assertIncludes(kong, "name: landing", "kong routes");
assertIncludes(kong, "url: http://landing:80/", "kong landing service");
assertIncludes(kong, "name: studio-entry", "kong studio routes");
assertIncludes(kong, "- /studio", "kong studio routes");
assertIncludes(kong, "name: studio-next", "kong studio routes");
assertIncludes(kong, "- /_next", "kong studio asset routes");
assertIncludes(kong, "name: studio-api", "kong studio routes");
assertIncludes(kong, "- /api", "kong studio routes");
assertIncludes(kong, "name: landing-root", "kong landing route");
assertIncludes(kong, "- /", "kong landing route");

const index = read("docker/landing/public/index.html");
assertIncludes(index, 'lang="ru"', "landing page");
assertIncludes(index, "Прогресс аудиокниг синхронизируется", "landing copy");
assertIncludes(index, "Музыка остаётся локальной", "landing copy");
assertIncludes(index, "Ваши данные под защитой", "landing copy");
assertIncludes(index, "/downloads/tonezen-android.apk", "landing downloads");
assertIncludes(index, "/downloads/tonezen-windows.exe", "landing downloads");
assertIncludes(index, "/downloads/tonezen-macos.dmg", "landing downloads");
assertIncludes(index, "/studio", "landing studio link");
assertIncludes(index, "assets/desktop-music.png", "landing screenshots");
assertIncludes(index, "assets/android-music.png", "landing screenshots");
assertIncludes(index, 'href="/favicon.ico"', "landing favicon");
assertIncludes(index, 'href="/favicon.png"', "landing favicon");
assertIncludes(index, 'class="brand-icon"', "landing header");
assertIncludes(index, 'src="/assets/app-icon.png"', "landing header");
assertNotIncludes(index, '<span class="brand-mark"', "landing header");
assertNotIncludes(index, '<nav class="main-nav"', "landing header");
assertIncludes(index, 'class="device-column"', "landing device columns");
assertIncludes(index, 'class="windows-frame"', "landing screenshots");
assertIncludes(index, 'class="windows-controls"', "landing screenshots");

const styles = read("docker/landing/public/styles.css");
assertIncludes(styles, "#5eead4", "landing CSS");
assertIncludes(styles, "@media", "landing CSS");
assertIncludes(styles, "--header-height: 64px;", "one-screen landing CSS");
assertIncludes(styles, "--feature-strip-height: 108px;", "one-screen landing CSS");
assertIncludes(styles, "height: 100vh;", "one-screen landing CSS");
assertIncludes(styles, "overflow: hidden;", "one-screen landing CSS");
assertIncludes(styles, "height: calc(100vh - var(--header-height));", "landing main CSS");
assertIncludes(styles, "grid-template-rows: minmax(0, 1fr) var(--feature-strip-height);", "landing main CSS");
assertIncludes(styles, "--device-preview-width", "landing CSS");
assertIncludes(styles, "--device-preview-width: clamp(230px, 13.5vw, 280px);", "larger device preview CSS");
assertIncludes(styles, "--device-preview-height", "landing CSS");
assertIncludes(styles, "--desktop-preview-height", "landing CSS");
assertIncludes(styles, "802 / 435", "desktop preview aspect ratio");
assertIncludes(styles, "width: var(--device-preview-width);", "device preview CSS");
assertIncludes(styles, "height: var(--device-preview-height);", "device preview CSS");
assertIncludes(styles, ".device-column", "device column CSS");
assertIncludes(styles, "justify-content: center;", "centered hero CSS");
assertIncludes(styles, ".brand-icon", "landing CSS");

assertFile("docker/landing/public/favicon.ico");
assertFile("docker/landing/public/favicon.png");
assertFile("docker/landing/public/assets/app-icon.png");
assertFile("docker/landing/public/assets/desktop-music.png");
assertFile("docker/landing/public/assets/android-music.png");
assertFile("docker/landing/public/downloads/README.md");
assertFile("docker/landing/public/downloads/.gitkeep");

const downloadsReadme = read("docker/landing/public/downloads/README.md");
assertIncludes(downloadsReadme, "tonezen-android.apk", "downloads README");
assertIncludes(downloadsReadme, "tonezen-windows.exe", "downloads README");
assertIncludes(downloadsReadme, "tonezen-macos.dmg", "downloads README");

const gitignore = read(".gitignore");
assertIncludes(gitignore, "/docker/landing/public/downloads/*", ".gitignore");
assertIncludes(gitignore, "!/docker/landing/public/downloads/README.md", ".gitignore");
assertIncludes(gitignore, "!/docker/landing/public/downloads/.gitkeep", ".gitignore");

console.log("Landing checks passed");
