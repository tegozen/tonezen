package com.tonezen.app.data.remote

import com.tonezen.app.data.remote.progress.ProgressRemoteApi
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

    fun connect(userId: String, accessToken: String) {
        disconnect()
        topic = "realtime:audiobook-progress-$userId"
        val wsBase = supabaseUrl.trimEnd('/').replace("https://", "wss://").replace("http://", "ws://")
        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=$anonKey&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()
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
        val message = JSONArray()
            .put("1")
            .put("1")
            .put(topic)
            .put("phx_join")
            .put(payload)
        webSocket.send(message.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                val heartbeat = JSONArray().put(null).put(null).put("phoenix").put("heartbeat").put(JSONObject())
                webSocket.send(heartbeat.toString())
            }
        }
    }

    private suspend fun handleMessage(text: String) {
        val message = runCatching { JSONArray(text) }.getOrNull() ?: return
        if (message.length() < 5) return
        val event = message.optString(3)
        if (event != "postgres_changes") return
        val payload = message.optJSONObject(4) ?: return
        val data = payload.optJSONObject("data") ?: payload
        val record = data.optJSONObject("record") ?: return
        val progress = ProgressRemoteApi.RemoteProgress(
            bookId = record.getString("book_id"),
            trackId = record.getString("track_id"),
            positionMs = record.getLong("position_ms"),
            updatedAt = record.getString("updated_at"),
        )
        onProgressChange(progress)
    }
}
