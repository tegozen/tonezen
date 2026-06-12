package com.tonezen.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSheetBg
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingSheet(
    shellState: AppShellUiState,
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragOffsetAnimatable = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        viewModel.refreshCatalogContext()
    }

    fun settleDrag(velocityY: Float = 0f) {
        scope.launch {
            if (dragOffset >= dismissThresholdPx || velocityY > 1_200f) {
                onDismiss()
                dragOffset = 0f
                dragOffsetAnimatable.snapTo(0f)
            } else if (dragOffset > 0f) {
                val current = dragOffset
                dragOffset = 0f
                dragOffsetAnimatable.snapTo(current)
                dragOffsetAnimatable.animateTo(0f, spring())
            }
        }
    }

    val dismissNestedScroll = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || scrollState.value > 0) {
                    return Offset.Zero
                }
                dragOffset = (dragOffset + available.y).coerceAtLeast(0f)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y >= 0f || dragOffset <= 0f) return Offset.Zero
                val consumedY = available.y.coerceAtLeast(-dragOffset)
                dragOffset += consumedY
                return Offset(0f, consumedY)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset > 0f) {
                    settleDrag(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }

    val handleDraggableState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(onClick = onDismiss),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .offset {
                    IntOffset(
                        x = 0,
                        y = (dragOffsetAnimatable.value + dragOffset).roundToInt(),
                    )
                }
                .nestedScroll(dismissNestedScroll),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = TonezenSheetBg,
            shadowElevation = 20.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .draggable(
                            state = handleDraggableState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> settleDrag(velocity) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle(
                        color = TonezenMuted.copy(alpha = 0.65f),
                        width = 40.dp,
                    )
                }
                NowPlayingContent(
                    shellState = shellState,
                    viewModel = viewModel,
                    scrollState = scrollState,
                )
            }
        }
    }
}

@Composable
internal fun NowPlayingContent(
    shellState: AppShellUiState,
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
    val isAudiobook = state.contentType == ContentType.AUDIOBOOK
    val isDownloading = state.downloadProgress != null

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
