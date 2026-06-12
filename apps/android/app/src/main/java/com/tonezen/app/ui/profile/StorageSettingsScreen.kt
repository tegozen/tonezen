package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.ChevronRightGlyph
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted

@Composable
internal fun StorageSettingsScreen(
    padding: PaddingValues,
    usedBytes: Long,
    totalBytes: Long?,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val usedPercent = totalBytes?.takeIf { it > 0L }?.let { usedBytes.toFloat() / it.toFloat() }

    TonezenFixedHeaderScreen(
        padding = padding,
        onBack = onBack,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenDownloads)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_storage_manage_downloads),
                        color = TonezenInk,
                        fontWeight = FontWeight.Medium,
                    )
                    ChevronRightGlyph()
                }
            }
        }
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_storage_cache_section)) {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_storage_cache_section),
                    subtitle = stringResource(R.string.settings_storage_cache_desc),
                )
            }
        }
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_storage_device_section)) {
                totalBytes?.let { total ->
                    Text(
                        formatStorageGb(total),
                        color = TonezenInk,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                SettingsInfoRow(
                    title = stringResource(R.string.settings_storage_device_section),
                    subtitle = stringResource(R.string.settings_storage_device_desc),
                )
            }
        }
    }
}

private fun formatStorageGb(bytes: Long): String =
    String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
