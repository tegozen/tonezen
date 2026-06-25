package com.tonezen.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.playback.DownloadQueueItem
import com.tonezen.app.playback.DownloadQueueItemStatus
import com.tonezen.app.ui.components.EmptyLibrary
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackDownloadButton
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.tonezenScreenContentPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
fun DownloadsTabScreen(
    hazeState: HazeState,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    offlineBanner: Boolean,
    viewModel: DownloadsTabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isEmpty = state.activeItems.isEmpty() && state.completedItems.isEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .haze(state = hazeState)
            .background(TonezenSurface),
        contentPadding = tonezenScreenContentPadding(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.pausedForNetwork) {
            item {
                Text(
                    text = "Ожидание сети",
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (state.activeItems.isNotEmpty()) {
            item {
                DownloadsSectionHeader(
                    title = "Сейчас",
                    actionLabel = "Остановить все загрузки",
                    onAction = viewModel::cancelAll,
                )
            }
            items(state.activeItems, key = { "${it.bookId}:${it.trackId}" }) { item ->
                DownloadsActiveRow(
                    item = item,
                    onCancel = { viewModel.cancelTrack(item.bookId, item.trackId) },
                )
            }
        }
        if (state.completedItems.isNotEmpty()) {
            item {
                DownloadsSectionHeader(
                    title = "Загружено",
                    actionLabel = null,
                    onAction = null,
                )
            }
            items(state.completedItems, key = { "${it.bookId}:${it.trackId}" }) { item ->
                DownloadsCompletedRow(
                    item = item,
                    onDelete = { viewModel.deleteCompleted(item.bookId, item.trackId) },
                )
            }
        }
        if (isEmpty) {
            item { EmptyLibrary(offline = offlineBanner) }
            item {
                Text(
                    text = "Нет загрузок",
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadsSectionHeader(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = TonezenMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun DownloadsActiveRow(
    item: DownloadQueueItem,
    onCancel: () -> Unit,
) {
    val statusLabel = when (item.status) {
        DownloadQueueItemStatus.PAUSED_OFFLINE -> "Ожидание сети"
        DownloadQueueItemStatus.QUEUED -> "В очереди"
        else -> null
    }
    TonezenTrackListRow(
        title = item.title,
        subtitle = item.subtitle ?: statusLabel,
        durationMs = null,
        isActive = item.status == DownloadQueueItemStatus.DOWNLOADING,
        onClick = {},
        clickEnabled = false,
        trailing = {
            TrackDownloadButton(
                progress = item.progress,
                onClick = onCancel,
                enabled = true,
            )
        },
    )
}

@Composable
private fun DownloadsCompletedRow(
    item: DownloadListItem,
    onDelete: () -> Unit,
) {
    TonezenTrackListRow(
        title = item.title,
        subtitle = item.subtitle,
        durationMs = item.durationMs,
        isActive = false,
        onClick = {},
        clickEnabled = false,
        trailing = {
            TextButton(onClick = onDelete) {
                Text(text = "Удалить загрузку")
            }
        },
    )
}
