package com.tonezen.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.R
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.components.RoundIconControl
import com.tonezen.app.ui.components.SkipNextGlyph
import com.tonezen.app.ui.components.SkipPreviousGlyph
import com.tonezen.app.ui.components.TrackCoverArt
import com.tonezen.app.ui.shell.AppShellUiState
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingSheet(
    shellState: AppShellUiState,
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TonezenAppBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TonezenMuted.copy(alpha = 0.4f)) },
    ) {
        NowPlayingContent(
            shellState = shellState,
            onDismiss = onDismiss,
            viewModel = viewModel,
            modifier = Modifier.fillMaxHeight(0.94f),
        )
    }
}

@Composable
internal fun NowPlayingContent(
    shellState: AppShellUiState,
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val title = state.title ?: shellState.nowPlayingTitle ?: return
    val subtitle = state.subtitle ?: shellState.nowPlayingSubtitle
    val coverSeed = state.coverSeed ?: shellState.nowPlayingCoverSeed ?: title
    val progress = if (state.durationMs > 0) {
        state.positionMs.toFloat() / state.durationMs
    } else {
        0f
    }
    val isAudiobook = state.contentType == ContentType.AUDIOBOOK

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(TonezenScreenBrush),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.now_playing),
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "⌄",
                    color = TonezenMuted,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(8.dp),
                )
            }
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TrackCoverArt(
                    seed = coverSeed,
                    title = title,
                    isPlaying = state.isPlaying,
                    modifier = Modifier.size(200.dp),
                    cornerRadius = 24,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        title,
                        color = TonezenInk,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            color = TonezenTeal,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressBar(
                    progress = progress,
                    onSeek = { fraction ->
                        if (state.durationMs > 0) {
                            viewModel.seekTo((state.durationMs * fraction).toLong())
                        }
                    },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        durationLabel(state.positionMs),
                        color = TonezenMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        durationLabel(state.durationMs),
                        color = TonezenMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isAudiobook) {
                    RoundControl(label = stringResource(R.string.rewind_15), outlined = true) {
                        viewModel.seekBy(-15_000L)
                    }
                } else {
                    Row(modifier = Modifier.size(42.dp)) {}
                }
                RoundIconControl(outlined = true, onClick = viewModel::skipPrevious) {
                    SkipPreviousGlyph(tint = if (state.canSkip) TonezenInk else TonezenMuted.copy(alpha = 0.4f))
                }
                PlayButton(isPlaying = state.isPlaying, onClick = viewModel::pauseOrResume)
                RoundIconControl(outlined = true, onClick = viewModel::skipNext) {
                    SkipNextGlyph(tint = if (state.canSkip) TonezenInk else TonezenMuted.copy(alpha = 0.4f))
                }
                if (isAudiobook) {
                    RoundControl(label = stringResource(R.string.forward_15), outlined = true) {
                        viewModel.seekBy(15_000L)
                    }
                } else {
                    Row(modifier = Modifier.size(42.dp)) {}
                }
            }
        }

        if (state.upNext.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.up_next),
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(state.upNext, key = { it.id }) { track ->
                UpNextRow(
                    track = track,
                    onClick = { viewModel.playTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun UpNextRow(
    track: com.tonezen.app.domain.model.Track,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            track.title,
            color = TonezenInk,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            durationLabel(track.durationMs),
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
