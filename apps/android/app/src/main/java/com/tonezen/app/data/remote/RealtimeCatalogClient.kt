package com.tonezen.app.data.remote

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
 * Minimal Supabase Realtime client for postgres_changes on catalog tables (books, cycles, tracks).
 */
class RealtimeCatalogClient(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onCatalogChange: () -> Unit,
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
        topic = "realtime:catalog-global-$userId"
        val wsBase = supabaseUrl.trimEnd('/').replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()
        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    sendJoin(webSocket, accessToken)
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

    private fun sendJoin(webSocket: WebSocket, accessToken: String) {
        val postgresChanges = JSONArray()
            .put(
                JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", "books"),
            )
            .put(
                JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", "cycles"),
            )
            .put(
                JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", "tracks"),
            )
        val config = JSONObject()
            .put("postgres_changes", postgresChanges)
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
            while (isActive) {
                delay(25_000)
                webSocket.send(
                    encodePhoenixV1Message(
                        topic = "phoenix",
                        event = "heartbeat",
                        payload = JSONObject(),
                        ref = messageRef.next(),
                    ),
                )
            }
        }
    }

    private suspend fun handleMessage(text: String) {
        when (phoenixMessageEvent(text)) {
            "phx_reply" -> {
                val reason = phxReplyErrorReason(phoenixMessagePayload(text))
                if (reason != null && isAuthSubscriptionError(reason)) {
                    onAuthError?.invoke()
                }
            }
            "postgres_changes" -> onCatalogChange()
        }
    }
}
