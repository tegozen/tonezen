package com.tonezen.app.ui.bookwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.BookWatch
import com.tonezen.app.domain.model.BookWatchQuery

@Composable
fun BookWatchSettingsDialog(
    watch: BookWatch,
    onDismiss: () -> Unit,
    onSave: (String, List<BookWatchQuery>) -> Unit,
    saving: Boolean = false,
    saveError: String? = null,
) {
    fun values(provider: String) = watch.queries.filter { it.provider == provider }.joinToString("\n") { it.query }
    var title by remember(watch.id) { mutableStateOf(watch.displayTitle) }
    var baza by remember(watch.id) { mutableStateOf(values("baza_knig")) }
    var allbookerka by remember(watch.id) { mutableStateOf(values("allbookerka")) }
    fun providerEnabled(provider: String): Boolean = watch.queries.any { it.provider == provider && it.enabled }
    var bazaEnabled by remember(watch.id) { mutableStateOf(providerEnabled("baza_knig")) }
    var allbookerkaEnabled by remember(watch.id) { mutableStateOf(providerEnabled("allbookerka")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отслеживание новинок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                saveError?.let { Text(it) }
                OutlinedTextField(title, { title = it }, label = { Text("Название цикла") })
                Row {
                    Text("Проверять baza-knig.top", modifier = androidx.compose.ui.Modifier.weight(1f))
                    Switch(bazaEnabled, { bazaEnabled = it })
                }
                OutlinedTextField(
                    baza,
                    { baza = it },
                    label = { Text("Названия для baza-knig.top") },
                    supportingText = { Text("Одно название на строку") },
                )
                Row {
                    Text("Проверять allbookerka.org", modifier = androidx.compose.ui.Modifier.weight(1f))
                    Switch(allbookerkaEnabled, { allbookerkaEnabled = it })
                }
                OutlinedTextField(
                    allbookerka,
                    { allbookerka = it },
                    label = { Text("Названия для allbookerka.org") },
                    supportingText = { Text("Одно название на строку") },
                )
                watch.lastSuccessAt?.let {
                    Text(
                        "Последняя успешная проверка: " +
                            java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && title.isNotBlank(),
                onClick = {
                    fun queries(provider: String, source: String, enabled: Boolean) = source.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .map { BookWatchQuery(provider, it, enabled) }
                        .toList()
                    onSave(
                        title.trim(),
                        queries("baza_knig", baza, bazaEnabled) +
                            queries("allbookerka", allbookerka, allbookerkaEnabled),
                    )
                },
            ) { Text(if (saving) "Сохранение…" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
