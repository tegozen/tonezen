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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tonezen.app.domain.library.LibraryFilterState
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
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenChromeHeaderRowHeight
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.TonezenTopChromeOfflineBannerExtra
import com.tonezen.app.ui.theme.TonezenTopChromeScrollPaddingBooks
import com.tonezen.app.ui.theme.tonezenScreenContentPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
internal fun LibraryScreen(
    hazeState: HazeState,
    cycles: List<Cycle>,
    allCycles: List<Cycle>,
    cycleCardStateById: Map<String, CycleCardState>,
    cyclePlayback: CyclePlaybackUi,
    offlineBanner: Boolean,
    isLoadingCatalog: Boolean,
    filter: LibraryFilterState,
    showFilterSheet: Boolean,
    onCycleClick: (Cycle) -> Unit,
    onCyclePlay: (Cycle) -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onApplyFilter: (LibraryFilterState) -> Unit,
    onResetFilter: () -> Unit,
    onContentFilterChange: (com.tonezen.app.domain.library.LibraryContentFilter) -> Unit,
    onSortOrderChange: (com.tonezen.app.domain.library.LibrarySortOrder) -> Unit,
    cyclePlaybackErrorMessage: String?,
    onDismissCyclePlaybackError: () -> Unit,
    showMiniPlayer: Boolean,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(cyclePlaybackErrorMessage) {
        cyclePlaybackErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissCyclePlaybackError()
        }
    }

    val topChromeScrollPadding = remember(offlineBanner) {
        val base = TonezenTopChromeScrollPaddingBooks
        if (offlineBanner) base + TonezenTopChromeOfflineBannerExtra else base
    }
    val bottomChromeScrollPadding = tonezenBottomChromeScrollPadding(
        showMiniPlayer = showMiniPlayer,
        showBottomNav = true,
    )

    BackHandler(enabled = showFilterSheet, onBack = onDismissFilterSheet)

    LibraryFilterSheet(
        visible = showFilterSheet,
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
            if (isLoadingCatalog) {
                item { LibraryLoading() }
            } else if (allCycles.isEmpty()) {
                item { EmptyLibrary(offline = offlineBanner) }
            } else {
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
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomChromeScrollPadding),
        )
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
                    text = "Книги",
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SearchRow(
                query = filter.query,
                onQueryChange = onSearchChange,
                onFilterClick = onFilterClick,
            )
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
