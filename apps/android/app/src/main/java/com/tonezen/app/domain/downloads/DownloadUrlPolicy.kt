package com.tonezen.app.domain.downloads

import java.net.URI

object DownloadUrlPolicy {
    fun assertAllowedDownloadUrl(url: String, baseUrl: String) {
        val target = URI(url)
        val allowed = URI(baseUrl.trimEnd('/'))
        val scheme = target.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Invalid download URL scheme")
        }
        val targetHost = target.host ?: throw IllegalArgumentException("Invalid download URL")
        val allowedHost = allowed.host ?: throw IllegalArgumentException("Invalid base URL")
        val targetPort = normalizedPort(target)
        val allowedPort = normalizedPort(allowed)
        if (!targetHost.equals(allowedHost, ignoreCase = true) || targetPort != allowedPort) {
            throw IllegalArgumentException("Download URL origin mismatch")
        }
    }

    private fun normalizedPort(uri: URI): Int {
        if (uri.port != -1) return uri.port
        return when (uri.scheme?.lowercase()) {
            "https" -> 443
            else -> 80
        }
    }
}
