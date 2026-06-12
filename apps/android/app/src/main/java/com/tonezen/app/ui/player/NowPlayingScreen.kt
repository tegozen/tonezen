package com.tonezen.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingSheet(
    shellState: AppShellUiState,
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f

    LaunchedEffect(Unit) {
        viewModel.refreshCatalogContext()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(TonezenAppBg)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle(color = TonezenMuted.copy(alpha = 0.4f))
            }
            NowPlayingContent(
                shellState = shellState,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
internal fun NowPlayingContent(
    shellState: AppShellUiState,
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
    val isDownloading = state.downloadProgress != null
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TrackCoverArt(
                seed = coverSeed,
                title = title,
                isPlaying = state.isPlaying && !isDownloading,
                downloadProgress = state.downloadProgress,
                modifier = Modifier.size(168.dp),
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
                Row(modifier = Modifier.size(48.dp)) {}
            }
            RoundIconControl(
                outlined = true,
                enabled = !isDownloading,
                onClick = viewModel::skipPrevious,
            ) {
                SkipPreviousGlyph(
                    tint = if (isDownloading) TonezenMuted.copy(alpha = 0.38f) else TonezenInk,
                )
            }
            PlayButton(
                isPlaying = state.isPlaying && !isDownloading,
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
            if (isAudiobook) {
                RoundControl(label = stringResource(R.string.forward_15), outlined = true) {
                    viewModel.seekBy(15_000L)
                }
            } else {
                Row(modifier = Modifier.size(48.dp)) {}
            }
        }
    }
}
