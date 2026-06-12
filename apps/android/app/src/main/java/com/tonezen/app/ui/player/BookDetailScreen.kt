package com.tonezen.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.ui.components.DownloadConfirmSheet
import com.tonezen.app.ui.components.OverflowGlyph
import com.tonezen.app.ui.components.PlayingBars
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackActionsSheet
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState

@Composable
internal fun BookDetailScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    @Suppress("UNUSED_PARAMETER") book: Book,
    uiState: BookDetailUiState,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onConfirmDownload: () -> Unit,
    onDismissDownloadSheet: () -> Unit,
    onShowTrackActions: (Track) -> Unit,
    onDismissTrackActions: () -> Unit,
    onMarkComplete: () -> Unit,
    onPlayNext: () -> Unit,
    onRemoveDownload: () -> Unit,
    bottomScrollPadding: Dp,
) {
    val tracks = uiState.tracks
    val activeTrackId = uiState.activeTrackId

    BackHandler {
        when {
            uiState.showTrackActions -> onDismissTrackActions()
            uiState.showDownloadSheet -> onDismissDownloadSheet()
            else -> onBack()
        }
    }

    DownloadConfirmSheet(
        visible = uiState.showDownloadSheet,
        hazeState = hazeState,
        estimatedBytes = uiState.estimatedDownloadBytes,
        onDismiss = onDismissDownloadSheet,
        onConfirm = onConfirmDownload,
    )
    TrackActionsSheet(
        visible = uiState.showTrackActions,
        hazeState = hazeState,
        track = uiState.actionTrack,
        onDismiss = onDismissTrackActions,
        onPlayNext = onPlayNext,
        onMarkComplete = onMarkComplete,
        onRemoveDownload = onRemoveDownload,
    )

    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        bottomScrollPadding = bottomScrollPadding,
        title = {
            Text(
                text = stringResource(R.string.chapters),
                color = TonezenInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        items(tracks, key = { it.id }) { track ->
            ChapterRow(
                track = track,
                selected = track.id == activeTrackId,
                onClick = { onTrackClick(track) },
                onLongClick = { onShowTrackActions(track) },
            )
        }
    }
}

@Composable
private fun ChapterRow(
    track: Track,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    TonezenTrackListRow(
        title = track.title,
        subtitle = track.localPath?.let { stringResource(R.string.offline) },
        subtitleColor = TonezenTeal,
        durationMs = track.durationMs,
        isActive = selected,
        onClick = onClick,
        leading = {
            PlayingBars(active = selected)
            Text(
                (track.sortOrder + 1).toString(),
                color = if (selected) TonezenAmber else TonezenMuted,
            )
        },
        trailing = {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                OverflowGlyph(
                    modifier = Modifier.clickable(onClick = onLongClick),
                    tint = TonezenMuted,
                )
            }
        },
    )
}
