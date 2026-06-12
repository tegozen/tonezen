package com.tonezen.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.model.Track

private val TonezenInk = Color(0xFFF8FAFC)
private val TonezenMuted = Color(0xFF94A3B8)
private val TonezenFaint = Color(0xFF64748B)
private val TonezenAppBg = Color(0xFF020617)
private val TonezenSurface = Color(0xFF0F172A)
private val TonezenSurfaceRaised = Color(0xFF172033)
private val TonezenSurfaceMuted = Color(0xFF1E293B)
private val TonezenBorder = Color(0xFF334155)
private val TonezenTeal = Color(0xFF5EEAD4)
private val TonezenTealStrong = Color(0xFF14B8A6)
private val TonezenAmber = Color(0xFFFCD34D)
private val TonezenGreen = Color(0xFF4ADE80)
private val TonezenError = Color(0xFFF87171)

private val TonezenColorScheme = darkColorScheme(
    primary = TonezenTeal,
    onPrimary = TonezenAppBg,
    secondary = TonezenAmber,
    background = TonezenAppBg,
    onBackground = TonezenInk,
    surface = TonezenSurface,
    onSurface = TonezenInk,
    surfaceVariant = TonezenSurfaceRaised,
    onSurfaceVariant = TonezenMuted,
    error = TonezenError,
)

@Composable
fun TonezenApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    TonezenTheme {
        when {
            state.sessionState == SessionState.UNAUTHENTICATED -> AuthScreen(
                padding = PaddingValues(0.dp),
                onLogin = viewModel::login,
                error = state.error,
            )

            state.selectedBook == null -> LibraryScreen(
                books = state.books,
                downloadedBookIds = state.downloadedBookIds,
                offlineBanner = state.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                nowPlayingTitle = state.nowPlayingTitle,
                onBookClick = viewModel::selectBook,
                onLogout = viewModel::logout,
            )

            else -> BookDetailScreen(
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
private fun TonezenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TonezenColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
private fun LibraryScreen(
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
private fun IconCircle(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun TonezenTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TonezenTab(
            label = stringResource(R.string.tab_audiobooks),
            selected = selectedTab == 0,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(0) },
        )
        TonezenTab(
            label = stringResource(R.string.tab_music),
            selected = selectedTab == 1,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(1) },
        )
    }
}

@Composable
private fun TonezenTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) TonezenTeal else TonezenMuted,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) TonezenTeal else TonezenBorder.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun SearchRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TonezenSurfaceRaised.copy(alpha = 0.92f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchGlyph()
            Text(
                text = stringResource(R.string.search_library),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TonezenSurfaceRaised.copy(alpha = 0.92f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.filter), color = TonezenInk, style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun BookDetailScreen(
    book: Book,
    tracks: List<Track>,
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
    val hasDownloadedTracks = tracks.any { it.localPath != null }
    val currentTrackTitle = nowPlayingTitle ?: progressLabel?.removePrefix("${stringResource(R.string.continue_label)}: ")
    val selectedChapterIndex = tracks.indexOfFirst { it.title == currentTrackTitle }.takeIf { it >= 0 } ?: 0

    Scaffold(
        containerColor = TonezenAppBg,
        bottomBar = { TonezenBottomNavigation(selected = BottomDestination.Player) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(TonezenScreenBrush)
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                PlayerHeader(onBack = onBack)
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .aspectRatio(1f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = book.title,
                            color = TonezenInk,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = book.author.orEmpty(),
                            color = TonezenMuted,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (hasDownloadedTracks) {
                            StatusChip(label = stringResource(R.string.offline), tone = TonezenTeal)
                        }
                        if (book.contentType == ContentType.AUDIOBOOK) {
                            StatusChip(label = stringResource(R.string.synced), tone = TonezenGreen)
                        }
                    }
                    downloadProgress?.let {
                        Text(
                            text = stringResource(R.string.downloading_percent, (it * 100).toInt()),
                            color = TonezenAmber,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            item {
                PlayerControls(
                    chapterLabel = tracks.getOrNull(selectedChapterIndex)?.let {
                        stringResource(R.string.chapter_label, it.sortOrder + 1)
                    } ?: stringResource(R.string.chapter_fallback),
                    isPlaying = isPlaying,
                    canResume = nowPlayingTitle != null,
                    onPlay = onPlay,
                    onPause = onPause,
                    onResume = onResume,
                )
            }
            item {
                PlayerActions(
                    onDownload = onDownload,
                    onDeleteLocal = onDeleteLocal,
                    onToggleFavorite = onToggleFavorite,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.tracks),
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(tracks) { track ->
                TrackRow(
                    track = track,
                    selected = track.title == currentTrackTitle || track.sortOrder == selectedChapterIndex,
                )
            }
        }
    }
}

@Composable
private fun PlayerHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back), color = TonezenInk)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(TonezenSurfaceRaised.copy(alpha = 0.85f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp)),
        ) {
            SegmentPill(label = stringResource(R.string.nav_player), selected = true)
            SegmentPill(label = stringResource(R.string.details), selected = false)
        }
        IconCircle { OverflowGlyph() }
    }
}

@Composable
private fun SegmentPill(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TonezenTeal.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) TonezenTeal else TonezenMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PlayerControls(
    chapterLabel: String,
    isPlaying: Boolean,
    canResume: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueGlyph()
            RoundControl(label = stringResource(R.string.rewind_15), outlined = true, onClick = {})
            PlayButton(
                isPlaying = isPlaying,
                onClick = {
                    when {
                        isPlaying -> onPause()
                        canResume -> onResume()
                        else -> onPlay()
                    }
                },
            )
            RoundControl(label = stringResource(R.string.forward_15), outlined = true, onClick = {})
            Text(stringResource(R.string.speed_normal), color = TonezenInk, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = chapterLabel,
            color = TonezenInk,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        ProgressBar(progress = 0.42f)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("18:35", color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            Text("-21:40", color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlayerActions(onDownload: () -> Unit, onDeleteLocal: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(label = stringResource(R.string.download), onClick = onDownload, modifier = Modifier.weight(1f))
        ActionButton(label = stringResource(R.string.toggle_favorite), onClick = onToggleFavorite, modifier = Modifier.weight(1f))
        ActionButton(label = stringResource(R.string.delete_local_files), onClick = onDeleteLocal, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        border = BorderStroke(1.dp, TonezenBorder),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TonezenInk),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TrackRow(track: Track, selected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TonezenAmber.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                BorderStroke(1.dp, if (selected) TonezenAmber.copy(alpha = 0.18f) else TonezenBorder.copy(alpha = 0.35f)),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayingBars(active = selected)
        Text(
            text = (track.sortOrder + 1).toString(),
            color = if (selected) TonezenAmber else TonezenMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (selected) TonezenAmber else TonezenInk,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.localPath != null) {
                Text(stringResource(R.string.offline), color = TonezenTeal, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(durationLabel(track.durationMs), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MiniPlayer(title: String?, subtitle: String?, enabled: Boolean) {
    if (!enabled || title == null) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        color = TonezenSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniCover()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TonezenInk, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle.orEmpty(), color = TonezenMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            PlayTriangle(tint = TonezenInk, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TonezenBottomNavigation(selected: BottomDestination) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TonezenSurface.copy(alpha = 0.96f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(BottomDestination.Library, selected)
        BottomNavItem(BottomDestination.Player, selected)
        BottomNavItem(BottomDestination.Downloads, selected)
        BottomNavItem(BottomDestination.Profile, selected)
    }
}

@Composable
private fun BottomNavItem(destination: BottomDestination, selected: BottomDestination) {
    val active = destination == selected
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(if (destination == BottomDestination.Player) CircleShape else RoundedCornerShape(6.dp))
                .background(if (active) TonezenTeal.copy(alpha = 0.95f) else Color.Transparent)
                .border(BorderStroke(1.dp, if (active) TonezenTeal else TonezenMuted.copy(alpha = 0.7f)), if (destination == BottomDestination.Player) CircleShape else RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(destination.glyph, color = if (active) TonezenAppBg else TonezenMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = stringResource(destination.labelRes),
            color = if (active) TonezenTeal else TonezenMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private enum class BottomDestination(val labelRes: Int, val glyph: String) {
    Library(R.string.nav_library, "L"),
    Player(R.string.nav_player, "P"),
    Downloads(R.string.nav_downloads, "D"),
    Profile(R.string.nav_profile, "U"),
}

@Composable
private fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val brush = remember(book.id) { coverBrush(book) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.minDimension * 0.32f,
                center = Offset(size.width * 0.82f, size.height * 0.18f),
            )
            drawCircle(
                color = TonezenTeal.copy(alpha = 0.08f),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.18f, size.height * 0.88f),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = book.title.uppercase(),
                color = if (book.contentType == ContentType.AUDIOBOOK) Color(0xFFFFE7BA) else TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author.orEmpty(),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, tone: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.17f))
            .border(BorderStroke(1.dp, tone.copy(alpha = 0.28f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tone),
        )
        Text(label, color = tone, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TonezenAmber.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, TonezenAmber.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.no_network_sync_paused), color = TonezenAmber, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurfaceRaised.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, TonezenBorder), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.empty_library_title), color = TonezenInk, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.empty_library_body), color = TonezenMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AuthScreen(
    padding: PaddingValues,
    onLogin: (String, String) -> Unit,
    error: String?,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotBlank()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TonezenScreenBrush)
            .padding(padding),
        contentPadding = PaddingValues(start = 20.dp, top = 34.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AuthBrandHeader()
        }
        item {
            AuthHeroPanel()
        }
        item {
            AuthFormCard(
                email = email,
                password = password,
                error = error,
                canSubmit = canSubmit,
                onEmailChange = { email = it },
                onPasswordChange = { password = it },
                onSubmit = { onLogin(email.trim(), password) },
            )
        }
        item {
            AuthFooterNote()
        }
    }
}

@Composable
private fun AuthBrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(TonezenTeal.copy(alpha = 0.28f), TonezenSurfaceRaised)))
                    .border(BorderStroke(1.dp, TonezenTeal.copy(alpha = 0.36f)), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                PlayingBars(active = true)
            }
            Column {
                Text(
                    stringResource(R.string.app_name),
                    color = TonezenInk,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.auth_eyebrow),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        StatusChip(label = stringResource(R.string.offline), tone = TonezenTeal)
    }
}

@Composable
private fun AuthHeroPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TonezenSurface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.auth_headline),
                    color = TonezenInk,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.auth_body),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthPill(stringResource(R.string.auth_sync_badge), TonezenGreen)
                    AuthPill(stringResource(R.string.auth_offline_badge), TonezenAmber)
                }
            }
            AuthMediaStack()
        }
    }
}

@Composable
private fun AuthPill(label: String, tone: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, tone.copy(alpha = 0.24f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, color = tone, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun AuthMediaStack() {
    Box(modifier = Modifier.size(width = 104.dp, height = 132.dp)) {
        AuthMiniCover(
            title = "AUDIO",
            brush = Brush.verticalGradient(listOf(Color(0xFF061826), Color(0xFF14324A))),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 76.dp, height = 104.dp),
        )
        AuthMiniCover(
            title = "MIX",
            brush = Brush.verticalGradient(listOf(Color(0xFF173B39), Color(0xFF5EEAD4))),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = 72.dp, height = 92.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5E9D6)),
            contentAlignment = Alignment.Center,
        ) {
            PlayTriangle(tint = TonezenAppBg, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun AuthMiniCover(title: String, brush: Brush, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(12.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = TonezenInk,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AuthFormCard(
    email: String,
    password: String,
    error: String?,
    canSubmit: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TonezenSurfaceRaised.copy(alpha = 0.88f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.auth_card_title),
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.auth_card_body),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TonezenAuthField(
                value = email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.email),
                keyboardType = KeyboardType.Email,
            )
            TonezenAuthField(
                value = password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.password),
                keyboardType = KeyboardType.Password,
                hidden = true,
            )
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TonezenTeal,
                    contentColor = TonezenAppBg,
                    disabledContainerColor = TonezenSurfaceMuted,
                    disabledContentColor = TonezenMuted,
                ),
            ) {
                Text(stringResource(R.string.sign_in), fontWeight = FontWeight.SemiBold)
            }
            error?.let {
                Text(it, color = TonezenError, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TonezenAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    hidden: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TonezenInk,
            unfocusedTextColor = TonezenInk,
            focusedContainerColor = TonezenAppBg.copy(alpha = 0.50f),
            unfocusedContainerColor = TonezenAppBg.copy(alpha = 0.34f),
            focusedBorderColor = TonezenTeal,
            unfocusedBorderColor = TonezenBorder,
            focusedLabelColor = TonezenTeal,
            unfocusedLabelColor = TonezenMuted,
            cursorColor = TonezenTeal,
        ),
    )
}

@Composable
private fun AuthFooterNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurface.copy(alpha = 0.64f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusChip(label = stringResource(R.string.paused), tone = TonezenAmber)
        Text(
            stringResource(R.string.offline_playback_note),
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(color = TonezenMuted, radius = size.minDimension * 0.34f, style = Stroke(width = 2.4f))
        drawLine(
            color = TonezenMuted,
            start = Offset(size.width * 0.68f, size.height * 0.68f),
            end = Offset(size.width * 0.95f, size.height * 0.95f),
            strokeWidth = 2.4f,
        )
    }
}

@Composable
private fun OverflowGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val dotRadius = size.minDimension * 0.09f
        listOf(0.25f, 0.50f, 0.75f).forEach { y ->
            drawCircle(
                color = TonezenMuted,
                radius = dotRadius,
                center = Offset(size.width * 0.5f, size.height * y),
            )
        }
    }
}

@Composable
private fun QueueGlyph() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(2.dp)
                    .background(TonezenInk.copy(alpha = 0.9f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun RoundControl(label: String, outlined: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(BorderStroke(if (outlined) 1.4.dp else 0.dp, TonezenInk), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TonezenInk, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xFFF5E9D6))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(width = 6.dp, height = 26.dp).background(TonezenAppBg, RoundedCornerShape(2.dp)))
                Box(Modifier.size(width = 6.dp, height = 26.dp).background(TonezenAppBg, RoundedCornerShape(2.dp)))
            }
        } else {
            PlayTriangle(tint = TonezenAppBg, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun PlayTriangle(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.18f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            lineTo(size.width * 0.82f, size.height * 0.50f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(TonezenAmber),
        )
    }
}

@Composable
private fun PlayingBars(active: Boolean) {
    Row(
        modifier = Modifier.width(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(8.dp, 14.dp, 10.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (active) height else 3.dp)
                    .background(if (active) TonezenAmber else TonezenMuted, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun MiniCover() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0B2535), Color(0xFF14213D))))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.app_name).take(1), color = TonezenAmber, fontWeight = FontWeight.Bold)
    }
}

private val TonezenScreenBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF070B0F),
        TonezenAppBg,
        Color(0xFF071016),
    ),
)

private fun coverBrush(book: Book): Brush {
    val variants = if (book.contentType == ContentType.AUDIOBOOK) {
        listOf(
            listOf(Color(0xFF061826), Color(0xFF102A43), Color(0xFF0B1120)),
            listOf(Color(0xFF33210E), Color(0xFFC78538), Color(0xFFFAECD2)),
            listOf(Color(0xFF461C12), Color(0xFFD94D28), Color(0xFF7F1D1D)),
        )
    } else {
        listOf(
            listOf(Color(0xFF103344), Color(0xFF9BD6E3), Color(0xFF0F172A)),
            listOf(Color(0xFF1D1712), Color(0xFF70513A), Color(0xFF111827)),
            listOf(Color(0xFF0F3B39), Color(0xFF69B3A2), Color(0xFF10201F)),
        )
    }
    val index = kotlin.math.abs(book.id.hashCode()) % variants.size
    return Brush.verticalGradient(variants[index])
}

private fun durationLabel(durationMs: Long?): String {
    val totalSeconds = durationMs?.div(1000) ?: return "--:--"
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
