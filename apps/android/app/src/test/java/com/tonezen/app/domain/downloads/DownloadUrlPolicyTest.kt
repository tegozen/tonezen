package com.tonezen.app.domain.downloads

import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadUrlPolicyTest {
    @Test
    fun acceptsSameOriginSignedUrl() {
        DownloadUrlPolicy.assertAllowedDownloadUrl(
            "http://localhost:8000/storage/v1/object/sign/content/a.mp3?token=x",
            "http://localhost:8000",
        )
    }

    @Test
    fun rejectsForeignOrigin() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadUrlPolicy.assertAllowedDownloadUrl(
                "http://evil.example/file.mp3",
                "http://localhost:8000",
            )
        }
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadUrlPolicy.assertAllowedDownloadUrl(
                "file:///etc/passwd",
                "http://localhost:8000",
            )
        }
    }
}
