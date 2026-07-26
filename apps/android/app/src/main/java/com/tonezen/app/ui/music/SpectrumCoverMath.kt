package com.tonezen.app.ui.music

import kotlin.math.sin

internal const val SpectrumBarCount = 26

internal data class SpectrumBar(
    val level: Int,
    val delayStep: Int,
)

internal fun buildSpectrumBars(
    seed: String,
    count: Int = SpectrumBarCount,
): List<SpectrumBar> {
    if (count <= 0) return emptyList()
    var state = hashSpectrumSeed(seed)
    return List(count) { index ->
        state = mixSpectrumState(state + index)
        val positive = state and Int.MAX_VALUE
        SpectrumBar(
            level = 2 + positive % 8,
            delayStep = (positive ushr 4) % 8,
        )
    }
}

internal fun spectrumBarHeightFraction(
    bar: SpectrumBar,
    index: Int,
    phase: Float,
    isPlaying: Boolean,
): Float {
    val base = bar.level / 10f
    if (!isPlaying) {
        return (0.22f + base * 0.62f).coerceIn(0.2f, 0.92f)
    }
    val wave = ((sin(phase + index * 0.72f + bar.delayStep * 0.21f) + 1f) / 2f)
    val shimmer = ((sin(phase * 0.55f + index * 1.37f) + 1f) / 2f)
    return (0.18f + base * 0.52f + wave * 0.22f + shimmer * 0.08f).coerceIn(0.22f, 0.96f)
}

private fun hashSpectrumSeed(seed: String): Int {
    var hash = FNV_OFFSET
    seed.forEach { char ->
        hash = (hash xor char.code) * FNV_PRIME
    }
    return hash
}

private fun mixSpectrumState(value: Int): Int {
    var state = value
    state = state xor (state ushr 16)
    state *= MIX_A
    state = state xor (state ushr 13)
    state *= MIX_B
    return state xor (state ushr 16)
}

private const val FNV_OFFSET = -2128831035
private const val FNV_PRIME = 16777619
private const val MIX_A = -2048144789
private const val MIX_B = -1028477387
