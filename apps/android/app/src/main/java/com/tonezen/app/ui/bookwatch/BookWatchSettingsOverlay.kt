package com.tonezen.app.ui.bookwatch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tonezen.app.data.remote.RemoteHttpException
import java.io.IOException
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
                    saveError = when (error) {
                        is RemoteHttpException -> when (error.statusCode) {
                            400 -> "Название и каждый поисковый запрос должны содержать от 1 до 200 символов. Всего допускается до 40 запросов."
                            401, 403 -> "Сервер не подтвердил авторизацию. Повторите попытку после восстановления соединения."
                            404 -> "Сервер не нашёл настройки отслеживания. Требуется проверить обновление сервера."
                            else -> "Сервер не смог сохранить настройки (код ${error.statusCode}). Повторите попытку позже."
                        }
                        is IOException -> "Нет соединения с сервером. Проверьте подключение и повторите сохранение."
                        else -> "Не удалось подготовить или сохранить настройки отслеживания."
                    }
                } finally {
                    saving = false
                }
            }
        },
    )
}
