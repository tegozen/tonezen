package com.tonezen.app.ui.library

import androidx.compose.runtime.Composable
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.resolvedAuthor

@Composable
fun bookAuthorLabel(book: Book): String =
    book.resolvedAuthor() ?: "Автор не указан"
