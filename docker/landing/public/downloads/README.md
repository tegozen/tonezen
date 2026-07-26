# Tonezen downloads

Place public app release files in this directory. The landing page links to these exact names:

- `tonezen-android.apk`
- `tonezen-windows.exe`
- `tonezen-macos.dmg`

Crash symbols (not linked on the landing page; FTP them with the apps for GlitchTip deobfuscation):

- `tonezen-android-mapping.txt.gz`
- `tonezen-android-proguard-uuid.txt`
- `tonezen-desktop-sourcemaps.tar.gz`

Collect after a release build: `node scripts/collect-release-symbols.mjs`

These binaries/symbols are intentionally ignored by git. Keep this README and `.gitkeep` tracked so the directory exists after checkout.
