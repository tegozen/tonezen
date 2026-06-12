package com.tonezen.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.IconCircle
import com.tonezen.app.ui.components.OverflowGlyph
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.PlayingBars
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.components.QueueGlyph
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.TonezenBottomNavigation
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
    tracks: List<Track>,
    progressTrackTitle: String?,
    nowPlayingTitle: String?,
    isPlaying: Boolean,
    downloadProgress: Float?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDownload: () -> Unit,
    onDeleteLocal: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val hasDownloadedTracks = tracks.any { it.localPath != null }
    val currentTrackTitle = nowPlayingTitle ?: progressTrackTitle
    val selectedChapterIndex = tracks.indexOfFirst { it.title == currentTrackTitle }.takeIf { it >= 0 } ?: 0

    Scaffold(
        containerColor = TonezenAppBg,
        bottomBar = { TonezenBottomNavigation(selected = BottomDestination.Player) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(TonezenScreenBrush)
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                PlayerHeader(onBack = onBack)
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .aspectRatio(1f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = book.title,
                            color = TonezenInk,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = book.author.orEmpty(),
                            color = TonezenMuted,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (hasDownloadedTracks) {
                            StatusChip(label = stringResource(R.string.offline), tone = TonezenTeal)
                        }
                        if (book.contentType == ContentType.AUDIOBOOK) {
                            StatusChip(label = stringResource(R.string.synced), tone = TonezenGreen)
                        }
                    }
                    downloadProgress?.let {
                        Text(
                            text = stringResource(R.string.downloading_percent, (it * 100).toInt()),
                            color = TonezenAmber,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            item {
                PlayerControls(
                    chapterLabel = tracks.getOrNull(selectedChapterIndex)?.let {
                        stringResource(R.string.chapter_label, it.sortOrder + 1)
                    } ?: stringResource(R.string.chapter_fallback),
                    isPlaying = isPlaying,
                    canResume = nowPlayingTitle != null,
                    onPlay = onPlay,
                    onPause = onPause,
                    onResume = onResume,
                )
            }
            item {
                PlayerActions(
                    onDownload = onDownload,
                    onDeleteLocal = onDeleteLocal,
                    onToggleFavorite = onToggleFavorite,
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
                )
            }
        }
    }
}

@Composable
private fun PlayerHeader(onBack: () -> Unit) {
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
            SegmentPill(label = stringResource(R.string.nav_player), selected = true)
            SegmentPill(label = stringResource(R.string.details), selected = false)
        }
        IconCircle { OverflowGlyph() }
    }
}

@Composable
private fun SegmentPill(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
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
private fun PlayerControls(
    chapterLabel: String,
    isPlaying: Boolean,
    canResume: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueGlyph()
            RoundControl(label = stringResource(R.string.rewind_15), outlined = true, onClick = {})
            PlayButton(
                isPlaying = isPlaying,
                onClick = {
                    when {
                        isPlaying -> onPause()
                        canResume -> onResume()
                        else -> onPlay()
                    }
                },
            )
            RoundControl(label = stringResource(R.string.forward_15), outlined = true, onClick = {})
            Text(stringResource(R.string.speed_normal), color = TonezenInk, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = chapterLabel,
            color = TonezenInk,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        ProgressBar(progress = 0.42f)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("18:35", color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            Text("-21:40", color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlayerActions(onDownload: () -> Unit, onDeleteLocal: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(label = stringResource(R.string.download), onClick = onDownload, modifier = Modifier.weight(1f))
        ActionButton(label = stringResource(R.string.toggle_favorite), onClick = onToggleFavorite, modifier = Modifier.weight(1f))
        ActionButton(label = stringResource(R.string.delete_local_files), onClick = onDeleteLocal, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TonezenAmber.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) TonezenAmber.copy(alpha = 0.18f) else TonezenBorder.copy(alpha = 0.35f)),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayingBars(active = selected)
        Text(
            text = (track.sortOrder + 1).toString(),
            color = if (selected) TonezenAmber else TonezenMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (selected) TonezenAmber else TonezenInk,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.localPath != null) {
                Text(stringResource(R.string.offline), color = TonezenTeal, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(durationLabel(track.durationMs), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
    }
}
