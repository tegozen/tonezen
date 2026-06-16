package com.tonezen.app.domain.downloads

import java.net.URI

object DownloadUrlPolicy {
    fun normalizeDownloadUrl(url: String, baseUrl: String): String {
        val target = URI(url)
        val path = target.path ?: return url
        if (!path.contains("/object/sign/")) return url
        val allowed = URI(baseUrl.trimEnd('/'))
        val normalizedPath = when {
            path.contains("/storage/v1/") -> path
            path.startsWith("/object/sign/") -> "/storage/v1$path"
            else -> path
        }
        val query = target.rawQuery?.let { "?$it" }.orEmpty()
        val scheme = allowed.scheme ?: target.scheme ?: "https"
        val host = allowed.host ?: return url
        val port = normalizedPort(allowed)
        val authority = if (
            (scheme.equals("https", ignoreCase = true) && port == 443) ||
            (scheme.equals("http", ignoreCase = true) && port == 80)
        ) {
            host
        } else {
            "$host:$port"
        }
        return "$scheme://$authority$normalizedPath$query"
    }

    fun assertAllowedDownloadUrl(url: String, baseUrl: String) {
        val normalized = normalizeDownloadUrl(url, baseUrl)
        val target = URI(normalized)
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
