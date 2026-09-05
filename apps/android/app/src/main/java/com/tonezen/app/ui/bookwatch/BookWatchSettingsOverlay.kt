package com.tonezen.app.ui.bookwatch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BookWatchSettingsOverlay(
    cycleId: String,
    cycleTitle: String,
    viewModel: BookWatchViewModel,
    onDismiss: () -> Unit,
) {
    val watch = remember(cycleId) { viewModel.settingsFor(cycleId, cycleTitle) }
    val scope = rememberCoroutineScope()
    var saving by remember(cycleId) { mutableStateOf(false) }
    var saveError by remember(cycleId) { mutableStateOf<String?>(null) }

    BookWatchSettingsDialog(
        watch = watch,
        onDismiss = onDismiss,
        saving = saving,
        saveError = saveError,
        onSave = { title, queries ->
            saving = true
            saveError = null
            scope.launch {
                try {
                    viewModel.update(watch, title, queries)
                    onDismiss()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    saveError = "Не удалось сохранить настройки. Попробуйте ещё раз."
                } finally {
                    saving = false
                }
            }
        },
    )
}
