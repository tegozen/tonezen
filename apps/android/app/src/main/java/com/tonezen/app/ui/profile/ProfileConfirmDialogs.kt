package com.tonezen.app.ui.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.tonezen.app.ui.components.TonezenGlassAlertDialog
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun SignOutConfirmDialog(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Выйти из аккаунта?",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text("Офлайн-загрузки останутся на устройстве. Прогресс аудиокниг синхронизируется снова после входа онлайн.", color = TonezenMuted)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Выйти", color = TonezenError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun OfflineSyncDialog(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Синхронизация приостановлена",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text("Вы офлайн. Прогресс аудиокниг синхронизируется, когда появится сеть.", color = TonezenMuted)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Продолжить слушать", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text("Повторить")
            }
        },
    )
}
