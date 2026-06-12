package com.tonezen.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
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
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.tonezenScreenContentPadding

@Composable
internal fun LibraryScreen(
    padding: PaddingValues,
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

    LaunchedEffect(selectedTab) {
        if (!isAudiobooksTab && showFilterSheet) {
            onDismissFilterSheet()
        }
        if (selectedTab == 1) {
            onMusicTabSelected()
        }
    }

    BackHandler(enabled = showFilterSheet && isAudiobooksTab, onBack = onDismissFilterSheet)

    if (showFilterSheet && isAudiobooksTab) {
        LibraryFilterSheet(
            filter = filter,
            onDismiss = onDismissFilterSheet,
            onApply = onApplyFilter,
            onReset = onResetFilter,
            onContentFilterChange = onContentFilterChange,
            onSortOrderChange = onSortOrderChange,
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(TonezenSurface),
        contentPadding = tonezenScreenContentPadding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { LibraryHeader() }
        if (offlineBanner) {
            item { OfflineBanner() }
        }
        item {
            TonezenTabs(
                selectedTab = selectedTab,
                onSelect = { tab ->
                    selectedTab = tab
                    if (tab == 1) onDismissFilterSheet()
                },
            )
        }
        if (isAudiobooksTab) {
            item {
                SearchRow(
                    query = filter.query,
                    onQueryChange = onSearchChange,
                    onFilterClick = onFilterClick,
                )
            }
        }
        if (isAudiobooksTab && isLoadingCatalog) {
            item { LibraryLoading() }
        } else if (isAudiobooksTab && allCycles.isEmpty()) {
            item { EmptyLibrary(offline = offlineBanner) }
        } else if (isAudiobooksTab) {
            item {
                Text(
                    stringResource(R.string.tab_audiobooks),
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
}

@Composable
private fun LibraryHeader() {
    Text(
        text = stringResource(R.string.app_name),
        color = TonezenInk,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
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
