package com.tonezen.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.downloads.DownloadedBookSummary
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.ui.components.BookCover
import com.tonezen.app.ui.components.TonezenTabs
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun DownloadsScreen(
    padding: PaddingValues,
    viewModel: DownloadsViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val tabSummaries = if (state.selectedTab == 0) {
        state.summaries.filter { it.contentType == "audiobook" }
    } else {
        state.summaries.filter { it.contentType == "music" }
    }

    BackHandler(enabled = state.showDeleteAllConfirm) {
        viewModel.showDeleteAllConfirm(false)
    }

    if (state.showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteAllConfirm(false) },
            title = { Text(stringResource(R.string.delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.delete_all_confirm_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::deleteAll) {
                    Text(stringResource(R.string.delete_all), color = TonezenError)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteAllConfirm(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(TonezenScreenBrush).padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.downloads), color = TonezenInk, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            TonezenTabs(selectedTab = state.selectedTab, onSelect = viewModel::selectTab)
        }
        item {
            StorageSummary(
                usedBytes = state.storageStats.usedBytes,
                percent = state.storageStats.usedPercent,
            )
        }
        items(tabSummaries) { summary ->
            DownloadRow(summary = summary)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { }, enabled = false, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pause_all), color = TonezenMuted)
                }
                OutlinedButton(onClick = { viewModel.showDeleteAllConfirm(true) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.delete_all), color = TonezenError)
                }
            }
        }
    }
}

@Composable
private fun StorageSummary(usedBytes: Long, percent: Float?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.storage_saved, formatGb(usedBytes)),
            color = TonezenInk,
            fontWeight = FontWeight.SemiBold,
        )
        percent?.let {
            Text(stringResource(R.string.storage_percent, (it * 100).toInt()), color = TonezenMuted)
        }
    }
}

@Composable
private fun DownloadRow(summary: DownloadedBookSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val book = com.tonezen.app.domain.model.Book(
            id = summary.bookId,
            slug = summary.bookId,
            contentType = if (summary.contentType == "music") ContentType.MUSIC else ContentType.AUDIOBOOK,
            title = summary.title,
            author = summary.author,
        )
        BookCover(book = book, modifier = Modifier.size(56.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.title, color = TonezenInk, fontWeight = FontWeight.SemiBold)
            Text(summary.author.orEmpty(), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            Text("${summary.downloadedTracks}/${summary.totalTracks}", color = TonezenMuted, style = MaterialTheme.typography.labelSmall)
        }
        val progress = summary.downloadProgress ?: 1f
        Text(
            "${(progress * 100).toInt()}%",
            color = if (progress >= 1f) TonezenTeal else TonezenAmber,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(TonezenMuted.copy(alpha = 0.12f))
                .padding(12.dp),
        )
    }
}

private fun formatGb(bytes: Long): String = "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
