package com.tonezen.app.ui.bookwatch

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import com.tonezen.app.data.local.BookWatchEntity
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun BookWatchSettingsDialog(watch: BookWatchEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val initial = remember(watch.queriesJson) { JSONArray(watch.queriesJson) }
    fun values(provider: String) = buildList {
        for (index in 0 until initial.length()) initial.getJSONObject(index).takeIf { it.optString("provider") == provider }?.let { add(it.optString("query")) }
    }.joinToString("\n")
    var title by remember(watch.id) { mutableStateOf(watch.displayTitle) }
    var baza by remember(watch.id) { mutableStateOf(values("baza_knig")) }
    var allbookerka by remember(watch.id) { mutableStateOf(values("allbookerka")) }
    fun providerEnabled(provider: String): Boolean = (0 until initial.length()).any {
        initial.getJSONObject(it).optString("provider") == provider && initial.getJSONObject(it).optBoolean("enabled", true)
    }
    var bazaEnabled by remember(watch.id) { mutableStateOf(providerEnabled("baza_knig")) }
    var allbookerkaEnabled by remember(watch.id) { mutableStateOf(providerEnabled("allbookerka")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отслеживание новинок") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Название цикла") })
            Row { Text("Проверять baza-knig.top", modifier = androidx.compose.ui.Modifier.weight(1f)); Switch(bazaEnabled, { bazaEnabled = it }) }
            OutlinedTextField(baza, { baza = it }, label = { Text("Названия для baza-knig.top") }, supportingText = { Text("Одно название на строку") })
            Row { Text("Проверять allbookerka.org", modifier = androidx.compose.ui.Modifier.weight(1f)); Switch(allbookerkaEnabled, { allbookerkaEnabled = it }) }
            OutlinedTextField(allbookerka, { allbookerka = it }, label = { Text("Названия для allbookerka.org") }, supportingText = { Text("Одно название на строку") })
            watch.lastSuccessAt?.let { Text("Последняя успешная проверка: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}") }
        } },
        confirmButton = { TextButton(onClick = {
            val queries = JSONArray()
            fun add(provider: String, source: String, enabled: Boolean) = source.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach {
                queries.put(JSONObject().put("provider", provider).put("query", it).put("enabled", enabled))
            }
            add("baza_knig", baza, bazaEnabled); add("allbookerka", allbookerka, allbookerkaEnabled)
            onSave(title.trim(), queries.toString())
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
