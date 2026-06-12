package com.tonezen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType

private val TonezenColorScheme = darkColorScheme(
    primary = TonezenTeal,
    onPrimary = TonezenAppBg,
    secondary = TonezenAmber,
    background = TonezenAppBg,
    onBackground = TonezenInk,
    surface = TonezenSurface,
    onSurface = TonezenInk,
    surfaceVariant = TonezenSurfaceRaised,
    onSurfaceVariant = TonezenMuted,
    error = TonezenError,
)

@Composable
internal fun TonezenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TonezenColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

internal val TonezenScreenBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF070B0F),
        TonezenAppBg,
        Color(0xFF071016),
    ),
)

internal fun trackCoverBrush(seed: String): Brush {
    val variants = listOf(
        listOf(Color(0xFF103344), Color(0xFF9BD6E3), Color(0xFF0F172A)),
        listOf(Color(0xFF1D1712), Color(0xFF70513A), Color(0xFF111827)),
        listOf(Color(0xFF0F3B39), Color(0xFF69B3A2), Color(0xFF10201F)),
        listOf(Color(0xFF2A1B3D), Color(0xFF7C5CBF), Color(0xFF1A1025)),
    )
    val index = kotlin.math.abs(seed.hashCode()) % variants.size
    return Brush.verticalGradient(variants[index])
}

internal fun coverBrush(book: Book): Brush {
    val variants = if (book.contentType == ContentType.AUDIOBOOK) {
        listOf(
            listOf(Color(0xFF061826), Color(0xFF102A43), Color(0xFF0B1120)),
            listOf(Color(0xFF33210E), Color(0xFFC78538), Color(0xFFFAECD2)),
            listOf(Color(0xFF461C12), Color(0xFFD94D28), Color(0xFF7F1D1D)),
        )
    } else {
        listOf(
            listOf(Color(0xFF103344), Color(0xFF9BD6E3), Color(0xFF0F172A)),
            listOf(Color(0xFF1D1712), Color(0xFF70513A), Color(0xFF111827)),
            listOf(Color(0xFF0F3B39), Color(0xFF69B3A2), Color(0xFF10201F)),
        )
    }
    val index = kotlin.math.abs(book.id.hashCode()) % variants.size
    return Brush.verticalGradient(variants[index])
}

internal fun durationLabel(durationMs: Long?): String {
    val totalSeconds = durationMs?.div(1000) ?: return "--:--"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
