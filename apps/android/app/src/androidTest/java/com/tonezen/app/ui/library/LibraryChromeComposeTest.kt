package com.tonezen.app.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonezen.app.ui.TonezenComposeTestContent
import com.tonezen.app.ui.components.EmptyLibrary
import com.tonezen.app.ui.components.LibraryLoading
import com.tonezen.app.ui.testing.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibrary_showsOnlineCopy() {
        composeRule.setContent {
            TonezenComposeTestContent {
                EmptyLibrary(offline = false)
            }
        }

        composeRule.onNodeWithTag(TestTags.EMPTY_LIBRARY).assertIsDisplayed()
        composeRule.onNodeWithText("Пока пусто").assertIsDisplayed()
        composeRule.onNodeWithText("На сервере пока нет контента", substring = true).assertExists()
    }

    @Test
    fun emptyLibrary_showsOfflineCopy() {
        composeRule.setContent {
            TonezenComposeTestContent {
                EmptyLibrary(offline = true)
            }
        }

        composeRule.onNodeWithTag(TestTags.EMPTY_LIBRARY).assertIsDisplayed()
        composeRule.onNodeWithText("Нет подключения к серверу", substring = true).assertExists()
    }

    @Test
    fun libraryLoading_showsLoadingLabel() {
        composeRule.setContent {
            TonezenComposeTestContent {
                LibraryLoading()
            }
        }

        composeRule.onNodeWithTag(TestTags.LIBRARY_LOADING).assertIsDisplayed()
        composeRule.onNodeWithText("Загрузка библиотеки…").assertExists()
    }

}
