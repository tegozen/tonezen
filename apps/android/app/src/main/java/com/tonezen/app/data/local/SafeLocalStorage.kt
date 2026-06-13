package com.tonezen.app.data.local

import java.io.File

object SafeLocalStorage {
    private val unsafeSegment = Regex("""[\\/]|\.\.""")

    fun isSafeId(id: String): Boolean = id.isNotBlank() && !unsafeSegment.containsMatchIn(id)

    fun trackFile(rootDir: File, bookId: String, trackId: String): File? {
        if (!isSafeId(bookId) || !isSafeId(trackId)) return null
        val downloadsRoot = File(rootDir, "downloads").canonicalFile
        val target = File(downloadsRoot, "$bookId/$trackId.mp3").canonicalFile
        val prefix = downloadsRoot.path + File.separator
        if (target.path != downloadsRoot.path && !target.path.startsWith(prefix)) return null
        return target
    }

    fun isUnderAppFilesRoot(rootDir: File, path: String): Boolean {
        if (path.isBlank()) return false
        return runCatching {
            val file = File(path).canonicalFile
            val root = rootDir.canonicalFile
            file.path == root.path || file.path.startsWith(root.path + File.separator)
        }.getOrDefault(false)
    }

    fun sanitizeExistingLocalPath(rootDir: File, path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (!isUnderAppFilesRoot(rootDir, path)) return null
        val file = File(path).canonicalFile
        if (!file.isFile || file.length() <= 0L) return null
        return file.path
    }

    /** Fast DB read path: prefix check only — full file validation happens before playback. */
    fun sanitizeStoredLocalPath(rootDir: File, path: String?): String? {
        if (path.isNullOrBlank() || path.contains("..")) return null
        val root = rootDir.absolutePath.replace('\\', '/').trimEnd('/')
        val normalized = path.replace('\\', '/')
        if (normalized != root && !normalized.startsWith("$root/")) return null
        return path
    }
}
