package com.tonezen.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.progress.BookContinueState
import com.tonezen.app.ui.components.CheckCircleGlyph
import com.tonezen.app.ui.components.CompactMediaPlayButton
import com.tonezen.app.ui.components.ContinueResumeMeta
import com.tonezen.app.ui.components.ContinueResumeVariant
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun LibraryCycleCard(
    cycle: Cycle,
    isDownloaded: Boolean,
    progressFraction: Float?,
    continueState: BookContinueState?,
    isPlaying: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayInset = 10.dp
    val showProgress = cycle.books.isNotEmpty()
    Box(modifier = modifier) {
        CycleCover(
            cycle = cycle,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clickable(onClick = onClick),
        )
        if (isDownloaded) {
            CheckCircleGlyph(
                tint = TonezenTeal,
                size = 18.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(overlayInset)
                    .zIndex(1f),
            )
        }
        if (continueState != null || showProgress) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = overlayInset, end = 48.dp, bottom = overlayInset)
                    .zIndex(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                continueState?.let { state ->
                    ContinueResumeMeta(
                        state = state,
                        variant = ContinueResumeVariant.Overlay,
                    )
                }
                if (showProgress) {
                    progressFraction?.let { progress ->
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } ?: Text(
                        text = "${0}%",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        CompactMediaPlayButton(
            isPlaying = isPlaying,
            downloadProgress = downloadProgress,
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .zIndex(2f),
        )
    }
}
