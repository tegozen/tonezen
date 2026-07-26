package com.tonezen.app.ui.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumCoverMathTest {
    @Test
    fun buildsStableBarsForSameSeed() {
        val first = buildSpectrumBars("track-miyagi-1")
        val second = buildSpectrumBars("track-miyagi-1")

        assertEquals(first, second)
        assertEquals(SpectrumBarCount, first.size)
    }

    @Test
    fun keepsBarsInsideExpectedRange() {
        val bars = buildSpectrumBars("track-miyagi-2")

        assertTrue(bars.all { it.level in 2..9 })
        assertTrue(bars.all { it.delayStep in 0..7 })
    }

    @Test
    fun keepsRenderedHeightInsideExpectedRange() {
        val bars = buildSpectrumBars("track-miyagi-3")
        val heights = bars.mapIndexed { index, bar ->
            spectrumBarHeightFraction(
                bar = bar,
                index = index,
                phase = 1.5f,
                isPlaying = true,
            )
        }

        assertTrue(heights.all { it in 0.22f..0.96f })
    }
}
