package com.tonezen.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.auth.AuthScreen
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.remember
import com.tonezen.app.ui.library.CycleCardState
import com.tonezen.app.ui.library.CyclePlaybackUi
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.library.LibrarySection
import com.tonezen.app.ui.library.MusicPlaybackUi
import com.tonezen.app.playback.DownloadQueueState
import com.tonezen.app.ui.library.MusicListTrack
import com.tonezen.app.ui.profile.ProfileScreenContent
import com.tonezen.app.ui.profile.ProfileUiState
import com.tonezen.app.ui.theme.TonezenTheme

private val previewBooks = listOf(
    Book(
        id = "midnight",
        slug = "midnight-library",
        contentType = ContentType.AUDIOBOOK,
        title = "The Midnight Library",
        author = "Matt Haig",
    ),
    Book(
        id = "atomic",
        slug = "atomic-habits",
        contentType = ContentType.AUDIOBOOK,
        title = "Atomic Habits",
        author = "James Clear",
    ),
    Book(
        id = "piano",
        slug = "peaceful-piano",
        contentType = ContentType.MUSIC,
        title = "Peaceful Piano",
        author = "Various Artists",
    ),
)

private val previewCycle = Cycle(
    id = "cycle-1",
    slug = "midnight-cycle",
    title = "The Midnight Cycle",
    bookOrder = listOf("midnight", "atomic"),
    books = previewBooks.filter { it.contentType == ContentType.AUDIOBOOK },
)

@Preview(
    name = "Auth",
    showBackground = true,
    backgroundColor = 0xFF020617,
    device = Devices.PIXEL_7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AuthScreenPreview() {
    TonezenTheme {
        AuthScreen(
            padding = PaddingValues(0.dp),
            onLogin = { _, _ -> },
            error = null,
        )
    }
}

@Preview(
    name = "Auth / Error",
    showBackground = true,
    backgroundColor = 0xFF020617,
    device = Devices.PIXEL_7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AuthScreenErrorPreview() {
    TonezenTheme {
        AuthScreen(
            padding = PaddingValues(0.dp),
            onLogin = { _, _ -> },
            error = "Invalid email or password",
        )
    }
}

@Preview(
    name = "Library",
    showBackground = true,
    backgroundColor = 0xFF020617,
    device = Devices.PIXEL_7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LibraryScreenPreview() {
    TonezenTheme {
        LibraryScreen(
            hazeState = remember { HazeState() },
            section = LibrarySection.Books,
            cycles = listOf(previewCycle),
            allCycles = listOf(previewCycle),
            books = previewBooks,
            allBooks = previewBooks,
            downloadedBookIds = setOf("midnight"),
            cycleCardStateById = mapOf(
                previewCycle.id to CycleCardState(
                    isDownloaded = true,
                    progressFraction = 0.42f,
                ),
            ),
            cyclePlayback = CyclePlaybackUi(),
            offlineBanner = false,
            isLoadingCatalog = false,
            filter = LibraryFilterState(),
            showFilterSheet = false,
            onCycleClick = {},
            onCyclePlay = {},
            onBookClick = {},
            onSearchChange = {},
            onFilterClick = {},
            onDismissFilterSheet = {},
            onApplyFilter = {},
            onResetFilter = {},
            onContentFilterChange = {},
            onSortOrderChange = {},
            musicTrackList = listOf(
                MusicListTrack(
                    trackId = "track-1",
                    trackTitle = "Самая",
                    artist = "Miyagi & Andy Panda",
                    albumTitle = "Miyagi",
                    bookId = "piano",
                    durationMs = 245_000L,
                    isDownloaded = true,
                ),
                MusicListTrack(
                    trackId = "track-2",
                    trackTitle = "Люби меня",
                    artist = "Miyagi",
                    albumTitle = "Music",
                    bookId = "piano",
                    durationMs = 198_000L,
                    isDownloaded = false,
                ),
            ),
            musicPlayback = MusicPlaybackUi(),
            downloadQueue = DownloadQueueState(),
            musicPlaybackErrorRes = null,
            cyclePlaybackErrorRes = null,
            onMusicWavePlay = {},
            onMusicTrackClick = {},
            onDownloadMusicTrack = {},
            onDeleteMusicTrack = {},
            onDownloadAllMusic = {},
            onMusicTabSelected = {},
            showMiniPlayer = false,
        )
    }
}

// Android Studio @Preview only — fake ProfileUiState, no Hilt/DB/network.
private val profilePreviewState = ProfileUiState(
    sessionState = SessionState.AUTHENTICATED_ONLINE,
    displayName = "Админ",
    email = "admin@tonezen.local",
    memberSinceLabel = "15.08.2024",
    lastSyncTime = "10:18",
)

@Preview(
    name = "Profile",
    showBackground = true,
    backgroundColor = 0xFF020617,
    device = Devices.PIXEL_7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileScreenPreview() {
    TonezenTheme {
        ProfileScreenContent(
            padding = PaddingValues(0.dp),
            hazeState = remember { HazeState() },
            state = profilePreviewState,
            onSignOutClick = {},
            onAccountClick = {},
            onSettingsClick = {},
        )
    }
}
