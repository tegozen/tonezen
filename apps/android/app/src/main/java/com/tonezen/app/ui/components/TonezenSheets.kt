package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.library.LibraryContentFilter
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.library.LibrarySortOrder
import com.tonezen.app.domain.model.Track
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterSheet(
    filter: LibraryFilterState,
    onDismiss: () -> Unit,
    onApply: (LibraryFilterState) -> Unit,
    onReset: () -> Unit,
    onContentFilterChange: (LibraryContentFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TonezenSurfaceRaised,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.search_filter_title), color = TonezenInk, fontWeight = FontWeight.SemiBold)
            FilterChipRow(
                label = stringResource(R.string.tab_audiobooks),
                selected = filter.contentFilter == LibraryContentFilter.AUDIOBOOKS,
                onClick = { onContentFilterChange(LibraryContentFilter.AUDIOBOOKS) },
            )
            FilterChipRow(
                label = stringResource(R.string.tab_music),
                selected = filter.contentFilter == LibraryContentFilter.MUSIC,
                onClick = { onContentFilterChange(LibraryContentFilter.MUSIC) },
            )
            FilterChipRow(
                label = stringResource(R.string.filter_downloaded),
                selected = filter.contentFilter == LibraryContentFilter.DOWNLOADED,
                onClick = { onContentFilterChange(LibraryContentFilter.DOWNLOADED) },
            )
            FilterChipRow(
                label = stringResource(R.string.favorites),
                selected = filter.contentFilter == LibraryContentFilter.FAVORITES,
                onClick = { onContentFilterChange(LibraryContentFilter.FAVORITES) },
            )
            Text(stringResource(R.string.sort_by), color = TonezenMuted)
            FilterChipRow(
                label = stringResource(R.string.sort_recently_played),
                selected = filter.sortOrder == LibrarySortOrder.RECENTLY_PLAYED,
                onClick = { onSortOrderChange(LibrarySortOrder.RECENTLY_PLAYED) },
            )
            FilterChipRow(
                label = stringResource(R.string.sort_title),
                selected = filter.sortOrder == LibrarySortOrder.TITLE,
                onClick = { onSortOrderChange(LibrarySortOrder.TITLE) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reset))
                }
                Button(
                    onClick = { onApply(filter) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
                ) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) TonezenTeal.copy(alpha = 0.15f) else TonezenSurfaceRaised,
                RoundedCornerShape(12.dp),
            )
            .border(
                BorderStroke(1.dp, if (selected) TonezenTeal else TonezenBorder),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (selected) TonezenTeal else TonezenInk,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadConfirmSheet(
    estimatedBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var includeAudio by remember { mutableStateOf(true) }
    var includeCover by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = TonezenSurfaceRaised) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.download_confirm_title), color = TonezenInk, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.download_confirm_body, formatMegabytes(estimatedBytes)), color = TonezenMuted)
            ToggleRow(stringResource(R.string.download_audio_files), includeAudio) { includeAudio = it }
            ToggleRow(stringResource(R.string.download_cover_art), includeCover) { includeCover = it }
            Button(
                onClick = onConfirm,
                enabled = includeAudio,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
            ) {
                Text(stringResource(R.string.download_offline))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel), color = TonezenMuted)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TonezenInk)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = TonezenTeal),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackActionsSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onMarkComplete: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = TonezenSurfaceRaised) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(track.title, color = TonezenInk, fontWeight = FontWeight.SemiBold)
            ActionRow(stringResource(R.string.play_next), onPlayNext)
            ActionRow(stringResource(R.string.mark_complete), onMarkComplete)
            ActionRow(stringResource(R.string.remove_download), onRemoveDownload, destructive = true)
            ActionRow(stringResource(R.string.share), onDismiss)
        }
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        color = if (destructive) TonezenError else TonezenInk,
    )
}

@Composable
internal fun SignOutConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_out_confirm_title)) },
        text = { Text(stringResource(R.string.sign_out_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sign_out), color = TonezenError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun OfflineSyncDialog(onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_paused_title)) },
        text = { Text(stringResource(R.string.sync_paused_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.keep_listening), color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        },
    )
}

private fun formatMegabytes(bytes: Long): String = "%.0f MB".format(bytes / (1024.0 * 1024.0))
