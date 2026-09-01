package com.tonezen.app.domain.model

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

private val windows1251: Charset = Charset.forName("windows-1251")

private fun mojibakeScore(value: String): Int {
    var score = value.count { it == '\uFFFD' } * 10 + value.count { it == 'Ð' || it == 'Ñ' } * 2
    for (index in 0 until value.lastIndex) {
        if ((value[index] == 'Р' || value[index] == 'С') && value[index + 1] in '\u0400'..'\u04ff') score += 4
        if ((value[index] == 'Ð' || value[index] == 'Ñ') && value[index + 1].code in 0x80..0xff) score += 4
    }
    return score
}

private val cp1252Special = mapOf(
    '€' to 0x80, '‚' to 0x82, 'ƒ' to 0x83, '„' to 0x84, '…' to 0x85, '†' to 0x86,
    '‡' to 0x87, 'ˆ' to 0x88, '‰' to 0x89, 'Š' to 0x8a, '‹' to 0x8b, 'Œ' to 0x8c,
    'Ž' to 0x8e, '‘' to 0x91, '’' to 0x92, '“' to 0x93, '”' to 0x94, '•' to 0x95,
    '–' to 0x96, '—' to 0x97, '˜' to 0x98, '™' to 0x99, 'š' to 0x9a, '›' to 0x9b,
    'œ' to 0x9c, 'ž' to 0x9e, 'Ÿ' to 0x9f,
)

private fun westernBytes(value: String): ByteArray? {
    val result = ByteArray(value.length)
    value.forEachIndexed { index, char ->
        val byte = if (char.code <= 0xff) char.code else cp1252Special[char] ?: return null
        result[index] = byte.toByte()
    }
    return result
}

private fun decodeUtf8(bytes: ByteArray): String? = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

/** Repairs UTF-8 text that legacy tags or paths exposed as Windows-1251/Latin-1. */
fun repairMojibake(value: String): String {
    val original = value.trim()
    if (original.isEmpty() || mojibakeScore(original) == 0) return original
    val candidates = listOfNotNull(
        decodeUtf8(original.toByteArray(windows1251)),
        westernBytes(original)?.let(::decodeUtf8),
    ).filterNot { it.contains('\uFFFD') }
    return candidates.minByOrNull(::mojibakeScore)
        ?.takeIf { mojibakeScore(it) < mojibakeScore(original) }
        ?.trim()
        ?: original
}
