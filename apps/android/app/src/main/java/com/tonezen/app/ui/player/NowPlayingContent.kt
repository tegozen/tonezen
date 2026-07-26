package com.tonezen.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.music.SpectrumCoverArt
import com.tonezen.app.ui.shell.AppShellUiState
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

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

        NowPlayingTransportControls(
            isPlaying = state.isPlaying,
            canSkipPrevious = state.canSkipPrevious,
            canSkipNext = state.canSkipNext,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onSeekBy = viewModel::seekBy,
            onSkipPrevious = viewModel::skipPrevious,
            onSkipNext = viewModel::skipNext,
            onPauseOrResume = viewModel::pauseOrResume,
        )
    }
}
