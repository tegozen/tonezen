package com.tonezen.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.progress.BookContinueState
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

enum class ContinueResumeVariant {
    Overlay,
    Inline,
    Button,
}

@Composable
fun ContinueResumeMeta(
    state: BookContinueState,
    variant: ContinueResumeVariant = ContinueResumeVariant.Overlay,
    modifier: Modifier = Modifier,
) {
    val spacing = when (variant) {
        ContinueResumeVariant.Overlay -> 2.dp
        ContinueResumeVariant.Inline -> 2.dp
        ContinueResumeVariant.Button -> 2.dp
    }
    val labelColor = when (variant) {
        ContinueResumeVariant.Overlay -> TonezenAmber
        ContinueResumeVariant.Inline -> TonezenAmber
        ContinueResumeVariant.Button -> MaterialTheme.colorScheme.onPrimary
    }
    val chapterColor = when (variant) {
        ContinueResumeVariant.Overlay -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f)
        ContinueResumeVariant.Inline -> TonezenInk
        ContinueResumeVariant.Button -> MaterialTheme.colorScheme.onPrimary
    }
    val timeColor = when (variant) {
        ContinueResumeVariant.Overlay -> TonezenTeal
        ContinueResumeVariant.Inline -> TonezenTeal
        ContinueResumeVariant.Button -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    }
    val labelStyle = when (variant) {
        ContinueResumeVariant.Overlay -> MaterialTheme.typography.labelSmall
        ContinueResumeVariant.Inline -> MaterialTheme.typography.labelMedium
        ContinueResumeVariant.Button -> MaterialTheme.typography.bodyMedium
    }
    val chapterStyle = when (variant) {
        ContinueResumeVariant.Overlay -> MaterialTheme.typography.labelSmall
        ContinueResumeVariant.Inline -> MaterialTheme.typography.bodyMedium
        ContinueResumeVariant.Button -> MaterialTheme.typography.titleMedium
    }
    val timeStyle = when (variant) {
        ContinueResumeVariant.Overlay -> MaterialTheme.typography.labelSmall
        ContinueResumeVariant.Inline -> MaterialTheme.typography.labelMedium
        ContinueResumeVariant.Button -> MaterialTheme.typography.bodyMedium
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Text(
            text = "Продолжить",
            color = labelColor,
            style = labelStyle,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.trackTitle,
            color = chapterColor,
            style = chapterStyle,
            fontWeight = if (variant == ContinueResumeVariant.Button) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = durationLabel(state.positionMs),
            color = timeColor,
            style = timeStyle,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
