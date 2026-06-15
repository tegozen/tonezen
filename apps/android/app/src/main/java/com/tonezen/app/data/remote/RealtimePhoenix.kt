package com.tonezen.app.data.remote

import org.json.JSONObject

internal fun isAuthSubscriptionError(message: String): Boolean =
    Regex("expired|invalid.*token|jwt", RegexOption.IGNORE_CASE).containsMatchIn(message)

internal fun phxReplyErrorReason(payload: JSONObject?): String? {
    if (payload == null || payload.optString("status") != "error") return null
    return payload.optJSONObject("response")?.optString("reason")?.takeIf { it.isNotBlank() }
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
