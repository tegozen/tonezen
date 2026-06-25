package com.tonezen.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.progress.BookContinueState
import com.tonezen.app.ui.components.CheckCircleGlyph
import com.tonezen.app.ui.components.CompactMediaPlayButton
import com.tonezen.app.ui.components.ContinueResumeMeta
import com.tonezen.app.ui.components.ContinueResumeVariant
import com.tonezen.app.ui.components.CycleCover
import com.tonezen.app.ui.components.EmptyLibrary
import com.tonezen.app.ui.components.LibraryLoading
import com.tonezen.app.ui.components.LibraryFilterSheet
import com.tonezen.app.ui.components.OfflineBanner
import com.tonezen.app.ui.components.SearchRow
import com.tonezen.app.ui.components.TonezenTopChromeBar
import com.tonezen.app.playback.DownloadQueueState
import com.tonezen.app.playback.forMusic
import com.tonezen.app.playback.toMusicDownloadState
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenChromeHeaderRowHeight
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.TonezenTopChromeOfflineBannerExtra
import com.tonezen.app.ui.theme.TonezenTopChromeScrollPaddingBooks
import com.tonezen.app.ui.theme.tonezenScreenContentPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

internal enum class LibrarySection {
    Music,
    Books,
}

@Composable
internal fun LibraryScreen(
    hazeState: HazeState,
    section: LibrarySection,
    cycles: List<Cycle>,
    allCycles: List<Cycle>,
    books: List<Book>,
    allBooks: List<Book>,
    downloadedBookIds: Set<String>,
    cycleCardStateById: Map<String, CycleCardState>,
    cyclePlayback: CyclePlaybackUi,
    offlineBanner: Boolean,
    isLoadingCatalog: Boolean,
    filter: LibraryFilterState,
    showFilterSheet: Boolean,
    onCycleClick: (Cycle) -> Unit,
    onCyclePlay: (Cycle) -> Unit,
    onBookClick: (Book) -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onApplyFilter: (LibraryFilterState) -> Unit,
    onResetFilter: () -> Unit,
    onContentFilterChange: (com.tonezen.app.domain.library.LibraryContentFilter) -> Unit,
    onSortOrderChange: (com.tonezen.app.domain.library.LibrarySortOrder) -> Unit,
    musicTrackList: List<MusicListTrack>,
    musicPlayback: MusicPlaybackUi,
    downloadQueue: DownloadQueueState,
    musicPlaybackErrorMessage: String?,
    cyclePlaybackErrorMessage: String?,
    onMusicWavePlay: () -> Unit,
    onMusicTrackClick: (MusicListTrack) -> Unit,
    onDownloadMusicTrack: (MusicListTrack) -> Unit,
    onDeleteMusicTrack: (MusicListTrack) -> Unit,
    onDownloadAllMusic: () -> Unit,
    onMusicTabSelected: () -> Unit,
    showMiniPlayer: Boolean,
    isNetworkOnline: Boolean = true,
) {
    val music = allBooks.filter { it.contentType == ContentType.MUSIC }
    val isBooksSection = section == LibrarySection.Books
    val isMusicSection = section == LibrarySection.Music
    var showAllMusicTracks by rememberSaveable { mutableStateOf(false) }
    val musicDownloadQueue = remember(downloadQueue) { downloadQueue.forMusic() }
    val musicDownload = remember(musicDownloadQueue) { musicDownloadQueue.toMusicDownloadState() }
    val topChromeScrollPadding = remember(offlineBanner, section) {
        val base = if (isBooksSection) {
            TonezenTopChromeScrollPaddingBooks
        } else {
            TonezenPageChromeScrollPadding
        }
        if (offlineBanner) base + TonezenTopChromeOfflineBannerExtra else base
    }
    val bottomChromeScrollPadding = tonezenBottomChromeScrollPadding(
        showMiniPlayer = showMiniPlayer,
        showBottomNav = true,
    )

    BackHandler(enabled = showFilterSheet && isBooksSection, onBack = onDismissFilterSheet)

    LibraryFilterSheet(
        visible = showFilterSheet && isBooksSection,
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
                bottom = bottomChromeScrollPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (isBooksSection && isLoadingCatalog) {
                item { LibraryLoading() }
            } else if (isBooksSection && allCycles.isEmpty()) {
                item { EmptyLibrary(offline = offlineBanner) }
            } else if (isBooksSection) {
                if (cyclePlaybackErrorMessage != null) {
                    item {
                        Text(
                            text = cyclePlaybackErrorMessage,
                            color = Color(0xFFF87171),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(cycles.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        row.forEach { cycle ->
                            val cardState = cycleCardStateById[cycle.id] ?: CycleCardState()
                            val isThisCycle = cyclePlayback.cycleId == cycle.id
                            LibraryCycleCard(
                                cycle = cycle,
                                isDownloaded = cardState.isDownloaded,
                                progressFraction = cardState.progressFraction,
                                continueState = cardState.continueState,
                                isPlaying = isThisCycle && cyclePlayback.isPlaying,
                                downloadProgress = if (isThisCycle && cyclePlayback.isPreparing) {
                                    cyclePlayback.downloadProgress
                                } else {
                                    null
                                },
                                onClick = { onCycleClick(cycle) },
                                onPlayClick = { onCyclePlay(cycle) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else if (isMusicSection && isLoadingCatalog) {
                item { LibraryLoading() }
            } else if (isMusicSection && music.isEmpty()) {
                item { EmptyLibrary(offline = offlineBanner) }
            } else if (isMusicSection && musicTrackList.isEmpty()) {
                item { EmptyLibrary(offline = offlineBanner || !isNetworkOnline) }
            } else if (isMusicSection) {
                item {
                    MusicWaveCard(
                        tracks = musicTrackList,
                        musicPlayback = musicPlayback,
                        isNetworkOnline = isNetworkOnline,
                        onClick = onMusicWavePlay,
                    )
                }
                item {
                    MusicDownloadAllButton(
                        tracks = musicTrackList,
                        musicDownload = musicDownload,
                        onClick = onDownloadAllMusic,
                    )
                }
                if (musicPlaybackErrorMessage != null) {
                    item {
                        Text(
                            text = musicPlaybackErrorMessage,
                            color = Color(0xFFF87171),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                item {
                    MusicAllTracksToggle(
                        count = musicTrackList.size,
                        expanded = showAllMusicTracks,
                        onClick = { showAllMusicTracks = !showAllMusicTracks },
                    )
                }
                if (showAllMusicTracks) {
                    items(musicTrackList, key = { it.trackId }) { track ->
                        val isActive = musicPlayback.trackId == track.trackId
                        val trackDownloadProgress = musicDownloadQueue.progressForTrack(track.trackId)
                        val isDownloading = trackDownloadProgress != null
                        val isQueued = musicDownloadQueue.isTrackQueued(track.trackId)
                        MusicTrackRow(
                            track = track,
                            isActive = isActive,
                            isQueued = isQueued,
                            isDownloading = isDownloading,
                            downloadProgress = trackDownloadProgress,
                            onClick = { onMusicTrackClick(track) },
                            onDownloadClick = { onDownloadMusicTrack(track) },
                            onDeleteClick = { onDeleteMusicTrack(track) },
                        )
                    }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TonezenChromeHeaderRowHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (isBooksSection) "Книги" else "Музыка",
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isBooksSection) {
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
    isDownloaded: Boolean,
    progressFraction: Float?,
    continueState: BookContinueState?,
    isPlaying: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayInset = 10.dp
    val showProgress = cycle.books.isNotEmpty()
    Box(modifier = modifier) {
        CycleCover(
            cycle = cycle,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clickable(onClick = onClick),
        )
        if (isDownloaded) {
            CheckCircleGlyph(
                tint = TonezenTeal,
                size = 18.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(overlayInset)
                    .zIndex(1f),
            )
        }
        if (continueState != null || showProgress) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = overlayInset, end = 48.dp, bottom = overlayInset)
                    .zIndex(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                continueState?.let { state ->
                    ContinueResumeMeta(
                        state = state,
                        variant = ContinueResumeVariant.Overlay,
                    )
                }
                if (showProgress) {
                    progressFraction?.let { progress ->
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } ?: Text(
                        text = "${0}%",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        CompactMediaPlayButton(
            isPlaying = isPlaying,
            downloadProgress = downloadProgress,
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .zIndex(2f),
        )
    }
}
