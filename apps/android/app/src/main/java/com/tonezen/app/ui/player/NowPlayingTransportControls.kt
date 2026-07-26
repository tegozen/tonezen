package com.tonezen.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.components.RoundIconControl
import com.tonezen.app.ui.components.SkipNextGlyph
import com.tonezen.app.ui.components.SkipPreviousGlyph
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted

@Composable
internal fun NowPlayingTransportControls(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onSeekBy: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onPauseOrResume: () -> Unit,
) {
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
            onSeekBy(-15_000L)
        }
        RoundIconControl(
            outlined = true,
            enabled = canSkipPrevious && !isDownloading,
            onClick = onSkipPrevious,
        ) {
            SkipPreviousGlyph(
                tint = when {
                    isDownloading -> TonezenMuted.copy(alpha = 0.38f)
                    canSkipPrevious -> TonezenInk
                    else -> TonezenMuted.copy(alpha = 0.38f)
                },
            )
        }
        PlayButton(
            isPlaying = isPlaying && !isDownloading,
            downloadProgress = downloadProgress,
            modifier = Modifier.size(64.dp),
            onClick = onPauseOrResume,
        )
        RoundIconControl(
            outlined = true,
            enabled = canSkipNext && !isDownloading,
            onClick = onSkipNext,
        ) {
            SkipNextGlyph(
                tint = when {
                    isDownloading -> TonezenMuted.copy(alpha = 0.38f)
                    canSkipNext -> TonezenInk
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
            onSeekBy(15_000L)
        }
    }
}
