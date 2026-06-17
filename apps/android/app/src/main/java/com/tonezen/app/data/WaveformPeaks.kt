package com.tonezen.app.data

import org.json.JSONArray

internal const val WaveformPeakCount = 64

internal fun normalizeWaveformPeaks(peaks: List<Int>?): List<Int>? =
    peaks?.takeIf { values ->
        values.size == WaveformPeakCount && values.all { it in 0..100 }
    }

internal fun waveformPeaksFromJsonArray(array: JSONArray?): List<Int>? {
    if (array == null || array.length() != WaveformPeakCount) return null
    val peaks = mutableListOf<Int>()
    for (index in 0 until array.length()) {
        val number = array.opt(index) as? Number ?: return null
        val doubleValue = number.toDouble()
        if (doubleValue.isNaN() || doubleValue.isInfinite() || doubleValue % 1.0 != 0.0) return null
        val value = number.toInt()
        if (value !in 0..100) return null
        peaks.add(value)
    }
    return peaks
}

internal fun waveformPeaksFromJson(raw: String?): List<Int>? {
    if (raw.isNullOrBlank()) return null
    return runCatching { waveformPeaksFromJsonArray(JSONArray(raw)) }.getOrNull()
}

internal fun waveformPeaksToJson(peaks: List<Int>?): String? =
    normalizeWaveformPeaks(peaks)?.let { JSONArray(it).toString() }
