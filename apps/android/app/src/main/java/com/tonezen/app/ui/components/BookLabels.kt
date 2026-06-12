package com.tonezen.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.resolvedAuthor

@Composable
fun bookAuthorLabel(book: Book): String =
    book.resolvedAuthor() ?: stringResource(R.string.author_placeholder)
