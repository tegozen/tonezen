package com.tonezen.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonezen.app.ui.TonezenComposeTestContent
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.TonezenBottomNavigation
import com.tonezen.app.ui.testing.TestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomNavComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNav_switchesBetweenMusicBooksDownloadsAndProfile() {
        var selected by mutableStateOf(BottomDestination.Music)
        composeRule.setContent {
            TonezenComposeTestContent {
                TonezenBottomNavigation(
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag(TestTags.NAV_BOOKS).performClick()
        composeRule.runOnIdle { assertEquals(BottomDestination.Books, selected) }

        composeRule.onNodeWithTag(TestTags.NAV_DOWNLOADS).performClick()
        composeRule.runOnIdle { assertEquals(BottomDestination.Downloads, selected) }

        composeRule.onNodeWithTag(TestTags.NAV_PROFILE).performClick()
        composeRule.runOnIdle { assertEquals(BottomDestination.Profile, selected) }

        composeRule.onNodeWithTag(TestTags.NAV_MUSIC).performClick()
        composeRule.runOnIdle { assertEquals(BottomDestination.Music, selected) }
    }
}
