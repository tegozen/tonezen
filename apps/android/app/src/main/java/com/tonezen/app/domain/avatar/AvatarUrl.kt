package com.tonezen.app.domain.avatar

private val AVATAR_PUBLIC_PATH_RE =
    Regex("""/storage/v1/object/public/avatars/([^/]+)/avatar\.jpg$""", RegexOption.IGNORE_CASE)

private const val AVATAR_FILE_NAME = "avatar.jpg"

fun publicAvatarUrl(baseUrl: String, userId: String): String {
    val root = baseUrl.trimEnd('/')
    return "$root/storage/v1/object/public/avatars/$userId/$AVATAR_FILE_NAME"
}

/** Rewrites emulator/host-specific avatar URLs to this client's public base URL. */
fun normalizeAvatarUrl(avatarUrl: String?, clientBaseUrl: String): String? {
    val trimmed = avatarUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val stripped = trimmed.substringBefore("?")
    val match = AVATAR_PUBLIC_PATH_RE.find(stripped) ?: return trimmed
    val userId = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return trimmed
    if (clientBaseUrl.isBlank()) return trimmed
    return publicAvatarUrl(clientBaseUrl, userId)
}
