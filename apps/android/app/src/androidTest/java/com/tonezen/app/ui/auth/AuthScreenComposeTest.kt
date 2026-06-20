package com.tonezen.app.ui.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonezen.app.ui.TonezenComposeTestContent
import com.tonezen.app.ui.testing.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signIn_disabledWhenFieldsEmpty() {
        composeRule.setContent {
            TonezenComposeTestContent {
                AuthScreen(
                    padding = PaddingValues(),
                    onLogin = { _, _ -> },
                    error = null,
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.AUTH_SIGN_IN).assertIsNotEnabled()
    }

    @Test
    fun signIn_submitsTrimmedCredentials() {
        var submitted: Pair<String, String>? = null
        composeRule.setContent {
            TonezenComposeTestContent {
                AuthScreen(
                    padding = PaddingValues(),
                    onLogin = { email, password -> submitted = email to password },
                    error = null,
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.AUTH_EMAIL).performTextInput("  user@test.com  ")
        composeRule.onNodeWithTag(TestTags.AUTH_PASSWORD).performTextInput("secret")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGN_IN).performClick()

        assertEquals("user@test.com" to "secret", submitted)
    }

    @Test
    fun signIn_showsLoginErrorMessage() {
        composeRule.setContent {
            TonezenComposeTestContent {
                AuthScreen(
                    padding = PaddingValues(),
                    onLogin = { _, _ -> },
                    error = AuthViewModel.AUTH_LOGIN_FAILED_ERROR,
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.AUTH_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Не удалось войти. Проверьте email и пароль.").assertExists()
    }

    @Test
    fun signIn_noErrorWhenErrorIsNull() {
        composeRule.setContent {
            TonezenComposeTestContent {
                AuthScreen(
                    padding = PaddingValues(),
                    onLogin = { _, _ -> },
                    error = null,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(TestTags.AUTH_ERROR).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun signUp_hidesRegistrationFormUntilInviteVerified() {
        composeRule.setContent {
            TonezenComposeTestContent {
                AuthScreen(
                    padding = PaddingValues(),
                    onLogin = { _, _ -> },
                    onVerifyInviteCode = {},
                    onSignup = { _, _, _, _, _ -> },
                    onPasswordRecovery = {},
                    inviteCodeVerified = false,
                    error = null,
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.AUTH_SHOW_SIGN_UP).performClick()

        composeRule.onNodeWithTag(TestTags.AUTH_INVITE_CODE).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(TestTags.AUTH_SIGN_UP).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun signUp_submitsWhenInviteVerified() {
        var submitted: List<String>? = null
        composeRule.setContent {
            TonezenComposeTestContent {
                AuthScreen(
                    padding = PaddingValues(),
                    onLogin = { _, _ -> },
                    onVerifyInviteCode = {},
                    onSignup = { invite, email, name, password, confirm ->
                        submitted = listOf(invite, email, name, password, confirm)
                    },
                    onPasswordRecovery = {},
                    inviteCodeVerified = true,
                    error = null,
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.AUTH_SHOW_SIGN_UP).performClick()
        composeRule.onNodeWithTag(TestTags.AUTH_INVITE_CODE).performTextInput("CODE12345678")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGNUP_EMAIL).performTextInput("user@example.com")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGNUP_NAME).performTextInput("User")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGNUP_PASSWORD).performTextInput("secret123")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGNUP_CONFIRM).performTextInput("secret123")
        composeRule.onNodeWithTag(TestTags.AUTH_SIGN_UP).performClick()

        assertEquals(
            listOf("CODE12345678", "user@example.com", "User", "secret123", "secret123"),
            submitted,
        )
    }

    @Test
    fun passwordRecovery_submitsEmail() {
        var submitted: String? = null
        composeRule.setContent {
            TonezenComposeTestContent {
                AuthScreen(
                    padding = PaddingValues(),
                    onLogin = { _, _ -> },
                    onVerifyInviteCode = {},
                    onSignup = { _, _, _, _, _ -> },
                    onPasswordRecovery = { submitted = it },
                    inviteCodeVerified = false,
                    error = null,
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.AUTH_SHOW_RECOVERY).performClick()
        composeRule.onNodeWithTag(TestTags.AUTH_RECOVERY_EMAIL).performTextInput("user@example.com")
        composeRule.onNodeWithTag(TestTags.AUTH_RECOVERY_SUBMIT).performClick()

        assertEquals("user@example.com", submitted)
    }
}
