package com.tonezen.app.e2e

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.tonezen.app.MainActivity
import com.tonezen.app.ui.testing.TestTags

fun AndroidComposeTestRule<*, MainActivity>.waitForLibraryShell(timeoutMillis: Long = 30_000) {
    try {
        waitUntil(timeoutMillis) {
            runCatching {
                onNodeWithTag(TestTags.TAB_AUDIOBOOKS).assertExists()
            }.isSuccess
        }
    } catch (timeout: ComposeTimeoutException) {
        runCatching { E2EScreenshots.capture("timeout_library_shell") }
        val onAuth = runCatching { onNodeWithTag(TestTags.AUTH_SIGN_IN).assertExists() }.isSuccess
        val loading = runCatching { onNodeWithTag(TestTags.LIBRARY_LOADING).assertExists() }.isSuccess
        throw IllegalStateException(
            "Library shell not shown within ${timeoutMillis}ms (authScreen=$onAuth, loading=$loading)",
            timeout,
        )
    }
    waitForIdle()
}
