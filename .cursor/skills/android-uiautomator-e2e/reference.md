# UI Automator reference (Tonezen)

## Dependency versions

| Version | API style | Tonezen status |
|---------|-----------|----------------|
| `2.3.0` | `UiDevice` + `UiSelector` | Current default in `build.gradle.kts` |
| `2.4.0-alpha05` | `uiAutomator { }` DSL, `ResultsReporter`, `takeScreenshot()` | Recommended for new E2E |

## Modern → legacy migration

| Action | Legacy (2.3) | Modern (2.4) |
|--------|--------------|--------------|
| Entry | `UiDevice.getInstance(instrumentation)` | `uiAutomator { }` |
| Find by text | `device.findObject(UiSelector().text("Войти"))` | `onElement { textAsString() == "Войти" }` |
| Find by test tag | Not reliable on Compose | Prefer Compose `onNodeWithTag`; or `contentDescription` |
| Wait for idle | `device.waitForIdle()` | `onElement(timeoutMs) { … }` or `activeWindow().waitForStable()` |
| Permissions | Manual `UiSelector().textMatches("(?i)allow")` | `watchFor(PermissionDialog) { clickAllow() }` |
| Screenshot | `uiAutomation.takeScreenshot()` / `E2EScreenshots` | `activeWindow().takeScreenshot().saveToFile(file)` + `ResultsReporter` |
| Start app | `context.startActivity(launchIntent)` | `startApp("com.tonezen.app")` / `startActivity(MainActivity::class.java)` |
| Reset state | `pm clear` shell | `clearAppData("com.tonezen.app")` |

## Predicate examples (2.4)

```kotlin
onElement { textAsString() == "Войти" }
onElement { viewIdResourceName == "auth_sign_in" } // resource name without package
onElement { contentDescription == "Скачать" }
onElementOrNull { textAsString() == "Пропустить" }?.click()
onElements { className == "android.widget.TextView" && isClickable }
```

Chain parent → child:

```kotlin
onElement { viewIdResourceName == "parent" }
    .onElement { viewIdResourceName == "child" }
    .click()
```

## Tonezen selectors

| UI | Selector |
|----|----------|
| Sign in | `TestTags.AUTH_SIGN_IN` / text `Войти` |
| Email / password | `TestTags.AUTH_EMAIL`, `AUTH_PASSWORD` |
| Login error | `TestTags.AUTH_ERROR` |
| Library tabs | `TAB_AUDIOBOOKS`, `TAB_MUSIC`, `TAB_DOWNLOADS` |
| Bottom nav | `NAV_LIBRARY`, `NAV_PROFILE` |
| Empty library | `EMPTY_LIBRARY` / `Пока пусто` |
| Loading | `LIBRARY_LOADING` |
| Track download | `TRACK_DOWNLOAD` / contentDescription `Скачать` |

## Screenshot policy (detail)

Capture points for typical flows:

| Flow | Steps to screenshot |
|------|---------------------|
| Auth | launch → fields filled → after submit → error/success |
| Navigation | library shell → each tab → profile → return |
| Download | before tap → progress → completed → downloads tab |
| Permissions | dialog visible → after allow/deny |
| Launcher | home screen → app icon → app restored |

File naming: `01_launch`, `02_tab_music`, `03_error_shown` — zero-padded, stable order.

## Compose + UiAutomator hybrid

Inside `@HiltAndroidTest`:

- **Compose** for in-app nodes (`createAndroidComposeRule`, `TestTags`)
- **UiAutomator** for system UI, launcher, permissions, cross-app

```kotlin
composeRule.onNodeWithTag(TestTags.TAB_MUSIC).performClick()
composeRule.waitForIdle()
E2EScreenshots.capture("tab_music")

device.pressHome()
E2EScreenshots.capture("home_screen")
// UiAutomator relaunch...
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Empty emulator in recording | `adb shell input keyevent KEYCODE_WAKEUP`; use `record-device-e2e.ps1` |
| Element not found | Add `testTag`; increase timeout; `waitForStable()` before screenshot |
| Flaky tab switch | `composeRule.waitUntil { … }` not fixed `sleep` |
| Session stuck on AppShell | `sessionRepository.clearSession()` in `@Before` |
| Screenshot black screen | Wake device; wait for animation end; `waitForStable()` |

## Gradle commands (PowerShell)

Single class:

```powershell
.\gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tonezen.app.e2e.AppAuthFlowE2ETest"
```

Single method:

```powershell
.\gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tonezen.app.e2e.AppAuthFlowE2ETest#coldStart_showsLoginForm"
```

Pull screenshots:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File apps/android/scripts/pull-e2e-screenshots.ps1
```
