package com.tonezen.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.components.RoundIconControl
import com.tonezen.app.ui.components.SkipNextGlyph
import com.tonezen.app.ui.components.SkipPreviousGlyph
import com.tonezen.app.ui.components.SpectrumCoverArt
import com.tonezen.app.ui.components.TonezenGlassModalBottomSheet
import com.tonezen.app.ui.components.WaveformProgressBar
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.ui.shell.AppShellUiState
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun NowPlayingSheet(
    visible: Boolean,
    hazeState: HazeState,
    shellState: AppShellUiState,
    musicDownload: MusicDownloadState,
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    LaunchedEffect(visible) {
        if (visible) {
            viewModel.refreshCatalogContext()
        }
    }

    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismiss,
    ) {
        NowPlayingContent(
            shellState = shellState,
            musicDownload = musicDownload,
            viewModel = viewModel,
        )
    }
}

@Composable
internal fun NowPlayingContent(
    shellState: AppShellUiState,
    musicDownload: MusicDownloadState,
    viewModel: NowPlayingViewModel = hiltViewModel(),
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
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
    val activeTrackId = state.coverSeed ?: shellState.nowPlayingCoverSeed
    val downloadProgress = activeTrackId?.let { musicDownload.progressForTrack(it) }
    val isDownloading = downloadProgress != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(start = 24.dp, end = 24.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SpectrumCoverArt(
                seed = coverSeed,
                isPlaying = state.isPlaying && !isDownloading,
                downloadProgress = downloadProgress,
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val seekToFraction: (Float) -> Unit = { fraction ->
                if (state.durationMs > 0) {
                    viewModel.seekTo((state.durationMs * fraction).toLong())
                }
            }
            val waveformPeaks = state.waveformPeaks
            if (waveformPeaks != null) {
                WaveformProgressBar(
                    progress = progress,
                    peaks = waveformPeaks,
                    onSeek = seekToFraction,
                )
            } else {
                ProgressBar(
                    progress = progress,
                    onSeek = seekToFraction,
                )
            }
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundControl(
                label = "-15",
                outlined = true,
                size = 40.dp,
                enabled = !isDownloading,
            ) {
                viewModel.seekBy(-15_000L)
            }
            RoundIconControl(
                outlined = true,
                enabled = state.canSkipPrevious && !isDownloading,
                onClick = viewModel::skipPrevious,
            ) {
                SkipPreviousGlyph(
                    tint = when {
                        isDownloading -> TonezenMuted.copy(alpha = 0.38f)
                        state.canSkipPrevious -> TonezenInk
                        else -> TonezenMuted.copy(alpha = 0.38f)
                    },
                )
            }
            PlayButton(
                isPlaying = state.isPlaying && !isDownloading,
                downloadProgress = downloadProgress,
                modifier = Modifier.size(64.dp),
                onClick = viewModel::pauseOrResume,
            )
            RoundIconControl(
                outlined = true,
                enabled = state.canSkipNext && !isDownloading,
                onClick = viewModel::skipNext,
            ) {
                SkipNextGlyph(
                    tint = when {
                        isDownloading -> TonezenMuted.copy(alpha = 0.38f)
                        state.canSkipNext -> TonezenInk
                        else -> TonezenMuted.copy(alpha = 0.38f)
                    },
                )
            }
            RoundControl(
                label = "+15",
                outlined = true,
                size = 40.dp,
                enabled = !isDownloading,
            ) {
                viewModel.seekBy(15_000L)
            }
        }
    }
}
