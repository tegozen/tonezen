package com.tonezen.app.ui.bookwatch

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import org.json.JSONArray

@Composable
fun BookWatchScreen(viewModel: BookWatchViewModel, onBack: () -> Unit) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("active") }
    val shown = events.filter {
        filter == "all" || filter == "errors" && it.kind == "provider_error" ||
            filter == it.status && it.kind == "book"
    }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Назад") }
            Text("Новые книги", style = MaterialTheme.typography.titleLarge, color = TonezenInk)
            TextButton(onClick = viewModel::markAllRead) { Text("Прочитано") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("active" to "Активные", "completed" to "Выполненные", "errors" to "Ошибки", "all" to "Все").forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { event ->
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(event.title, color = if (event.kind == "provider_error") TonezenError else TonezenInk)
                    event.author?.let { Text(it, color = TonezenMuted) }
                    StatusChip(if (event.status == "completed") "Выполнено" else "Новое", if (event.status == "completed") TonezenTeal else TonezenMuted)
                    val links = JSONArray(event.linksJson)
                    Row {
                        for (index in 0 until links.length()) {
                            val link = links.getJSONObject(index)
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.getString("url"))))
                            }) { Text(if (link.getString("provider") == "baza_knig") "Baza Knig" else "Allbookerka") }
                        }
                    }
                }
            }
        }
    }
}
