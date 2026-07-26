package com.tonezen.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.Track
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.durationLabel

@Composable
internal fun BookDetailPlaybackControls(
    track: Track,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    val progress = if (durationMs > 0L) {
        positionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = track.title,
            color = TonezenInk,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        ProgressBar(
            progress = progress,
            onSeek = onSeekToFraction,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                durationLabel(positionMs),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                durationLabel(durationMs),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
            )
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
            ) {
                onSeekBy(-15_000L)
            }
            PlayButton(
                isPlaying = isPlaying,
                modifier = Modifier.size(56.dp),
                onClick = onPlayPause,
            )
            RoundControl(
                label = "+15",
                outlined = true,
                size = 40.dp,
            ) {
                onSeekBy(15_000L)
            }
        }
    }
}
