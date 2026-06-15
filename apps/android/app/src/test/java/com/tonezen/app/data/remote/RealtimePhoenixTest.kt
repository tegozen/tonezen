package com.tonezen.app.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimePhoenixTest {
    @Test
    fun detectsExpiredJwtReplyReason() {
        assertTrue(isAuthSubscriptionError("Token has expired 143 seconds ago"))
    }

    @Test
    fun ignoresUnrelatedErrors() {
        assertFalse(isAuthSubscriptionError("connection closed"))
    }

    @Test
    fun parsesPhxReplyErrorPayload() {
        val reason = phxReplyErrorReason(
            JSONObject()
                .put("status", "error")
                .put("response", JSONObject().put("reason", "Token has expired 10 seconds ago")),
        )
        assertTrue(reason?.contains("expired") == true)
    }

    @Test
    fun encodesPhoenixV1JoinMessage() {
        val payload = JSONObject().put("access_token", "token")
        val raw = encodePhoenixV1Message(
            topic = "realtime:catalog-global-user",
            event = "phx_join",
            payload = payload,
            ref = "1",
            joinRef = "1",
        )
        val message = JSONObject(raw)
        assertEquals("realtime:catalog-global-user", message.getString("topic"))
        assertEquals("phx_join", message.getString("event"))
        assertEquals("1", message.getString("ref"))
        assertEquals("1", message.getString("join_ref"))
        assertEquals("token", message.getJSONObject("payload").getString("access_token"))
    }

    @Test
    fun encodesPhoenixV1HeartbeatWithNullJoinRef() {
        val raw = encodePhoenixV1Message(
            topic = "phoenix",
            event = "heartbeat",
            payload = JSONObject(),
            ref = "2",
        )
        val message = JSONObject(raw)
        assertEquals("phoenix", message.getString("topic"))
        assertEquals("heartbeat", message.getString("event"))
        assertEquals("2", message.getString("ref"))
        assertTrue(message.isNull("join_ref"))
    }

    @Test
    fun parsesPhoenixV1ReplyEvent() {
        val raw = JSONObject()
            .put("topic", "realtime:catalog-global-user")
            .put("event", "phx_reply")
            .put("payload", JSONObject().put("status", "ok"))
            .toString()
        assertEquals("phx_reply", phoenixMessageEvent(raw))
        assertEquals("ok", phoenixMessagePayload(raw)?.getString("status"))
    }
}
