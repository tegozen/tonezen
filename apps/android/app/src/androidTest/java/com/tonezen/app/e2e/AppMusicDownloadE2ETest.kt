package com.tonezen.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonezen.app.MainActivity
import com.tonezen.app.data.local.CatalogDao
import com.tonezen.app.data.local.DownloadQueueDao
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ResumableDownloadOutcome
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.data.remote.catalog.CatalogRemoteApi
import com.tonezen.app.data.remote.downloads.DownloadsRemoteApi
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import com.tonezen.app.ui.testing.TestTags
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import com.tonezen.app.di.RemoteApiModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * Visible UI proof: Music tab → tap download → file on disk.
 * Screenshots: apps/android/app/build/e2e-screenshots/.../AppMusicDownloadE2ETest/
 */
@UninstallModules(RemoteApiModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppMusicDownloadE2ETest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val hiltInjectRule = object : ExternalResource() {
        override fun before() {
            hiltRule.inject()
        }
    }

    @get:Rule(order = 2)
    val screenshotRule = E2EScreenshotRule()

    @get:Rule(order = 3)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var catalogDao: CatalogDao

    @Inject
    lateinit var downloadQueueDao: DownloadQueueDao

    @Inject
    lateinit var sessionRepository: SessionRepository

    @BindValue
    @JvmField
    val progressRemoteApi: ProgressRemoteApi = mockk(relaxed = true)

    @BindValue
    @JvmField
    val downloadsRemoteApi: DownloadsRemoteApi = mockk(relaxed = true)

    @BindValue
    @JvmField
    val catalogRemoteApi: CatalogRemoteApi = mockk(relaxed = true) {
        coEvery { fetchBooks(any()) } returns listOf(E2ECatalogSeed.testMusicBook())
        coEvery { fetchCycles(any()) } returns emptyList()
        coEvery { fetchBookDetail(E2ECatalogSeed.BOOK_ID, any()) } returns
            (E2ECatalogSeed.testMusicBook() to listOf(E2ECatalogSeed.testMusicTrack()))
    }

    @BindValue
    @JvmField
    val networkMonitor: NetworkMonitor = mockk(relaxed = true) {
        every { isOnline() } returns true
        every { online } returns MutableStateFlow(true)
    }

    @BindValue
    @JvmField
    val downloadRepository: DownloadRepository = run {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        mockk(relaxed = true) {
            coEvery {
                downloadTrackResumable(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } coAnswers {
                val bookId = it.invocation.args[1] as String
                val trackId = it.invocation.args[2] as String
                val onProgress = it.invocation.args[5] as (Float) -> Unit
                val file = SafeLocalStorage.trackFile(appContext.filesDir, bookId, trackId)
                    ?: error("Invalid track path")
                file.parentFile?.mkdirs()
                onProgress(0.2f)
                delay(400)
                onProgress(0.6f)
                delay(400)
                file.writeBytes(ByteArray(2048) { index -> index.toByte() })
                onProgress(1f)
                ResumableDownloadOutcome(
                    finalFile = file,
                    bytesDownloaded = file.length(),
                    totalBytes = file.length(),
                )
            }
        }
    }

    @Before
    fun setUp() {
        UiAutomatorHelpers.prepareDevice()
        runBlocking {
            sessionRepository.isLoaded.filter { it }.first()
            E2ECatalogSeed.seedMusicTrack(catalogDao)
            E2ECatalogSeed.clearDownloadState(context, catalogDao, downloadQueueDao)
            sessionRepository.saveSession(testOfflineSession())
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForLibraryShell()
    }

    @Test
    fun singleTrackDownload_visibleInMusicTab() {
        composeRule.onNodeWithTag(TestTags.TAB_MUSIC, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 15_000) {
            runCatching {
                composeRule.onNodeWithText(E2ECatalogSeed.TRACK_TITLE).assertExists()
            }.isSuccess
        }
        E2EScreenshots.capture("01_music_track_visible")

        composeRule.onNodeWithTag(TestTags.TRACK_DOWNLOAD, useUnmergedTree = true).performClick()
        E2EScreenshots.capture("02_download_tapped")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText("%", substring = true).assertExists()
            }.isSuccess
        }
        E2EScreenshots.capture("03_download_progress")

        composeRule.waitUntil(timeoutMillis = 30_000) {
            val file = SafeLocalStorage.trackFile(
                context.filesDir,
                E2ECatalogSeed.BOOK_ID,
                E2ECatalogSeed.TRACK_ID,
            )
            val onDisk = file != null && file.isFile && file.length() > 0L
            val indicatorVisible = runCatching {
                composeRule.onNodeWithTag(TestTags.TRACK_DOWNLOADED, useUnmergedTree = true).assertExists()
            }.isSuccess
            onDisk && indicatorVisible
        }
        composeRule.waitForIdle()
        E2EScreenshots.capture("04_download_complete")

        val file = SafeLocalStorage.trackFile(context.filesDir, E2ECatalogSeed.BOOK_ID, E2ECatalogSeed.TRACK_ID)
        assertTrue(file != null && file.isFile && file.length() > 0L)
        composeRule.onNodeWithTag(TestTags.TRACK_DOWNLOADED, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(E2ECatalogSeed.TRACK_TITLE).assertIsDisplayed()
    }
}
