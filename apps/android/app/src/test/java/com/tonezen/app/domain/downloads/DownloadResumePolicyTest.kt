package com.tonezen.app.domain.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadResumePolicyTest {
    @Test
    fun resolveResumeActionRangeAppendOn206() {
        assertEquals(
            DownloadResumePolicy.ResumeAction.RANGE_APPEND,
            DownloadResumePolicy.resolveResumeAction(100L, 100L, 1000L, 206),
        )
    }

    @Test
    fun resolveResumeActionRestartOn200() {
        assertEquals(
            DownloadResumePolicy.ResumeAction.RESTART,
            DownloadResumePolicy.resolveResumeAction(100L, 100L, 1000L, 200),
        )
    }

    @Test
    fun resolveResumeActionRestartWhenPartTooLarge() {
        assertEquals(
            DownloadResumePolicy.ResumeAction.RESTART,
            DownloadResumePolicy.resolveResumeAction(2000L, 2000L, 1000L, 206),
        )
    }
}
