package com.tonezen.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.ui.components.BookCover
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.EmptyLibrary
import com.tonezen.app.ui.components.IconCircle
import com.tonezen.app.ui.components.MiniPlayer
import com.tonezen.app.ui.components.OfflineBanner
import com.tonezen.app.ui.components.OverflowGlyph
import com.tonezen.app.ui.components.SearchGlyph
import com.tonezen.app.ui.components.SearchRow
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.TonezenBottomNavigation
import com.tonezen.app.ui.components.TonezenTabs
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun LibraryScreen(
    books: List<Book>,
    downloadedBookIds: Set<String>,
    offlineBanner: Boolean,
    nowPlayingTitle: String?,
    onBookClick: (Book) -> Unit,
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val audiobooks = books.filter { it.contentType == ContentType.AUDIOBOOK }
    val music = books.filter { it.contentType == ContentType.MUSIC }
    val tabBooks = if (selectedTab == 0) audiobooks else music

    Scaffold(
        containerColor = TonezenAppBg,
        bottomBar = {
            Column {
                MiniPlayer(
                    title = nowPlayingTitle ?: audiobooks.firstOrNull()?.title,
                    subtitle = nowPlayingTitle?.let { stringResource(R.string.now_playing) }
                        ?: audiobooks.firstOrNull()?.author,
                    enabled = books.isNotEmpty(),
                )
                TonezenBottomNavigation(selected = BottomDestination.Library)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(TonezenScreenBrush)
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                LibraryHeader(onLogout = onLogout)
            }
            if (offlineBanner) {
                item {
                    OfflineBanner()
                }
            }
            item {
                TonezenTabs(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
            item {
                SearchRow()
            }
            if (books.isEmpty()) {
                item {
                    EmptyLibrary()
                }
            } else {
                item {
                    LibrarySection(
                        title = if (selectedTab == 0) {
                            stringResource(R.string.tab_audiobooks)
                        } else {
                            stringResource(R.string.tab_music)
                        },
                        books = tabBooks,
                        downloadedBookIds = downloadedBookIds,
                        onBookClick = onBookClick,
                    )
                }
                if (selectedTab == 0 && music.isNotEmpty()) {
                    item {
                        LibrarySection(
                            title = stringResource(R.string.tab_music),
                            books = music,
                            downloadedBookIds = downloadedBookIds,
                            onBookClick = onBookClick,
                        )
                    }
                }
                if (selectedTab == 1 && audiobooks.isNotEmpty()) {
                    item {
                        LibrarySection(
                            title = stringResource(R.string.tab_audiobooks),
                            books = audiobooks,
                            downloadedBookIds = downloadedBookIds,
                            onBookClick = onBookClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = TonezenInk,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircle { SearchGlyph() }
            IconCircle { OverflowGlyph() }
            TextButton(onClick = onLogout) {
                Text(stringResource(R.string.sign_out), color = TonezenMuted)
            }
        }
    }
}

@Composable
private fun LibrarySection(
    title: String,
    books: List<Book>,
    downloadedBookIds: Set<String>,
    onBookClick: (Book) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TonezenInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.see_all), color = TonezenTeal, style = MaterialTheme.typography.labelMedium)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(books) { book ->
                LibraryBookCard(
                    book = book,
                    downloaded = downloadedBookIds.contains(book.id),
                    onClick = { onBookClick(book) },
                )
            }
        }
    }
}

@Composable
private fun LibraryBookCard(book: Book, downloaded: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(154.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            BookCover(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f),
            )
            if (downloaded) {
                StatusChip(
                    label = stringResource(R.string.offline),
                    tone = TonezenTeal,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = book.title,
                    color = TonezenInk,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author.orEmpty(),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(":", color = TonezenMuted, style = MaterialTheme.typography.titleMedium)
        }
    }
}
