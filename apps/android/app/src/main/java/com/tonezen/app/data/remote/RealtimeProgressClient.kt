package com.tonezen.app.data.remote

import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Supabase Realtime client for postgres_changes on audiobook_progress.
 */
class RealtimeProgressClient(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onProgressChange: suspend (ProgressRemoteApi.RemoteProgress) -> Unit,
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var topic: String? = null
    private var onAuthError: (() -> Unit)? = null
    private val messageRef = PhoenixMessageRefCounter()

    fun connect(userId: String, accessToken: String, onAuthError: () -> Unit = {}) {
        disconnect()
        this.onAuthError = onAuthError
        messageRef.reset()
        topic = "realtime:audiobook-progress-$userId"
        val wsBase = supabaseUrl.trimEnd('/').replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsBase/realtime/v1/websocket?vsn=1.0.0"
        val request = Request.Builder()
            .url(wsUrl)
            .header("apikey", anonKey)
            .build()
        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sendJoin(webSocket, userId, accessToken)
                    startHeartbeat(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch { handleMessage(text) }
                }
            },
        )
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "bye")
        webSocket = null
        topic = null
        onAuthError = null
    }

    private fun sendJoin(webSocket: WebSocket, userId: String, accessToken: String) {
        val postgresChange = JSONObject()
            .put("event", "*")
            .put("schema", "public")
            .put("table", "audiobook_progress")
            .put("filter", "user_id=eq.$userId")
        val config = JSONObject()
            .put("postgres_changes", JSONArray().put(postgresChange))
            .put("broadcast", JSONObject().put("self", true))
            .put("presence", JSONObject().put("key", ""))
        val payload = JSONObject()
            .put("config", config)
            .put("access_token", accessToken)
        val joinRef = messageRef.next()
        webSocket.send(
            encodePhoenixV1Message(
                topic = topic ?: return,
                event = "phx_join",
                payload = payload,
                ref = joinRef,
                joinRef = joinRef,
            ),
        )
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob = scope.launch {
            try {
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    val sent = webSocket.send(
                        encodePhoenixV1Message(
                            topic = "phoenix",
                            event = "heartbeat",
                            payload = JSONObject(),
                            ref = messageRef.next(),
                        ),
                    )
                    if (!sent) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Connection dropped; disconnect() runs on the next reconnect.
            }
        }
    }

    private fun dispatchAuthError() {
        val handler = onAuthError ?: return
        scope.launch { handler() }
    }

    private suspend fun handleMessage(text: String) {
        when (phoenixMessageEvent(text)) {
            "phx_reply" -> {
                val reason = phxReplyErrorReason(phoenixMessagePayload(text))
                if (reason != null && isAuthSubscriptionError(reason)) {
                    dispatchAuthError()
                }
                return
            }
            "postgres_changes" -> {
                val payload = phoenixMessagePayload(text) ?: return
                val data = payload.optJSONObject("data") ?: payload
                val record = data.optJSONObject("record") ?: return
                val progress = ProgressRemoteApi.RemoteProgress(
                    bookId = record.getString("book_id"),
                    trackId = record.getString("track_id"),
                    positionMs = record.getLong("position_ms"),
                    updatedAt = record.getString("updated_at"),
                    revision = record.optLong("revision", 0L),
                )
                onProgressChange(progress)
            }
        }
    }

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 25_000L
    }
}
