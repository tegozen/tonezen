package com.tonezen.app.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiSelector
import com.tonezen.app.MainActivity
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.ui.testing.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppAuthFlowE2ETest {
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
        sessionRepository.clearSession()
        composeRule.waitForIdle()
    }

    @Test
    fun coldStart_showsLoginForm() {
        composeRule.waitForIdle()
        E2EScreenshots.capture("01_login_form")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGN_IN).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.AUTH_EMAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.AUTH_PASSWORD).assertIsDisplayed()

        val device = UiAutomatorHelpers.device()
        assertTrue(device.findObject(UiSelector().text("Войти")).waitForExists(5_000))
        E2EScreenshots.capture("02_login_form_verified")
    }

    @Test
    fun invalidCredentials_showsLoginError() {
        composeRule.onNodeWithTag(TestTags.AUTH_EMAIL).performTextInput("bad@test.com")
        composeRule.onNodeWithTag(TestTags.AUTH_PASSWORD).performTextInput("wrong-password")
        E2EScreenshots.capture("01_credentials_filled")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGN_IN).performClick()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithTag(TestTags.AUTH_ERROR).assertExists()
            }.isSuccess
        }
        composeRule.onNodeWithText("Не удалось войти", substring = true).assertExists()
        assertTrue(UiAutomatorHelpers.waitForText("Не удалось войти", timeoutMs = 5_000))
        E2EScreenshots.capture("02_login_error_shown")
    }
}
