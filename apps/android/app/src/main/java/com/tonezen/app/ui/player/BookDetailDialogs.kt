package com.tonezen.app.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import com.tonezen.app.ui.components.TonezenGlassAlertDialog
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState

/** Показывает снекбары для ошибок плейбека и загрузки и сбрасывает их после показа. */
@Composable
internal fun BookDetailErrorSnackbars(
    playbackErrorMessage: String?,
    downloadErrorMessage: String?,
    snackbarHostState: SnackbarHostState,
    onDismissPlaybackError: () -> Unit,
    onDismissDownloadError: () -> Unit,
) {
    LaunchedEffect(playbackErrorMessage) {
        playbackErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissPlaybackError()
        }
    }

    LaunchedEffect(downloadErrorMessage) {
        downloadErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissDownloadError()
        }
    }
}

@Composable
internal fun EarlierChapterConfirmDialog(
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
                "Начать с этой главы?",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                "Вы уже слушали более позднюю главу. Начать выбранную главу с начала?",
                color = TonezenMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Начать", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
internal fun EarlierCycleBookConfirmDialog(
    visible: Boolean,
    laterBookTitle: String,
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
                "Начать эту книгу?",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                "Последнее прослушивание в цикле — «$laterBookTitle». Начать выбранную книгу?",
                color = TonezenMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Начать", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
internal fun ProgressSyncConflictDialog(
    visible: Boolean,
    localLabel: String,
    serverLabel: String,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onChooseLocal: () -> Unit,
    onChooseServer: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Где продолжить?",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                "Прогресс на устройстве и в облаке различается.\n\n" +
                    "На устройстве: $localLabel\nВ облаке: $serverLabel",
                color = TonezenMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onChooseServer) {
                Text("В облаке", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onChooseLocal) {
                Text("На устройстве")
            }
        },
    )
}
