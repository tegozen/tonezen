package com.tonezen.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.shell.AppShellUiState
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenGreen
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

@Composable
internal fun NowPlayingScreen(
    padding: PaddingValues,
    shellState: AppShellUiState,
    libraryBooks: List<Book>,
    downloadedBookIds: Set<String>,
    favoriteBookIds: Set<String>,
    onOpenBook: (Book) -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onGoToLibrary: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val title = state.title ?: shellState.nowPlayingTitle

    if (title == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TonezenScreenBrush)
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.empty_player_title), color = TonezenInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.empty_player_body), color = TonezenMuted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            Button(onClick = onGoToLibrary, colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg)) {
                Text(stringResource(R.string.go_to_library))
            }
        }
        return
    }

    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(TonezenScreenBrush).padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.now_playing), color = TonezenInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                StatusChip(label = stringResource(R.string.offline), tone = TonezenTeal)
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TonezenSurfaceRaised, MaterialTheme.shapes.large)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, color = TonezenInk, fontWeight = FontWeight.SemiBold)
                Text(state.subtitle.orEmpty(), color = TonezenMuted)
                ProgressBar(progress = progress)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(durationLabel(state.positionMs), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
                    Text("-" + durationLabel((state.durationMs - state.positionMs).coerceAtLeast(0L)), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundControl(label = stringResource(R.string.rewind_15), outlined = true) { onSeekBy(-15_000L); viewModel.seekBy(-15_000L) }
                    RoundControl(label = "<", outlined = true) { viewModel.skipPrevious() }
                    PlayButton(isPlaying = state.isPlaying, onClick = { onPlayPause(); viewModel.pauseOrResume() })
                    RoundControl(label = ">", outlined = true) { viewModel.skipNext() }
                    RoundControl(label = stringResource(R.string.forward_15), outlined = true) { onSeekBy(15_000L); viewModel.seekBy(15_000L) }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.queue), state.queueCount.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.favorites), favoriteBookIds.size.toString(), Modifier.weight(1f))
                StatTile(stringResource(R.string.downloads), downloadedBookIds.size.toString(), Modifier.weight(1f))
                StatTile(
                    label = stringResource(R.string.synced),
                    value = if (state.hasSyncedAudiobooks) "✓" else "—",
                    modifier = Modifier.weight(1f),
                    tone = TonezenGreen,
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.up_next), color = TonezenInk, fontWeight = FontWeight.SemiBold)
            }
        }
        items(state.upNext) { track ->
            UpNextRow(track = track, book = state.activeBook, onOpenBook = onOpenBook)
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: androidx.compose.ui.graphics.Color = TonezenInk,
) {
    Column(
        modifier = modifier
            .background(TonezenSurfaceRaised, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = tone, fontWeight = FontWeight.Bold)
        Text(label, color = TonezenMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun UpNextRow(track: Track, book: Book?, onOpenBook: (Book) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = TonezenInk)
            Text(book?.author.orEmpty(), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(durationLabel(track.durationMs), color = TonezenMuted)
        book?.let { b ->
            Text(
                "≡",
                color = TonezenMuted,
                modifier = Modifier.padding(start = 8.dp).clickable { onOpenBook(b) },
            )
        }
    }
}
