package com.tonezen.app.data.network

import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMonitorTest {
    @Test
    fun resolveOnlineState_isOnlineWhenAnyNetworkHasInternet() {
        assertTrue(
            resolveOnlineState(
                listOf(
                    capabilities(hasInternet = false),
                    capabilities(hasInternet = true),
                ),
            ),
        )
    }

    @Test
    fun resolveOnlineState_isOfflineWhenNoNetworkHasInternet() {
        assertFalse(
            resolveOnlineState(
                listOf(
                    null,
                    capabilities(hasInternet = false),
                ),
            ),
        )
    }

    private fun capabilities(hasInternet: Boolean): NetworkCapabilities {
        val capabilities = mockk<NetworkCapabilities>()
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns hasInternet
        return capabilities
    }
}
