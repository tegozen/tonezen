package com.tonezen.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaveformPeaksTest {
    private val validPeaks = List(WaveformPeakCount) { it }

    @Test
    fun waveformPeaksFromJsonParsesValidIntegerArray() {
        val json = JSONArray(validPeaks).toString()

        assertEquals(validPeaks, waveformPeaksFromJson(json))
    }

    @Test
    fun waveformPeaksFromJsonRejectsMissingOrInvalidArrays() {
        assertNull(waveformPeaksFromJson(null))
        assertNull(waveformPeaksFromJson("[]"))
        assertNull(waveformPeaksFromJson("not-json"))
    }

    @Test
    fun waveformPeaksFromJsonArrayRejectsOutOfRangeAndNonIntegerValues() {
        val outOfRange = MutableList(WaveformPeakCount) { 0 }
        outOfRange[10] = 101

        assertNull(waveformPeaksFromJsonArray(JSONArray(outOfRange)))
        assertNull(waveformPeaksFromJsonArray(JSONArray(List(WaveformPeakCount) { "5" })))
        assertNull(waveformPeaksFromJsonArray(JSONArray(List(WaveformPeakCount) { 0.5 })))
    }

    @Test
    fun waveformPeaksToJsonSerializesOnlyValidArrays() {
        assertEquals(JSONArray(validPeaks).toString(), waveformPeaksToJson(validPeaks))
        assertNull(waveformPeaksToJson(listOf(0, 100)))
        assertNull(waveformPeaksToJson(List(WaveformPeakCount) { -1 }))
    }
}
