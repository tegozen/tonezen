package com.tonezen.app.data.nearby

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.tonezen.app.domain.progress.PeerDeviceInfo
import com.tonezen.app.domain.progress.PeerProgressOffer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class NearbyPeerTransport @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val mutex = Mutex()

    private val _devices = MutableStateFlow<List<PeerDeviceInfo>>(emptyList())
    val devices: StateFlow<List<PeerDeviceInfo>> = _devices.asStateFlow()

    private val _incomingOffers = MutableSharedFlow<IncomingOffer>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingOffers: SharedFlow<IncomingOffer> = _incomingOffers.asSharedFlow()

    private var localUserId: String? = null
    private var localDeviceLabel: String = "Android"
    private var pendingConnection: CancellableContinuation<Boolean>? = null
    private var pendingAck: CancellableContinuation<Boolean?>? = null

    data class IncomingOffer(
        val endpointId: String,
        val offer: PeerProgressOffer,
    )

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            PeerProtocol.decodeOffer(bytes)?.let { offer ->
                _incomingOffers.tryEmit(IncomingOffer(endpointId, offer))
                return
            }
            val ack = PeerProtocol.decodeAck(bytes) ?: return
            pendingAck?.takeIf { it.isActive }?.resume(ack)
            pendingAck = null
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val ok = resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK
            pendingConnection?.takeIf { it.isActive }?.resume(ok)
            pendingConnection = null
        }

        override fun onDisconnected(endpointId: String) {
            pendingAck?.takeIf { it.isActive }?.resume(null)
            pendingAck = null
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val presence = PeerProtocol.decodePresence(info.endpointInfo) ?: return
            val (userId, label) = presence
            val expected = localUserId ?: return
            if (userId != expected) return
            _devices.value = _devices.value
                .filterNot { it.endpointId == endpointId }
                .plus(PeerDeviceInfo(endpointId, label, userId))
        }

        override fun onEndpointLost(endpointId: String) {
            _devices.value = _devices.value.filterNot { it.endpointId == endpointId }
        }
    }

    suspend fun startAccepting(userId: String, deviceLabel: String): Result<Unit> = mutex.withLock {
        stopInternal()
        localUserId = userId
        localDeviceLabel = deviceLabel
        val presence = PeerProtocol.encodePresence(userId, deviceLabel)
        return awaitNearbyTask {
            client.startAdvertising(
                presence,
                PeerProtocol.SERVICE_ID,
                connectionLifecycle,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
            )
        }
    }

    suspend fun startDiscovering(userId: String, deviceLabel: String): Result<Unit> = mutex.withLock {
        stopInternal()
        localUserId = userId
        localDeviceLabel = deviceLabel
        _devices.value = emptyList()
        return awaitNearbyTask {
            client.startDiscovery(
                PeerProtocol.SERVICE_ID,
                discoveryCallback,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
            )
        }
    }

    /** Connect, send offer, wait for ACK. `true` = accepted. */
    suspend fun sendOffer(endpointId: String, offer: PeerProgressOffer): Result<Boolean> {
        val connected = withTimeoutOrNull(PeerProtocol.SESSION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                pendingConnection = cont
                cont.invokeOnCancellation { pendingConnection = null }
                client.requestConnection(
                    localDeviceLabel.take(40),
                    endpointId,
                    connectionLifecycle,
                ).addOnFailureListener {
                    if (cont.isActive) cont.resume(false)
                    pendingConnection = null
                }
            }
        } ?: false
        if (!connected) {
            stop()
            return Result.failure(IllegalStateException("connect_failed"))
        }
        return try {
            client.sendPayload(endpointId, Payload.fromBytes(PeerProtocol.encodeOffer(offer)))
            val ack = withTimeoutOrNull(PeerProtocol.SESSION_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    pendingAck = cont
                    cont.invokeOnCancellation { pendingAck = null }
                }
            }
            stop()
            when (ack) {
                true -> Result.success(true)
                false -> Result.success(false)
                null -> Result.failure(IllegalStateException("ack_timeout"))
            }
        } catch (e: Exception) {
            stop()
            Result.failure(e)
        }
    }

    fun sendAck(endpointId: String, accepted: Boolean) {
        runCatching {
            client.sendPayload(endpointId, Payload.fromBytes(PeerProtocol.encodeAck(accepted)))
        }
    }

    suspend fun stop() = mutex.withLock { stopInternal() }

    /** Non-suspend stop for ViewModel.onCleared. */
    fun stopSync() {
        stopInternal()
    }

    private fun stopInternal() {
        runCatching { client.stopAllEndpoints() }
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        pendingConnection?.takeIf { it.isActive }?.resume(false)
        pendingConnection = null
        pendingAck?.takeIf { it.isActive }?.resume(null)
        pendingAck = null
        _devices.value = emptyList()
    }

    private suspend fun awaitNearbyTask(block: () -> com.google.android.gms.tasks.Task<Void>): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            block()
                .addOnSuccessListener { cont.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> cont.resume(Result.failure(mapError(e))) }
        }

    private fun mapError(error: Exception): Exception {
        val code = (error as? ApiException)?.statusCode
        val message = when (code) {
            ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> "bluetooth"
            else -> error.message ?: "nearby_error"
        }
        return IllegalStateException(message, error)
    }
}
