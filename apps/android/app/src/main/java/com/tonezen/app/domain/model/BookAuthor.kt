package com.tonezen.app.domain.model

fun normalizeAuthor(author: String?): String? =
    author
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

fun Book.resolvedAuthor(): String? = normalizeAuthor(author)
