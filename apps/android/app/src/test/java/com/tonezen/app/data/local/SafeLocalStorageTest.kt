package com.tonezen.app.data.local

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeLocalStorageTest {
    @Test
    fun findDownloadedTrack_returnsFileFromAnyBookFolder() {
        val root = Files.createTempDirectory("tonezen-safe-storage").toFile()
        val file = File(root, "downloads/book-a/track-1.mp3")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))

        val found = SafeLocalStorage.findDownloadedTrack(root, "track-1", preferredBookId = "book-b")

        assertNotNull(found)
        assertEquals("book-a", found?.bookId)
        assertTrue(found?.file?.isFile == true)
    }

    @Test
    fun findDownloadedTrack_ignoresPartFile() {
        val root = Files.createTempDirectory("tonezen-safe-storage-part").toFile()
        val part = File(root, "downloads/book-a/track-1.part")
        part.parentFile?.mkdirs()
        part.writeBytes(byteArrayOf(9))

        val found = SafeLocalStorage.findDownloadedTrack(root, "track-1", preferredBookId = "book-a")

        assertNull(found)
        assertTrue(part.isFile)
        assertNull(File(root, "downloads/book-a/track-1.mp3").takeIf { it.exists() })
    }

    @Test
    fun sanitizeStoredLocalPath_acceptsCanonicalPathWhenRootIsSymlinked() {
        val realRoot = Files.createTempDirectory("tonezen-files-real").toFile()
        val linkRoot = realRoot.parentFile.resolve("tonezen-files-link-${System.nanoTime()}")
        Files.createSymbolicLink(linkRoot.toPath(), realRoot.toPath())
        try {
            val file = File(realRoot, "downloads/book-a/track-1.mp3")
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(1, 2, 3))
            val storedPath = file.canonicalPath

            val validated = SafeLocalStorage.sanitizeStoredLocalPath(linkRoot, storedPath)

            assertEquals(storedPath, validated)
        } finally {
            Files.deleteIfExists(linkRoot.toPath())
        }
    }
}
