package com.tonezen.app.ui.bookwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.BookWatchEvent
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookWatchScreen(
    events: List<BookWatchEvent>,
    hazeState: HazeState,
    padding: PaddingValues,
    bottomScrollPadding: Dp,
    onMarkAllRead: () -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("active") }
    val shown = events.filter {
        filter == "all" || filter == "errors" && it.kind == "provider_error" ||
            filter == it.status && it.kind == "book"
    }
    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        bottomScrollPadding = bottomScrollPadding,
        onBack = onBack,
        title = { Text("Новые книги", color = TonezenInk, fontWeight = FontWeight.SemiBold) },
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Новинки ваших циклов и результаты проверки сайтов.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TonezenMuted,
                )
                if (events.any { it.readAt == null }) {
                    TextButton(onClick = onMarkAllRead) { Text("Отметить всё прочитанным", color = TonezenTeal) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("active" to "Активные", "completed" to "Выполненные", "errors" to "Ошибки", "all" to "Все")
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = filter == key,
                                onClick = { filter = key },
                                label = { Text(label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = TonezenSurfaceRaised,
                                    labelColor = TonezenMuted,
                                    selectedContainerColor = TonezenTeal,
                                    selectedLabelColor = TonezenSurfaceRaised,
                                ),
                            )
                        }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                BookWatchEmptyState(filter)
            }
        }
        items(shown, key = { it.id }) { event -> BookWatchEventCard(event) }
    }
}
