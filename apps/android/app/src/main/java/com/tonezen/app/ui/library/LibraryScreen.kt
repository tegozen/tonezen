package com.tonezen.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.ui.components.EmptyLibrary
import com.tonezen.app.ui.components.LibraryLoading
import com.tonezen.app.ui.components.OfflineBanner
import com.tonezen.app.ui.components.SearchRow
import com.tonezen.app.ui.components.TonezenTopChromeBar
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenChromeHeaderRowHeight
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenSurface
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
    val cycleRows = remember(cycles) { cycles.chunked(2) }

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
                items(
                    items = cycleRows,
                    key = { row -> row.joinToString(separator = "|") { it.id } },
                ) { row ->
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
