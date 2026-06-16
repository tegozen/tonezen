---
name: android-uiautomator-e2e
description: Writes and verifies Android instrumented E2E tests with UI Automator (modern 2.4 DSL or legacy UiDevice), captures mandatory per-step screenshots for visual proof, and runs tests on emulator/device. Use when the user asks for E2E/UI Automator tests, device tests with screenshots, visual verification of Android flows, or says "e2e", "UI Automator", "скриншоты тестов".
---

# Android UI Automator E2E (Tonezen)

Write **real user flows** on emulator/device. Every assertion step must leave a **screenshot artifact** so the user can visually confirm behavior.

Official reference: [UI Automator (Android Developers)](https://developer.android.com/training/testing/other-components/ui-automator?hl=ru)

## Test layers (pick the right tool)

| Layer | Tool | When |
|-------|------|------|
| Screen states, forms, empty states | **Compose UI Testing** (`createComposeRule`, `onNodeWithTag`) | Isolated composables; fast feedback |
| XML/View screens | **Espresso** | Legacy views; syncs with main thread |
| Full device E2E (app + system UI) | **UI Automator** | Login, navigation, permissions, launcher, settings, other apps |

UI Automator runs **outside the app process** — closest to “user taps the phone”. Combine with Compose selectors (`TestTags`) inside `@HiltAndroidTest` when the app is Compose.

## Mandatory: screenshot evidence

**Rule:** no E2E step is “verified” without a screenshot saved and pulled to the host.

For each logical step (launch, tap, error shown, tab switch, download complete):

1. `E2EScreenshots.capture(stepName)` — after UI is stable
2. Run assertion (Compose `onNodeWithTag` / UiAutomator `onElement` / legacy `UiSelector`)
3. On failure: screenshot is already saved for debugging

After the test run, pull artifacts:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File apps/android/scripts/pull-e2e-screenshots.ps1
```

Output: `apps/android/app/build/e2e-screenshots/<Class>_<method>/<step>.png`

For video recordings of full runs: `apps/android/scripts/record-device-e2e.ps1`

## Project conventions

| Item | Location |
|------|----------|
| E2E tests | `apps/android/app/src/androidTest/java/com/tonezen/app/e2e/` |
| Compose UI tests | `apps/android/app/src/androidTest/java/com/tonezen/app/ui/` |
| `TestTags` | `apps/android/app/src/main/java/com/tonezen/app/ui/testing/TestTags.kt` |
| Screenshot helper | `apps/android/app/src/androidTest/java/com/tonezen/app/e2e/E2EScreenshots.kt` |
| Screenshot rule | `apps/android/app/src/androidTest/java/com/tonezen/app/e2e/E2EScreenshotRule.kt` |
| UiAutomator helpers | `apps/android/app/src/androidTest/java/com/tonezen/app/e2e/UiAutomatorHelpers.kt` |
| Hilt runner | `HiltTestRunner` + `@HiltAndroidTest` |
| Strings (RU) | `apps/android/app/src/main/res/values/strings.xml` |

Add `Modifier.testTag(TestTags.…)` to new interactive controls. Icon-only buttons need `testTag` **and** `contentDescription` for UiAutomator.

## Write a new E2E test

### 1. Scaffold

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MyFlowE2ETest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val screenshotRule = E2EScreenshotRule()
    @get:Rule(order = 2) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        UiAutomatorHelpers.prepareDevice()
        // clearSession() or seedSession() as needed
    }

    @Test
    fun myScenario_stepByStep() {
        E2EScreenshots.capture("01_launch")
        // interact + assert
        E2EScreenshots.capture("02_after_action")
    }
}
```

### 2. Prefer modern UI Automator 2.4 DSL

Dependency (when adopting 2.4):

```kotlin
androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0-alpha05")
```

```kotlin
@Test
fun loginWithPermission() = uiAutomator {
    watchFor(PermissionDialog) { clickAllow() }
    startActivity(MainActivity::class.java)
    waitForAppToBeVisible("com.tonezen.app")
    activeWindow().waitForStable()

    E2EScreenshots.capture("login_screen")
    onElement { textAsString() == "Войти" }.click()
    // ...
    E2EScreenshots.capture("login_error")

    val reporter = ResultsReporter("e2e-${testName}")
    val file = reporter.addNewFile("final_state", "Final screen")
    activeWindow().takeScreenshot().saveToFile(file)
    reporter.reportToInstrumentation()
}
```

Key APIs: `uiAutomator { }`, `onElement { }`, `onElementOrNull { }`, `watchFor(PermissionDialog)`, `waitForAppToBeVisible`, `activeWindow().waitForStable()`, `takeScreenshot()`, `ResultsReporter`.

### 3. Legacy API (current 2.3.x fallback)

```kotlin
val device = UiAutomatorHelpers.device()
device.findObject(UiSelector().text("Войти")).waitForExists(5_000)
E2EScreenshots.capture("login_visible")
```

Migrate to 2.4 DSL when dependency is bumped — see [reference.md](reference.md).

### 4. System flows (UiAutomator-only)

- Permission dialogs → `watchFor(PermissionDialog)` or `UiAutomatorHelpers.dismissSystemDialogs()`
- Home → relaunch via `getLaunchIntentForPackage` (see `AppNavigationE2ETest`)
- Notifications / settings → `UiSelector().packageName("com.android.settings")`

### 5. Do not mock away the bug

E2E must exercise **real UI + real disk/network** for the scenario under test. Controller-only tests belong in `playback/` — label them as integration, not user E2E.

## Run and verify

```powershell
cd apps/android
.\gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.tonezen.app.e2e.MyFlowE2ETest"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/pull-e2e-screenshots.ps1
```

Checklist before reporting success:

- [ ] Gradle: tests green on connected emulator/device
- [ ] Every step has a PNG in `app/build/e2e-screenshots/`
- [ ] Screenshots match expected UI (login, tabs, errors, empty states)
- [ ] No flaky waits — use `waitUntil` / `onElement(timeout)` instead of `Thread.sleep`

## Agent workflow

1. Read the user scenario and pick E2E vs Compose UI test.
2. Add missing `TestTags` on touched UI.
3. Implement test with **screenshot after each step**.
4. Run `connectedDebugAndroidTest` on available device (start emulator if needed).
5. Pull screenshots and **describe what each image proves** when reporting to the user.
6. If test fails: use last screenshot + logcat snippet; fix and re-run.

## Additional resources

- API migration table, predicate examples: [reference.md](reference.md)
- Android docs: https://developer.android.com/training/testing/other-components/ui-automator?hl=ru
