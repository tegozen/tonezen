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
    fun findDownloadedTrack_promotesCompletePartFile() {
        val root = Files.createTempDirectory("tonezen-safe-storage-part").toFile()
        val part = File(root, "downloads/book-a/track-1.part")
        part.parentFile?.mkdirs()
        part.writeBytes(byteArrayOf(9))

        val found = SafeLocalStorage.findDownloadedTrack(root, "track-1", preferredBookId = "book-a")

        assertNotNull(found)
        assertEquals("book-a", found?.bookId)
        assertTrue(File(root, "downloads/book-a/track-1.mp3").isFile)
        assertNull(SafeLocalStorage.trackPartFile(root, "book-a", "track-1")?.takeIf { it.exists() })
    }
}
