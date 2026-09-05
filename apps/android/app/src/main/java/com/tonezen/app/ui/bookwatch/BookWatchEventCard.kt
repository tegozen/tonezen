package com.tonezen.app.ui.bookwatch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.BookWatchEvent
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookWatchEventCard(event: BookWatchEvent) {
    val uriHandler = LocalUriHandler.current
    BookWatchPanel {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                event.kind == "provider_error" -> StatusChip("Ошибка проверки", TonezenError)
                event.status == "completed" -> StatusChip("В каталоге", TonezenTeal)
                else -> StatusChip("Найдена новинка", TonezenTeal)
            }
            if (event.readAt == null) StatusChip("Не прочитано", TonezenInk)
        }
        Text(event.title, color = TonezenInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        event.author?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = TonezenMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date(event.firstSeenAt)),
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        if (event.links.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                event.links.forEach { link ->
                    TextButton(onClick = { uriHandler.openUri(link.url) }) {
                        Text(if (link.provider == "baza_knig") "База книг" else "Аудиокниги онлайн", color = TonezenTeal)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookWatchEmptyState(filter: String) {
    BookWatchPanel {
        Text(
            when (filter) {
                "completed" -> "Пока нет выполненных событий"
                "errors" -> "Нет сообщений об ошибках"
                "all" -> "Пока нет событий"
                else -> "Пока нет новых книг"
            },
            color = TonezenInk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when (filter) {
                "completed" -> "Здесь будут найденные новинки, которые уже появились в каталоге."
                "errors" -> "Если при проверке сайтов возникнет ошибка, она появится здесь."
                else -> "Когда проверка найдёт новые книги для ваших циклов, они появятся здесь. Названия для поиска можно изменить в меню цикла."
            },
            color = TonezenMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BookWatchPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TonezenSurfaceRaised,
        border = BorderStroke(1.dp, TonezenBorder),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}
