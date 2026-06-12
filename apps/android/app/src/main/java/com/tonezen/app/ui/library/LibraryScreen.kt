package com.tonezen.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.ui.components.CycleCover
import com.tonezen.app.ui.components.EmptyLibrary
import com.tonezen.app.ui.components.LibraryLoading
import com.tonezen.app.ui.components.LibraryFilterSheet
import com.tonezen.app.ui.components.OfflineBanner
import com.tonezen.app.ui.components.SearchRow
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.TonezenTabs
import com.tonezen.app.ui.components.TonezenTopChromeBar
import com.tonezen.app.ui.theme.TonezenBottomChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.TonezenTopChromeOfflineBannerExtra
import com.tonezen.app.ui.theme.TonezenTopChromeScrollPaddingAudiobooks
import com.tonezen.app.ui.theme.TonezenTopChromeScrollPaddingMusic
import com.tonezen.app.ui.theme.tonezenScreenContentPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
internal fun LibraryScreen(
    hazeState: HazeState,
    cycles: List<Cycle>,
    allCycles: List<Cycle>,
    books: List<Book>,
    allBooks: List<Book>,
    downloadedBookIds: Set<String>,
    favoriteBookIds: Set<String>,
    offlineBanner: Boolean,
    isLoadingCatalog: Boolean,
    filter: LibraryFilterState,
    showFilterSheet: Boolean,
    onCycleClick: (Cycle) -> Unit,
    onBookClick: (Book) -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onApplyFilter: (LibraryFilterState) -> Unit,
    onResetFilter: () -> Unit,
    onContentFilterChange: (com.tonezen.app.domain.library.LibraryContentFilter) -> Unit,
    onSortOrderChange: (com.tonezen.app.domain.library.LibrarySortOrder) -> Unit,
    musicPreview: MusicTrackPreview?,
    musicPlayback: MusicPlaybackUi,
    musicDownloadProgress: Float?,
    musicPlaybackErrorRes: Int?,
    onMusicPlayPause: () -> Unit,
    onMusicShuffle: () -> Unit,
    onMusicTabSelected: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val music = allBooks.filter { it.contentType == ContentType.MUSIC }
    val isAudiobooksTab = selectedTab == 0
    val topChromeScrollPadding = remember(offlineBanner, isAudiobooksTab) {
        val base = if (isAudiobooksTab) {
            TonezenTopChromeScrollPaddingAudiobooks
        } else {
            TonezenTopChromeScrollPaddingMusic
        }
        if (offlineBanner) base + TonezenTopChromeOfflineBannerExtra else base
    }

    LaunchedEffect(selectedTab) {
        if (!isAudiobooksTab && showFilterSheet) {
            onDismissFilterSheet()
        }
        if (selectedTab == 1) {
            onMusicTabSelected()
        }
    }

    BackHandler(enabled = showFilterSheet && isAudiobooksTab, onBack = onDismissFilterSheet)

    LibraryFilterSheet(
        visible = showFilterSheet && isAudiobooksTab,
        hazeState = hazeState,
        filter = filter,
        onDismiss = onDismissFilterSheet,
        onApply = onApplyFilter,
        onReset = onResetFilter,
        onContentFilterChange = onContentFilterChange,
        onSortOrderChange = onSortOrderChange,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
                .background(TonezenSurface),
            contentPadding = tonezenScreenContentPadding(
                top = topChromeScrollPadding,
                bottom = TonezenBottomChromeScrollPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (isAudiobooksTab && isLoadingCatalog) {
                item { LibraryLoading() }
            } else if (isAudiobooksTab && allCycles.isEmpty()) {
                item { EmptyLibrary(offline = offlineBanner) }
            } else if (isAudiobooksTab) {
                items(cycles.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        row.forEach { cycle ->
                            LibraryCycleCard(
                                cycle = cycle,
                                downloaded = cycle.books.any { downloadedBookIds.contains(it.id) },
                                onClick = { onCycleClick(cycle) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else if (isLoadingCatalog) {
                item { LibraryLoading() }
            } else if (music.isEmpty()) {
                item { EmptyLibrary(offline = offlineBanner) }
            } else {
                item {
                    MusicPlayHero(
                        preview = musicPreview,
                        playback = musicPlayback,
                        downloadProgress = musicDownloadProgress,
                        playbackErrorRes = musicPlaybackErrorRes,
                        onPlayPause = onMusicPlayPause,
                        onShuffle = onMusicShuffle,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        TonezenTopChromeBar(
            modifier = Modifier.align(Alignment.TopCenter),
            hazeState = hazeState,
        ) {
            if (offlineBanner) {
                OfflineBanner()
            }
            TonezenTabs(
                selectedTab = selectedTab,
                onSelect = { tab ->
                    selectedTab = tab
                    if (tab == 1) onDismissFilterSheet()
                },
            )
            if (isAudiobooksTab) {
                SearchRow(
                    query = filter.query,
                    onQueryChange = onSearchChange,
                    onFilterClick = onFilterClick,
                )
            }
        }
    }
}

@Composable
private fun LibraryCycleCard(
    cycle: Cycle,
    downloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        CycleCover(cycle = cycle, modifier = Modifier.fillMaxWidth().aspectRatio(0.78f))
        if (downloaded) {
            StatusChip(
                label = stringResource(R.string.offline),
                tone = TonezenTeal,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}
