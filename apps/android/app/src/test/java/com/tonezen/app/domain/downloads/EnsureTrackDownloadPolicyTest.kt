package com.tonezen.app.domain.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnsureTrackDownloadPolicyTest {
    @Test
    fun usesDownloadedPathWhenMarkSucceeded() {
        assertEquals(
            "/files/track.mp3",
            EnsureTrackDownloadPolicy.resolveLocalPathAfterDownload(
                downloadedPath = "/files/track.mp3",
                markSucceeded = true,
                recoveredPath = null,
            ),
        )
    }

    @Test
    fun recoversPathWhenMarkFailedButFileExists() {
        assertEquals(
            "/files/track.mp3",
            EnsureTrackDownloadPolicy.resolveLocalPathAfterDownload(
                downloadedPath = "/files/track.mp3",
                markSucceeded = false,
                recoveredPath = "/files/track.mp3",
            ),
        )
    }

    @Test
    fun returnsNullWhenMarkFailedAndNoRecovery() {
        assertNull(
            EnsureTrackDownloadPolicy.resolveLocalPathAfterDownload(
                downloadedPath = "/files/track.mp3",
                markSucceeded = false,
                recoveredPath = null,
            ),
        )
    }

    @Test
    fun resolvesAfterFailureWhenFileExistsOnDisk() {
        assertEquals("/files/track.mp3", EnsureTrackDownloadPolicy.resolveLocalPathAfterFailure("/files/track.mp3"))
        assertNull(EnsureTrackDownloadPolicy.resolveLocalPathAfterFailure(null))
    }
}
