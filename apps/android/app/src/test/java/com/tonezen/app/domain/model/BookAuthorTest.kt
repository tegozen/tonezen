package com.tonezen.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookAuthorTest {
    @Test
    fun normalizeAuthor_rejectsNullLiteral() {
        assertNull(normalizeAuthor("null"))
        assertNull(normalizeAuthor("NULL"))
    }

    @Test
    fun normalizeAuthor_keepsRealName() {
        assertEquals("А. Никл", normalizeAuthor("  А. Никл  "))
    }

    @Test
    fun normalizeAuthor_rejectsBlank() {
        assertNull(normalizeAuthor(""))
        assertNull(normalizeAuthor("   "))
        assertNull(null)
    }
}
