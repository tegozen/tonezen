package com.tonezen.app.data.remote

import org.json.JSONObject

internal fun isAuthSubscriptionError(message: String): Boolean =
    Regex("expired|invalid.*token|jwt", RegexOption.IGNORE_CASE).containsMatchIn(message)

internal fun phxReplyErrorReason(payload: JSONObject?): String? {
    if (payload == null || payload.optString("status") != "error") return null
    return payload.optJSONObject("response")?.optString("reason")?.takeIf { it.isNotBlank() }
}

internal class PhoenixMessageRefCounter(private var value: Int = 1) {
    fun next(): String = (value++).toString()

    fun reset(start: Int = 1) {
        value = start
    }
}

internal fun encodePhoenixV1Message(
    topic: String,
    event: String,
    payload: JSONObject,
    ref: String,
    joinRef: String? = null,
): String =
    JSONObject()
        .put("topic", topic)
        .put("event", event)
        .put("payload", payload)
        .put("ref", ref)
        .put("join_ref", joinRef ?: JSONObject.NULL)
        .toString()

internal fun phoenixMessageEvent(raw: String): String? {
    val v1 = runCatching { JSONObject(raw) }.getOrNull()
    if (v1 != null && v1.has("event")) {
        return v1.optString("event").takeIf { it.isNotBlank() }
    }
    return JSONArrayMessageEvent(raw)
}

internal fun phoenixMessagePayload(raw: String): JSONObject? {
    val v1 = runCatching { JSONObject(raw) }.getOrNull()
    if (v1 != null && v1.has("payload")) {
        return v1.optJSONObject("payload")
    }
    return JSONArrayMessagePayload(raw)
}

internal fun JSONArrayMessageEvent(raw: String): String? {
    val message = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return null
    if (message.length() < 4) return null
    return message.optString(3).takeIf { it.isNotBlank() }
}

internal fun JSONArrayMessagePayload(raw: String): JSONObject? {
    val message = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return null
    if (message.length() < 5) return null
    return message.optJSONObject(4)
}
