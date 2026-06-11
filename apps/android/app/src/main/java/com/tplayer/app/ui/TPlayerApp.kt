package com.tplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tplayer.app.domain.model.Book
import com.tplayer.app.domain.model.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TPlayerApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("TPlayer") })
        },
    ) { padding ->
        when {
            state.sessionState == SessionState.UNAUTHENTICATED -> AuthScreen(
                padding = padding,
                onLogin = viewModel::login,
                error = state.error,
            )
            state.selectedBook == null -> LibraryScreen(
                padding = padding,
                books = state.books,
                offlineBanner = state.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                onBookClick = viewModel::selectBook,
                onLogout = viewModel::logout,
            )
            else -> BookDetailScreen(
                padding = padding,
                book = state.selectedBook!!,
                tracks = state.tracks,
                progressLabel = state.progressLabel,
                nowPlayingTitle = state.nowPlayingTitle,
                isPlaying = state.isPlaying,
                downloadProgress = state.downloadProgress,
                onPlay = viewModel::playBook,
                onPause = viewModel::pausePlayback,
                onResume = viewModel::resumePlayback,
                onDownload = viewModel::downloadBook,
                onDeleteLocal = viewModel::deleteLocalDownloads,
                onBack = viewModel::clearSelection,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    padding: PaddingValues,
    books: List<Book>,
    offlineBanner: Boolean,
    onBookClick: (Book) -> Unit,
    onLogout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (offlineBanner) {
            Text(
                "No network — sync paused",
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books) { book ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookClick(book) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                        book.author?.let { Text(it) }
                        Text(book.contentType.name.lowercase())
                    }
                }
            }
        }
        Button(onClick = onLogout, modifier = Modifier.padding(12.dp)) { Text("Sign out") }
    }
}

@Composable
private fun BookDetailScreen(
    padding: PaddingValues,
    book: Book,
    tracks: List<com.tplayer.app.domain.model.Track>,
    progressLabel: String?,
    nowPlayingTitle: String?,
    isPlaying: Boolean,
    downloadProgress: Float?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDownload: () -> Unit,
    onDeleteLocal: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(book.title, style = MaterialTheme.typography.headlineSmall)
        progressLabel?.let { Text("Continue: $it") }
        nowPlayingTitle?.let { Text("Now playing: $it") }
        downloadProgress?.let { Text("Downloading: ${(it * 100).toInt()}%") }
        if (isPlaying) {
            Button(onClick = onPause) { Text("Pause") }
        } else {
            Button(onClick = {
                if (nowPlayingTitle != null) onResume() else onPlay()
            }) {
                Text(if (progressLabel != null && nowPlayingTitle == null) "Resume" else if (nowPlayingTitle != null) "Play" else "Play")
            }
        }
        Button(onClick = onDownload) { Text("Download") }
        Button(onClick = onDeleteLocal) { Text("Delete local files") }
        Button(onClick = onToggleFavorite) { Text("Toggle favorite") }
        Button(onClick = onBack) { Text("Back") }
        tracks.forEach { track ->
            val suffix = if (track.localPath != null) " [downloaded]" else ""
            Text("${track.sortOrder + 1}. ${track.title}$suffix")
        }
    }
}
