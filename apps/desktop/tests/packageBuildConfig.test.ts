import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("desktop package build config", () => {
  const packageJson = JSON.parse(
    fs.readFileSync(path.join(process.cwd(), "package.json"), "utf8"),
  ) as {
    build?: {
      files?: string[];
      mac?: { icon?: string };
      nsis?: { installerIcon?: string; uninstallerIcon?: string };
      win?: { icon?: string };
    };
  };

  it("includes main-process image resources used by packaged Electron windows", () => {
    expect(packageJson.build?.files).toContain("resources/**/*");
  });

  it("sets branded application icons for Windows and macOS packages", () => {
    const windowsIcon = "resources/app-icon.ico";
    const macIcon = "resources/app-icon.icns";

    expect(packageJson.build?.win?.icon).toBe(windowsIcon);
    expect(packageJson.build?.nsis?.installerIcon).toBe(windowsIcon);
    expect(packageJson.build?.nsis?.uninstallerIcon).toBe(windowsIcon);
    expect(packageJson.build?.mac?.icon).toBe(macIcon);

    expect(fs.existsSync(path.join(process.cwd(), windowsIcon))).toBe(true);
    expect(fs.existsSync(path.join(process.cwd(), macIcon))).toBe(true);
  });
});
