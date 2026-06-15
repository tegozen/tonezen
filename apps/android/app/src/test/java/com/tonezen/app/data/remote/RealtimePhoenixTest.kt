package com.tonezen.app.data.remote

import org.json.JSONObject
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
}
