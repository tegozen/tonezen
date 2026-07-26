package com.tonezen.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.nearby.PeerNearbyPermissions
import com.tonezen.app.data.nearby.PeerProgressSyncRepository
import com.tonezen.app.data.nearby.PeerProtocol
import com.tonezen.app.domain.progress.PeerCycleChoice
import com.tonezen.app.domain.progress.PeerDeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PeerProgressViewModel @Inject constructor(
    private val peerRepository: PeerProgressSyncRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PeerProgressUiState())
    val uiState: StateFlow<PeerProgressUiState> = _uiState.asStateFlow()

    private var timeoutJob: Job? = null
    private var devicesJob: Job? = null
    private var offersJob: Job? = null

    init {
        offersJob = viewModelScope.launch {
            peerRepository.incomingOffers.collectLatest { incoming ->
                if (_uiState.value.mode != PeerSessionMode.Accepting) return@collectLatest
                if (_uiState.value.pendingOffer != null) return@collectLatest
                timeoutJob?.cancel()
                _uiState.update {
                    it.copy(
                        pendingOffer = PeerPendingOffer(incoming.endpointId, incoming.offer),
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun requiredPermissions(): Array<String> = PeerNearbyPermissions.required()

    fun onAcceptClick(hasPermissions: Boolean) {
        if (!hasPermissions) {
            showAlert(
                "Не удалось включить синхронизацию по блютус",
                "Проверьте Bluetooth и разрешения",
            )
            return
        }
        viewModelScope.launch {
            stopSessionInternal(clearAlert = false)
            _uiState.update {
                it.copy(
                    mode = PeerSessionMode.Accepting,
                    statusMessage = "Ожидание отправки…",
                    pendingOffer = null,
                )
            }
            val result = peerRepository.startAccepting()
            if (result.isFailure) {
                stopSessionInternal(clearAlert = false)
                showAlert(
                    "Не удалось включить синхронизацию по блютус",
                    "Проверьте Bluetooth и разрешения",
                )
                return@launch
            }
            armTimeout {
                stopSessionInternal(clearAlert = false)
                showAlert("Время ожидания истекло", "Никто не отправил прогресс")
            }
        }
    }

    fun onSendClick(hasPermissions: Boolean) {
        if (!hasPermissions) {
            showAlert(
                "Не удалось включить синхронизацию по блютус",
                "Проверьте Bluetooth и разрешения",
            )
            return
        }
        viewModelScope.launch {
            stopSessionInternal(clearAlert = false)
            _uiState.update {
                it.copy(
                    mode = PeerSessionMode.DiscoveringDevices,
                    devices = emptyList(),
                    statusMessage = "Поиск устройств…",
                )
            }
            val result = peerRepository.startDiscovering()
            if (result.isFailure) {
                stopSessionInternal(clearAlert = false)
                showAlert(
                    "Не удалось включить синхронизацию по блютус",
                    "Проверьте Bluetooth и разрешения",
                )
                return@launch
            }
            devicesJob?.cancel()
            devicesJob = viewModelScope.launch {
                peerRepository.devices.collectLatest { list ->
                    _uiState.update { state ->
                        state.copy(
                            devices = list,
                            statusMessage = if (list.isEmpty()) "Поиск устройств…" else null,
                        )
                    }
                }
            }
            armTimeout {
                val empty = _uiState.value.devices.isEmpty()
                stopSessionInternal(clearAlert = false)
                if (empty) {
                    showAlert("Устройства не найдены", "Включите «Принять» на другом устройстве")
                }
            }
        }
    }

    fun dismissAccepting() {
        viewModelScope.launch { stopSessionInternal(clearAlert = true) }
    }

    fun dismissSending() {
        viewModelScope.launch { stopSessionInternal(clearAlert = true) }
    }

    fun onDeviceSelected(device: PeerDeviceInfo) {
        viewModelScope.launch {
            timeoutJob?.cancel()
            val cycles = peerRepository.listSendableCycles()
            if (cycles.isEmpty()) {
                stopSessionInternal(clearAlert = false)
                showAlert("Нечего отправлять", "Нет прогресса аудиокниг с позицией больше нуля")
                return@launch
            }
            _uiState.update {
                it.copy(
                    mode = PeerSessionMode.PickingCycle,
                    selectedDevice = device,
                    cycles = cycles,
                    statusMessage = null,
                )
            }
        }
    }

    fun onCycleSelected(choice: PeerCycleChoice) {
        val device = _uiState.value.selectedDevice ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(mode = PeerSessionMode.Sending, statusMessage = "Отправка…")
            }
            val result = peerRepository.sendCycle(
                endpointId = device.endpointId,
                cycleId = choice.cycleId,
                cycleTitle = choice.cycleTitle,
            )
            stopSessionInternal(clearAlert = false)
            result.fold(
                onSuccess = { accepted ->
                    if (accepted) {
                        showAlert("Готово", "Прогресс отправлен на «${device.deviceLabel}»")
                    } else {
                        showAlert("Отклонено", "На другом устройстве отключили приём")
                    }
                },
                onFailure = {
                    showAlert("Не удалось отправить", "Проверьте Bluetooth и попробуйте снова")
                },
            )
        }
    }

    fun confirmIncomingOffer() {
        val pending = _uiState.value.pendingOffer ?: return
        viewModelScope.launch {
            peerRepository.replyToOffer(pending.endpointId, accepted = true)
            val merge = peerRepository.applyOffer(pending.offer)
            peerRepository.stop()
            timeoutJob?.cancel()
            if (merge.conflicts.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        mode = PeerSessionMode.Idle,
                        pendingOffer = null,
                        statusMessage = null,
                        conflictPrompt = PeerConflictPrompt(
                            cycleTitle = pending.offer.cycleTitle,
                            conflicts = merge.conflicts,
                        ),
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        mode = PeerSessionMode.Idle,
                        pendingOffer = null,
                        statusMessage = null,
                    )
                }
                val message = if (merge.takePeer.isEmpty() && merge.skipped > 0) {
                    "Нечего применить — книг нет в локальном каталоге"
                } else {
                    "Прогресс по «${pending.offer.cycleTitle}» принят"
                }
                showAlert("Готово", message)
            }
        }
    }

    fun rejectIncomingOffer() {
        val pending = _uiState.value.pendingOffer ?: return
        viewModelScope.launch {
            peerRepository.replyToOffer(pending.endpointId, accepted = false)
            stopSessionInternal(clearAlert = true)
        }
    }

    fun chooseConflictLocal() {
        _uiState.update { it.copy(conflictPrompt = null) }
        showAlert("Готово", "Оставлен прогресс на этом устройстве")
    }

    fun chooseConflictPeer() {
        val prompt = _uiState.value.conflictPrompt ?: return
        viewModelScope.launch {
            peerRepository.applyConflictChoice(prompt.conflicts, takePeer = true)
            _uiState.update { it.copy(conflictPrompt = null) }
            showAlert("Готово", "Принят прогресс с другого устройства")
        }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(alertTitle = null, alertMessage = null) }
    }

    override fun onCleared() {
        timeoutJob?.cancel()
        devicesJob?.cancel()
        offersJob?.cancel()
        peerRepository.stopSync()
        super.onCleared()
    }

    private fun armTimeout(onTimeout: suspend () -> Unit) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(PeerProtocol.SESSION_TIMEOUT_MS)
            onTimeout()
        }
    }

    private suspend fun stopSessionInternal(clearAlert: Boolean) {
        timeoutJob?.cancel()
        devicesJob?.cancel()
        peerRepository.stop()
        _uiState.update {
            it.copy(
                mode = PeerSessionMode.Idle,
                devices = emptyList(),
                cycles = emptyList(),
                selectedDevice = null,
                pendingOffer = null,
                statusMessage = null,
                alertTitle = if (clearAlert) null else it.alertTitle,
                alertMessage = if (clearAlert) null else it.alertMessage,
            )
        }
    }

    private fun showAlert(title: String, message: String) {
        _uiState.update { it.copy(alertTitle = title, alertMessage = message) }
    }
}
