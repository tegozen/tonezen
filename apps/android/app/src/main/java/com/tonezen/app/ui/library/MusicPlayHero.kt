package com.tonezen.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tonezen.app.R
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.SyncGlyph
import com.tonezen.app.ui.components.TrackCoverArt
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun MusicPlayHero(
    preview: MusicTrackPreview?,
    playback: MusicPlaybackUi,
    downloadProgress: Float?,
    playbackErrorRes: Int?,
    onPlayPause: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (preview == null) return

    val showingActive = playback.isActive && playback.trackTitle != null
    val trackTitle = if (showingActive) playback.trackTitle.orEmpty() else preview.trackTitle
    val artist = if (showingActive) playback.artist.orEmpty() else preview.artist
    val albumTitle = if (showingActive) playback.albumTitle.orEmpty() else preview.albumTitle
    val coverSeed = if (showingActive) playback.bookId ?: preview.bookId else preview.bookId
    val isDownloading = downloadProgress != null
    val isPlaying = !isDownloading && playback.isPlaying && (
        showingActive || playback.trackId == preview.trackId
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TonezenSurfaceRaised,
                            Color(0xFF0B1220),
                            Color(0xFF071018),
                        ),
                    ),
                )
                .padding(24.dp),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = TonezenTeal.copy(alpha = 0.06f),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = size.minDimension * 0.45f,
                    center = Offset(size.width * 0.05f, size.height * 0.95f),
                )
            }

            if (!showingActive && !isDownloading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
                        .clickable(onClick = onShuffle),
                    contentAlignment = Alignment.Center,
                ) {
                    SyncGlyph(tint = TonezenMuted, size = 18.dp)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    TrackCoverArt(
                        seed = coverSeed,
                        title = trackTitle,
                        isPlaying = isPlaying,
                        modifier = Modifier.size(168.dp),
                        cornerRadius = 20,
                    )
                    PlayButton(
                        isPlaying = isPlaying,
                        downloadProgress = downloadProgress,
                        modifier = Modifier.size(72.dp),
                        onClick = onPlayPause,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = trackTitle,
                        color = TonezenInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            color = TonezenTeal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (albumTitle.isNotBlank()) {
                        Text(
                            text = albumTitle,
                            color = TonezenMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (playbackErrorRes != null) {
                        Text(
                            text = stringResource(playbackErrorRes),
                            color = Color(0xFFF87171),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
