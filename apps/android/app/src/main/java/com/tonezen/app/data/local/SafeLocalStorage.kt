package com.tonezen.app.data.local

import java.io.File

object SafeLocalStorage {
    private val unsafeSegment = Regex("""[\\/]|\.\.""")

    fun isSafeId(id: String): Boolean = id.isNotBlank() && !unsafeSegment.containsMatchIn(id)

    fun trackFile(rootDir: File, bookId: String, trackId: String): File? =
        trackFileWithSuffix(rootDir, bookId, trackId, ".mp3")

    fun trackPartFile(rootDir: File, bookId: String, trackId: String): File? =
        trackFileWithSuffix(rootDir, bookId, trackId, ".part")

    data class DownloadedTrackLocation(
        val bookId: String,
        val path: String,
        val file: File,
    )

    /**
     * Resolves a downloaded track by [trackId], optionally preferring [preferredBookId].
     * Promotes a complete `.part` file to `.mp3` when the final file is missing.
     */
    fun findDownloadedTrack(
        rootDir: File,
        trackId: String,
        preferredBookId: String? = null,
    ): DownloadedTrackLocation? {
        if (!isSafeId(trackId)) return null
        if (preferredBookId != null && isSafeId(preferredBookId)) {
            locateDownloadedTrack(rootDir, preferredBookId, trackId)?.let { return it }
        }
        val downloadsRoot = File(rootDir, "downloads")
        if (!downloadsRoot.isDirectory) return null
        downloadsRoot.listFiles()?.forEach { bookDir ->
            if (!bookDir.isDirectory) return@forEach
            val bookId = bookDir.name
            if (!isSafeId(bookId) || bookId == preferredBookId) return@forEach
            locateDownloadedTrack(rootDir, bookId, trackId)?.let { return it }
        }
        return null
    }

    fun promotePartToFinal(part: File, final: File): Boolean {
        if (!part.isFile || part.length() <= 0L) return false
        final.parentFile?.mkdirs()
        if (final.exists()) final.delete()
        if (part.renameTo(final)) {
            return final.isFile && final.length() > 0L
        }
        return runCatching {
            part.copyTo(final, overwrite = true)
            part.delete()
            final.isFile && final.length() > 0L
        }.getOrDefault(false)
    }

    private fun locateDownloadedTrack(
        rootDir: File,
        bookId: String,
        trackId: String,
    ): DownloadedTrackLocation? {
        val finalFile = trackFile(rootDir, bookId, trackId) ?: return null
        if (finalFile.isFile && finalFile.length() > 0L) {
            return DownloadedTrackLocation(bookId, finalFile.absolutePath, finalFile)
        }
        val partFile = trackPartFile(rootDir, bookId, trackId) ?: return null
        if (!partFile.isFile || partFile.length() <= 0L) return null
        if (!promotePartToFinal(partFile, finalFile)) return null
        return DownloadedTrackLocation(bookId, finalFile.absolutePath, finalFile)
    }

    private fun trackFileWithSuffix(rootDir: File, bookId: String, trackId: String, suffix: String): File? {
        if (!isSafeId(bookId) || !isSafeId(trackId)) return null
        val downloadsRoot = File(rootDir, "downloads").canonicalFile
        val target = File(downloadsRoot, "$bookId/$trackId$suffix").canonicalFile
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
