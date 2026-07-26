package com.tonezen.app.data.nearby

import com.tonezen.app.domain.progress.PeerProgressItem
import com.tonezen.app.domain.progress.PeerProgressOffer
import org.json.JSONArray
import org.json.JSONObject

internal object PeerProtocol {
    const val VERSION = 1
    const val SERVICE_ID = "com.tonezen.peer_progress"
    const val SESSION_TIMEOUT_MS = 120_000L

    fun encodePresence(userId: String, deviceLabel: String): ByteArray {
        val name = deviceLabel.take(40)
        return JSONObject()
            .put("u", userId)
            .put("n", name)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decodePresence(bytes: ByteArray): Pair<String, String>? {
        return try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val userId = json.optString("u").trim()
            val name = json.optString("n").trim().ifBlank { "Android" }
            if (userId.isBlank()) null else userId to name
        } catch (_: Exception) {
            null
        }
    }

    fun encodeOffer(offer: PeerProgressOffer): ByteArray {
        val progress = JSONArray()
        for (item in offer.progress) {
            progress.put(
                JSONObject()
                    .put("bookId", item.bookId)
                    .put("trackId", item.trackId)
                    .put("positionMs", item.positionMs)
                    .put("updatedAtEpochMs", item.updatedAtEpochMs),
            )
        }
        return JSONObject()
            .put("type", "offer")
            .put("protocol", offer.protocol)
            .put("userId", offer.userId)
            .put("deviceLabel", offer.deviceLabel)
            .put("cycleId", offer.cycleId)
            .put("cycleTitle", offer.cycleTitle)
            .put("progress", progress)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    fun decodeOffer(bytes: ByteArray): PeerProgressOffer? {
        return try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            if (json.optString("type") != "offer") return null
            val progressJson = json.optJSONArray("progress") ?: JSONArray()
            val progress = buildList {
                for (i in 0 until progressJson.length()) {
                    val row = progressJson.getJSONObject(i)
                    val positionMs = row.optLong("positionMs", 0L)
                    if (positionMs <= 0L) continue
                    add(
                        PeerProgressItem(
                            bookId = row.getString("bookId"),
                            trackId = row.getString("trackId"),
                            positionMs = positionMs,
                            updatedAtEpochMs = row.optLong("updatedAtEpochMs", 0L),
                        ),
                    )
                }
            }
            PeerProgressOffer(
                protocol = json.optInt("protocol", VERSION),
                userId = json.getString("userId"),
                deviceLabel = json.optString("deviceLabel").ifBlank { "Android" },
                cycleId = json.getString("cycleId"),
                cycleTitle = json.optString("cycleTitle").ifBlank { "Цикл" },
                progress = progress,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encodeAck(accepted: Boolean): ByteArray =
        JSONObject()
            .put("type", if (accepted) "accept" else "reject")
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun decodeAck(bytes: ByteArray): Boolean? {
        return try {
            when (JSONObject(String(bytes, Charsets.UTF_8)).optString("type")) {
                "accept" -> true
                "reject" -> false
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
