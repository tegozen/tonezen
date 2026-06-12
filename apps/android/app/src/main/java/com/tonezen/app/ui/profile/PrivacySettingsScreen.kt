package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun PrivacySettingsScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    TonezenFixedHeaderScreen(
        padding = padding,
        onBack = onBack,
        title = {
            Text(
                stringResource(R.string.settings_privacy_page_title),
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_privacy_data_section)) {
                Text(
                    stringResource(R.string.settings_privacy_dialog_body),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_privacy_lock_section)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_privacy_lock_section),
                            color = TonezenInk,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stringResource(R.string.settings_privacy_lock_desc),
                            color = TonezenMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = false,
                        colors = SwitchDefaults.colors(
                            disabledCheckedThumbColor = TonezenMuted,
                            disabledUncheckedThumbColor = TonezenMuted,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.settings_coming_soon),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_privacy_permissions_section)) {
                Button(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
                ) {
                    Text(stringResource(R.string.settings_privacy_open_app_settings))
                }
            }
        }
    }
}
