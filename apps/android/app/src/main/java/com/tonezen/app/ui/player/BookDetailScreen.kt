package com.tonezen.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.ui.components.DownloadConfirmSheet
import com.tonezen.app.ui.components.OverflowGlyph
import com.tonezen.app.ui.components.PlayingBars
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.TrackActionsSheet
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (selected) TonezenAmber.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) TonezenAmber.copy(alpha = 0.18f) else TonezenBorder.copy(alpha = 0.35f),
                ),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayingBars(active = selected)
        Text(
            (track.sortOrder + 1).toString(),
            color = if (selected) TonezenAmber else TonezenMuted,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (selected) TonezenAmber else TonezenInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.localPath != null) {
                Text(
                    stringResource(R.string.offline),
                    color = TonezenTeal,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            durationLabel(track.durationMs),
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        OverflowGlyph(
            modifier = Modifier.clickable(onClick = onLongClick),
            tint = TonezenMuted,
        )
    }
}
