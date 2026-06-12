package com.tonezen.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.ui.components.ActionButton
import com.tonezen.app.ui.components.BookCover
import com.tonezen.app.ui.components.DownloadConfirmSheet
import com.tonezen.app.ui.components.IconCircle
import com.tonezen.app.ui.components.OverflowGlyph
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.PlayingBars
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.components.QueueGlyph
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.TrackActionsSheet
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenGreen
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

@Composable
internal fun BookDetailScreen(
    book: Book,
    uiState: BookDetailUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDownload: () -> Unit,
    onConfirmDownload: () -> Unit,
    onDismissDownloadSheet: () -> Unit,
    onDeleteLocal: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectTab: (BookDetailTab) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onShowTrackActions: (Track) -> Unit,
    onDismissTrackActions: () -> Unit,
    onMarkComplete: () -> Unit,
    onPlayNext: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val tracks = uiState.tracks
    val hasDownloadedTracks = tracks.any { it.localPath != null }
    val currentTrackTitle = uiState.nowPlayingTitle ?: uiState.progressTrackTitle
    val selectedChapterIndex = tracks.indexOfFirst { it.title == currentTrackTitle }.takeIf { it >= 0 } ?: 0
    val progress = if (uiState.durationMs > 0) {
        uiState.positionMs.toFloat() / uiState.durationMs.toFloat()
    } else {
        0f
    }

    if (uiState.showDownloadSheet) {
        DownloadConfirmSheet(
            estimatedBytes = uiState.estimatedDownloadBytes,
            onDismiss = onDismissDownloadSheet,
            onConfirm = onConfirmDownload,
        )
    }
    uiState.actionTrack?.let { track ->
        if (uiState.showTrackActions) {
            TrackActionsSheet(
                track = track,
                onDismiss = onDismissTrackActions,
                onPlayNext = onPlayNext,
                onMarkComplete = onMarkComplete,
                onRemoveDownload = onRemoveDownload,
            )
        }
    }

    Scaffold(containerColor = TonezenAppBg) { padding ->
        if (uiState.selectedTab == BookDetailTab.DETAILS) {
            BookDetailsContent(
                padding = padding,
                book = book,
                tracks = tracks,
                hasDownloadedTracks = hasDownloadedTracks,
                isFavorite = uiState.isFavorite,
                onBack = onBack,
                onSelectTab = onSelectTab,
                onDownload = onDownload,
                onToggleFavorite = onToggleFavorite,
                onPlay = onPlay,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TonezenScreenBrush)
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, top = 22.dp, end = 20.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    PlayerHeader(
                        onBack = onBack,
                        selectedTab = uiState.selectedTab,
                        onSelectTab = onSelectTab,
                    )
                }
                item {
                    PlayerHero(
                        book = book,
                        hasDownloadedTracks = hasDownloadedTracks,
                        syncStatus = uiState.syncStatus,
                        downloadProgress = uiState.downloadProgress,
                    )
                }
                item {
                    PlayerControls(
                        chapterLabel = tracks.getOrNull(selectedChapterIndex)?.let {
                            stringResource(R.string.chapter_label, it.sortOrder + 1)
                        } ?: stringResource(R.string.chapter_fallback),
                        isPlaying = uiState.isPlaying,
                        canResume = uiState.nowPlayingTitle != null,
                        progress = progress,
                        positionMs = uiState.positionMs,
                        durationMs = uiState.durationMs,
                        playbackSpeed = uiState.playbackSpeed,
                        onPlay = onPlay,
                        onPause = onPause,
                        onResume = onResume,
                        onSeekBy = onSeekBy,
                        onCycleSpeed = onCycleSpeed,
                        onSeek = onSeek,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.tracks),
                        color = TonezenInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(tracks) { track ->
                    TrackRow(
                        track = track,
                        selected = track.title == currentTrackTitle || track.sortOrder == selectedChapterIndex,
                        onClick = { onTrackClick(track) },
                        onLongClick = { onShowTrackActions(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerHeader(
    onBack: () -> Unit,
    selectedTab: BookDetailTab,
    onSelectTab: (BookDetailTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back), color = TonezenInk)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(TonezenSurfaceRaised.copy(alpha = 0.85f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp)),
        ) {
            SegmentPill(
                label = stringResource(R.string.nav_player),
                selected = selectedTab == BookDetailTab.PLAYER,
                onClick = { onSelectTab(BookDetailTab.PLAYER) },
            )
            SegmentPill(
                label = stringResource(R.string.details),
                selected = selectedTab == BookDetailTab.DETAILS,
                onClick = { onSelectTab(BookDetailTab.DETAILS) },
            )
        }
        IconCircle { OverflowGlyph() }
    }
}

@Composable
private fun SegmentPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (selected) TonezenTeal.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) TonezenTeal else TonezenMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PlayerHero(
    book: Book,
    hasDownloadedTracks: Boolean,
    syncStatus: SyncDisplayStatus,
    downloadProgress: Float?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BookCover(
            book = book,
            modifier = Modifier.fillMaxWidth(0.68f).aspectRatio(1f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, color = TonezenInk, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(book.author.orEmpty(), color = TonezenMuted, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasDownloadedTracks) {
                StatusChip(label = stringResource(R.string.offline), tone = TonezenTeal)
            }
            when (syncStatus) {
                SyncDisplayStatus.SYNCED -> StatusChip(label = stringResource(R.string.synced), tone = TonezenGreen)
                SyncDisplayStatus.PENDING -> StatusChip(label = stringResource(R.string.pending), tone = TonezenAmber)
                SyncDisplayStatus.NONE -> Unit
            }
        }
        downloadProgress?.let {
            Text(
                stringResource(R.string.downloading_percent, (it * 100).toInt()),
                color = TonezenAmber,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    chapterLabel: String,
    isPlaying: Boolean,
    canResume: Boolean,
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueGlyph()
            RoundControl(label = stringResource(R.string.rewind_15), outlined = true) { onSeekBy(-15_000L) }
            PlayButton(isPlaying = isPlaying) {
                when {
                    isPlaying -> onPause()
                    canResume -> onResume()
                    else -> onPlay()
                }
            }
            RoundControl(label = stringResource(R.string.forward_15), outlined = true) { onSeekBy(15_000L) }
            Text(
                stringResource(R.string.speed_format, playbackSpeed),
                color = TonezenInk,
                modifier = Modifier.clickable(onClick = onCycleSpeed),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(chapterLabel, color = TonezenInk, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
        ProgressBar(progress = progress, onSeek = { fraction -> onSeek((durationMs * fraction).toLong()) })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(durationLabel(positionMs), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            Text("-" + durationLabel((durationMs - positionMs).coerceAtLeast(0L)), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (selected) TonezenAmber.copy(alpha = 0.08f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) TonezenAmber.copy(alpha = 0.18f) else TonezenBorder.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayingBars(active = selected)
        Text((track.sortOrder + 1).toString(), color = if (selected) TonezenAmber else TonezenMuted)
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = if (selected) TonezenAmber else TonezenInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.localPath != null) {
                Text(stringResource(R.string.offline), color = TonezenTeal, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(durationLabel(track.durationMs), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
        OverflowGlyph(
            modifier = Modifier.clickable(onClick = onLongClick),
            tint = TonezenMuted,
        )
    }
}
