package com.tonezen.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SafeLocalStorageTest {
    @Test
    fun trackFile_rejectsPathTraversalInIds() {
        val root = File("build/test-safe-storage").apply {
            deleteRecursively()
            mkdirs()
        }
        assertNull(SafeLocalStorage.trackFile(root, "../etc", "track"))
        assertNull(SafeLocalStorage.trackFile(root, "book", "../passwd"))
    }

    @Test
    fun trackFile_staysUnderDownloadsRoot() {
        val root = File("build/test-safe-storage").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = SafeLocalStorage.trackFile(root, "book-1", "track-1")
        requireNotNull(target)
        target.parentFile?.mkdirs()
        target.writeText("ok")
        assertEquals(true, target.isFile)
        assertEquals(true, SafeLocalStorage.isUnderAppFilesRoot(root, target.path))
    }

    @Test
    fun sanitizeExistingLocalPath_rejectsPathsOutsideAppRoot() {
        val root = File("build/test-safe-storage").apply {
            deleteRecursively()
            mkdirs()
        }
        val allowed = File(root, "downloads/book/track.mp3").apply {
            parentFile?.mkdirs()
            writeText("audio")
        }
        assertEquals(allowed.canonicalFile.path, SafeLocalStorage.sanitizeExistingLocalPath(root, allowed.path))
        assertNull(SafeLocalStorage.sanitizeExistingLocalPath(root, "/etc/passwd"))
        assertNull(SafeLocalStorage.sanitizeExistingLocalPath(root, "../outside.mp3"))
    }

    @Test
    fun sanitizeStoredLocalPath_rejectsPathsOutsideAppRoot() {
        val root = File("build/test-safe-storage").apply {
            deleteRecursively()
            mkdirs()
        }.absoluteFile
        val allowed = File(root, "downloads/book/track.mp3")
        assertEquals(allowed.path, SafeLocalStorage.sanitizeStoredLocalPath(root, allowed.path))
        assertNull(SafeLocalStorage.sanitizeStoredLocalPath(root, "/etc/passwd"))
        assertNull(SafeLocalStorage.sanitizeStoredLocalPath(root, "../outside.mp3"))
    }
}
