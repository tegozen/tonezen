package com.tplayer.app.domain.progress

import com.tplayer.app.data.local.AudiobookProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressMergerTest {
    private val older = AudiobookProgressEntity("b1", "t1", 1000, 1_000L)
    private val newer = AudiobookProgressEntity("b1", "t2", 2000, 2_000L)

    @Test
    fun returnsNewerOnConflict() {
        assertEquals(newer, ProgressMerger.merge(older, newer))
        assertEquals(newer, ProgressMerger.merge(newer, older))
    }

    @Test
    fun returnsRemoteWhenLocalMissing() {
        assertEquals(newer, ProgressMerger.merge(null, newer))
    }
}
