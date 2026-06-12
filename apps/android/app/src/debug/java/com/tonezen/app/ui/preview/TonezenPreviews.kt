package com.tonezen.app.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.ui.auth.AuthScreen
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.theme.TonezenTheme

private val previewBooks = listOf(
    Book(
        id = "midnight",
        slug = "midnight-library",
        contentType = ContentType.AUDIOBOOK,
        title = "The Midnight Library",
        author = "Matt Haig",
    ),
    Book(
        id = "atomic",
        slug = "atomic-habits",
        contentType = ContentType.AUDIOBOOK,
        title = "Atomic Habits",
        author = "James Clear",
    ),
    Book(
        id = "piano",
        slug = "peaceful-piano",
        contentType = ContentType.MUSIC,
        title = "Peaceful Piano",
        author = "Various Artists",
    ),
)

@Preview(
    name = "Auth",
    showBackground = true,
    backgroundColor = 0xFF020617,
    device = Devices.PIXEL_7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AuthScreenPreview() {
    TonezenTheme {
        AuthScreen(
            padding = PaddingValues(0.dp),
            onLogin = { _, _ -> },
            error = null,
        )
    }
}

@Preview(
    name = "Auth / Error",
    showBackground = true,
    backgroundColor = 0xFF020617,
    device = Devices.PIXEL_7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AuthScreenErrorPreview() {
    TonezenTheme {
        AuthScreen(
            padding = PaddingValues(0.dp),
            onLogin = { _, _ -> },
            error = "Invalid email or password",
        )
    }
}

@Preview(
    name = "Library",
    showBackground = true,
    backgroundColor = 0xFF020617,
    device = Devices.PIXEL_7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun LibraryScreenPreview() {
    TonezenTheme {
        LibraryScreen(
            padding = PaddingValues(0.dp),
            books = previewBooks,
            allBooks = previewBooks,
            downloadedBookIds = setOf("midnight"),
            favoriteBookIds = emptySet(),
            offlineBanner = false,
            filter = LibraryFilterState(),
            showFilterSheet = false,
            onBookClick = {},
            onSearchChange = {},
            onFilterClick = {},
            onDismissFilterSheet = {},
            onApplyFilter = {},
            onResetFilter = {},
            onContentFilterChange = {},
            onSortOrderChange = {},
        )
    }
}
