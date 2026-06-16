package com.tonezen.app.e2e

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiSelector
import com.tonezen.app.MainActivity
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.ui.testing.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppNavigationE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val screenshotRule = E2EScreenshotRule()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        UiAutomatorHelpers.prepareDevice()
        runBlocking {
            sessionRepository.isLoaded.filter { it }.first()
            sessionRepository.saveSession(testOfflineSession())
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForLibraryShell()
    }

    @Test
    fun authenticatedUser_navigatesLibraryTabsAndProfile() {
        E2EScreenshots.capture("01_library_shell")
        composeRule.onNodeWithTag(TestTags.TAB_MUSIC).performClick()
        composeRule.waitForIdle()
        E2EScreenshots.capture("02_tab_music")

        composeRule.onNodeWithTag(TestTags.TAB_DOWNLOADS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Загрузки", substring = true).assertExists()
        E2EScreenshots.capture("03_tab_downloads")

        composeRule.onNodeWithTag(TestTags.NAV_PROFILE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Профиль", substring = true).assertExists()
        E2EScreenshots.capture("04_profile")

        composeRule.onNodeWithTag(TestTags.NAV_LIBRARY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.TAB_AUDIOBOOKS).assertIsDisplayed()
        E2EScreenshots.capture("05_back_to_library")
    }

    @Test
    fun app_reopensFromLauncher_afterPressHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val device = UiAutomatorHelpers.device()

        composeRule.onNodeWithTag(TestTags.TAB_AUDIOBOOKS).assertIsDisplayed()
        E2EScreenshots.capture("01_before_home")

        device.pressHome()
        E2EScreenshots.capture("02_home_screen")
        val launchIntent = instrumentation.context.packageManager.getLaunchIntentForPackage(packageName)
            ?: error("Launch intent missing for $packageName")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        instrumentation.targetContext.startActivity(launchIntent)

        assertTrue(device.findObject(UiSelector().packageName(packageName)).waitForExists(10_000))
        composeRule.waitForLibraryShell()
        composeRule.onNodeWithTag(TestTags.TAB_MUSIC).assertIsDisplayed()
        E2EScreenshots.capture("03_app_reopened")
    }
}
