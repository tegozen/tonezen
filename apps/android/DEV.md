# Android UI development workflow

Full APK reinstall on every UI tweak is unnecessary. Use the layers below.

## 1. Compose Preview (fastest for layout)

Best for screens, spacing, colors, and typography while editing in Android Studio.

1. Open `app/src/debug/java/com/tonezen/app/ui/preview/TonezenPreviews.kt`.
2. Open **Split** or **Design** mode on a `@Preview` function.
3. Edit the composable and save — the preview refreshes in ~1 s without a device.

Add new previews there when you work on a screen. Keep previews in the `debug` source set so they never ship in release.

**Limitation:** SVG icons loaded from `android_asset` via Coil may be missing in Preview. Layout and theme still match the app.

## 2. Live Edit (hot reload on device/emulator)

Closest thing to hot reload for Compose on a real device.

1. Run the app once from **Android Studio** (Debug, not Release).
2. Enable **Settings → Build, Execution, Deployment → Live Edit → Live Edit for Compose**.
3. Keep the app on the current screen.
4. Change a `@Composable` body and save — UI updates on the device without reinstall.

Works for most UI-only edits. Does **not** work for manifest, resources, new dependencies, Hilt/DI graph changes, or new composable signatures.

## 3. Apply Code Changes (when Live Edit cannot)

With the debug app still running from Android Studio:

| Action | Shortcut | When |
| --- | --- | --- |
| Apply Changes | `Ctrl+F10` | Method body / composable tweaks |
| Apply Code Changes | `Ctrl+Alt+F10` | Slightly broader code changes |

Still much faster than `./gradlew installDebug` from scratch.

## 4. Gradle from terminal (fallback)

Use only when Preview/Live Edit are not enough (new deps, DI, manifest, assets):

```bash
cd apps/android
./gradlew :app:installDebug
```

Incremental compiles are usually a few seconds thanks to Gradle cache and configuration cache in `gradle.properties`.

Auto-reinstall on save (still compiles each time, not hot reload):

```bash
./gradlew :app:installDebug --continuous
```

## Recommended setup

| Task | Tool |
| --- | --- |
| Pixel-perfect UI, no device | Android Studio **Preview** |
| See real device/emulator behavior | Android Studio **Live Edit** |
| Edit Kotlin in Cursor | Cursor for code + Android Studio open for Preview/Live Edit |
| CI / clean install | `./gradlew :app:installDebug` |

There is no Flutter-style hot reload from Cursor/CLI alone on Android. Preview + Live Edit cover most view work without a full rebuild.
