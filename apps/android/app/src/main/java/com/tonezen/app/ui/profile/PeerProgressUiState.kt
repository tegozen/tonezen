package com.tonezen.app.ui.profile

import com.tonezen.app.domain.progress.PeerCycleChoice
import com.tonezen.app.domain.progress.PeerDeviceInfo
import com.tonezen.app.domain.progress.PeerProgressItem
import com.tonezen.app.domain.progress.PeerProgressOffer

enum class PeerSessionMode {
    Idle,
    Accepting,
    DiscoveringDevices,
    PickingCycle,
    Sending,
}

data class PeerPendingOffer(
    val endpointId: String,
    val offer: PeerProgressOffer,
)

data class PeerConflictPrompt(
    val cycleTitle: String,
    val conflicts: List<PeerProgressItem>,
)

data class PeerProgressUiState(
    val mode: PeerSessionMode = PeerSessionMode.Idle,
    val devices: List<PeerDeviceInfo> = emptyList(),
    val cycles: List<PeerCycleChoice> = emptyList(),
    val selectedDevice: PeerDeviceInfo? = null,
    val pendingOffer: PeerPendingOffer? = null,
    val conflictPrompt: PeerConflictPrompt? = null,
    val alertTitle: String? = null,
    val alertMessage: String? = null,
    val statusMessage: String? = null,
)
