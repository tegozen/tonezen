package com.tonezen.app.ui.profile

import com.tonezen.app.data.nearby.PeerProgressSyncRepository
import com.tonezen.app.data.nearby.PeerProtocol
import com.tonezen.app.data.nearby.NearbyPeerTransport
import com.tonezen.app.domain.progress.PeerDeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class PeerProgressSessionController(
    private val scope: CoroutineScope,
    private val peerRepository: PeerProgressSyncRepository,
) {
    private var timeoutJob: Job? = null
    private var devicesJob: Job? = null
    private var offersJob: Job? = null

    fun observeOffers(onOffer: suspend (NearbyPeerTransport.IncomingOffer) -> Unit) {
        offersJob?.cancel()
        offersJob = scope.launch {
            peerRepository.incomingOffers.collectLatest(onOffer)
        }
    }

    fun observeDevices(onDevices: suspend (List<PeerDeviceInfo>) -> Unit) {
        devicesJob?.cancel()
        devicesJob = scope.launch {
            peerRepository.devices.collectLatest(onDevices)
        }
    }

    fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    fun armTimeout(onTimeout: suspend () -> Unit) {
        cancelTimeout()
        timeoutJob = scope.launch {
            delay(PeerProtocol.SESSION_TIMEOUT_MS)
            onTimeout()
        }
    }

    suspend fun stopSession() {
        cancelTimeout()
        devicesJob?.cancel()
        devicesJob = null
        peerRepository.stop()
    }

    fun close() {
        cancelTimeout()
        devicesJob?.cancel()
        offersJob?.cancel()
        peerRepository.stopSync()
    }
}
