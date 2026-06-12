package com.tonezen.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.TonezenGlassAlertDialog
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import dev.chrisbanes.haze.HazeState

@Composable
internal fun StorageSettingsScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    bottomScrollPadding: Dp,
    usedBytes: Long,
    totalBytes: Long?,
    showDeleteAllConfirm: Boolean,
    onBack: () -> Unit,
    onDeleteAllClick: () -> Unit,
    onDismissDeleteAllConfirm: () -> Unit,
    onConfirmDeleteAll: () -> Unit,
) {
    val usedPercent = totalBytes?.takeIf { it > 0L }?.let { usedBytes.toFloat() / it.toFloat() }
    val hasDownloads = usedBytes > 0L

    BackHandler(enabled = showDeleteAllConfirm) {
        onDismissDeleteAllConfirm()
    }

    TonezenGlassAlertDialog(
        visible = showDeleteAllConfirm,
        hazeState = hazeState,
        onDismissRequest = onDismissDeleteAllConfirm,
        title = {
            Text(
                stringResource(R.string.delete_all_confirm_title),
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(stringResource(R.string.delete_all_confirm_body), color = TonezenMuted)
        },
        confirmButton = {
            TextButton(onClick = onConfirmDeleteAll) {
                Text(stringResource(R.string.delete_all), color = TonezenError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissDeleteAllConfirm) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        bottomScrollPadding = bottomScrollPadding,
        title = {
            Text(
                stringResource(R.string.settings_storage_page_title),
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_storage_downloads_section)) {
                Text(
                    stringResource(R.string.storage_saved, formatStorageGb(usedBytes)),
                    color = TonezenInk,
                    fontWeight = FontWeight.SemiBold,
                )
                usedPercent?.let {
                    Text(
                        stringResource(R.string.storage_percent, (it * 100).toInt()),
                        color = TonezenMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(R.string.settings_storage_downloads_desc),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onDeleteAllClick,
                    enabled = hasDownloads,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.delete_all), color = TonezenError)
                }
            }
        }
    }
}

private fun formatStorageGb(bytes: Long): String =
    String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
