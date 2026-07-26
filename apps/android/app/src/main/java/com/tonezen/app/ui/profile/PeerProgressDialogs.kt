package com.tonezen.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.progress.PeerCycleChoice
import com.tonezen.app.domain.progress.PeerDeviceInfo
import com.tonezen.app.ui.components.TonezenGlassAlertDialog
import com.tonezen.app.ui.components.TonezenGlassModalBottomSheet
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun PeerAcceptWaitingDialog(
    visible: Boolean,
    statusMessage: String?,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    title: String = "Ожидание отправки",
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(color = TonezenTeal)
                Text(
                    statusMessage ?: "Ожидание отправки…",
                    color = TonezenMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun PeerIncomingOfferDialog(
    visible: Boolean,
    deviceLabel: String,
    cycleTitle: String,
    hazeState: HazeState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onReject,
        title = {
            Text(
                "Принять прогресс?",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                "«$deviceLabel» предлагает прогресс по циклу «$cycleTitle». Сверить и принять?",
                color = TonezenMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Да", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Отключить приём")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun PeerConflictDialog(
    visible: Boolean,
    cycleTitle: String,
    hazeState: HazeState,
    onChooseLocal: () -> Unit,
    onChoosePeer: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onChooseLocal,
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
                "Прогресс по «$cycleTitle» различается на устройствах.",
                color = TonezenMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onChoosePeer) {
                Text("На другом устройстве", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onChooseLocal) {
                Text("На этом устройстве")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun PeerAlertDialog(
    visible: Boolean,
    title: String,
    message: String,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = { Text(message, color = TonezenMuted) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ОК", color = TonezenTeal)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun PeerDevicePickerSheet(
    visible: Boolean,
    devices: List<PeerDeviceInfo>,
    statusMessage: String?,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onDeviceClick: (PeerDeviceInfo) -> Unit,
) {
    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Выберите устройство",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (devices.isEmpty()) {
                Text(statusMessage ?: "Поиск устройств…", color = TonezenMuted)
                CircularProgressIndicator(color = TonezenTeal, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(devices, key = { it.endpointId }) { device ->
                        Text(
                            device.deviceLabel,
                            color = TonezenInk,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDeviceClick(device) }
                                .padding(vertical = 14.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Отмена")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun PeerCyclePickerSheet(
    visible: Boolean,
    cycles: List<PeerCycleChoice>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onCycleClick: (PeerCycleChoice) -> Unit,
) {
    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Выберите цикл",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(cycles, key = { it.cycleId }) { cycle ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCycleClick(cycle) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(cycle.cycleTitle, color = TonezenInk, fontWeight = FontWeight.Medium)
                        Text(
                            "${cycle.progress.size} кн. с прогрессом",
                            color = TonezenMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Отмена")
            }
        }
    }
}
