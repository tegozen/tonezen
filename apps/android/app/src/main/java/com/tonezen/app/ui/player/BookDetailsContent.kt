package com.tonezen.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.ui.components.TonezenBackHeaderRow
import com.tonezen.app.ui.components.bookAuthorLabel
import com.tonezen.app.ui.components.ActionButton
import com.tonezen.app.ui.components.BookCover
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel
import com.tonezen.app.ui.theme.tonezenScreenContentPadding

@Composable
internal fun BookDetailsContent(
    padding: PaddingValues,
    book: Book,
    tracks: List<Track>,
    hasDownloadedTracks: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onSelectTab: (BookDetailTab) -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
) {
    val totalDuration = tracks.sumOf { it.durationMs ?: 0L }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TonezenScreenBrush)
            .padding(padding),
        contentPadding = tonezenScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TonezenBackHeaderRow(
                onBack = onBack,
                title = {
                    Text(
                        stringResource(R.string.details),
                        color = TonezenInk,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                trailing = {
                    TextButton(onClick = { onSelectTab(BookDetailTab.PLAYER) }) {
                        Text(stringResource(R.string.nav_player), color = TonezenTeal)
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(book = book, modifier = Modifier.weight(0.42f).aspectRatio(0.78f))
                Column(modifier = Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.title, color = TonezenInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(bookAuthorLabel(book), color = TonezenMuted)
                    Text(stringResource(R.string.duration_label) + ": " + durationLabel(totalDuration), color = TonezenMuted)
                    Text(stringResource(R.string.chapters) + ": ${tracks.size}", color = TonezenMuted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(label = stringResource(R.string.audiobook), tone = TonezenTeal)
                if (hasDownloadedTracks) {
                    StatusChip(label = stringResource(R.string.offline), tone = TonezenTeal)
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn(stringResource(R.string.duration_label), durationLabel(totalDuration))
                StatColumn(stringResource(R.string.chapters), tracks.size.toString())
            }
        }
        item {
            Text(stringResource(R.string.about_this_book), color = TonezenInk, fontWeight = FontWeight.SemiBold)
            Text(book.title, color = TonezenMuted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            ActionButton(label = stringResource(R.string.download), onClick = onDownload, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    label = if (isFavorite) stringResource(R.string.favorites) else stringResource(R.string.toggle_favorite),
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TonezenAmber, contentColor = TonezenAppBg),
                ) {
                    Text(stringResource(R.string.start_listening))
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TonezenInk, fontWeight = FontWeight.SemiBold)
        Text(label, color = TonezenMuted, style = MaterialTheme.typography.labelSmall)
    }
}
