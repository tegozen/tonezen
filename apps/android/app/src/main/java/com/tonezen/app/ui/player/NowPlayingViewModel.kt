package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.playback.MusicDownloadNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

data class NowPlayingUiState(
    val title: String? = null,
    val subtitle: String? = null,
    val coverSeed: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val contentType: ContentType? = null,
    val activeBook: Book? = null,
    val upNext: List<Track> = emptyList(),
    val canSkipPrevious: Boolean = true,
    val canSkipNext: Boolean = false,
)

private data class AlbumTrackEntry(
    val book: Book,
    val track: Track,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackClient: PlaybackClient,
    private val catalogRepository: CatalogRepository,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val playbackEvents: PlaybackEvents,
    private val musicDownloadNotifier: MusicDownloadNotifier,
    private val musicPlaybackQueue: MusicPlaybackQueue,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    private var libraryTracks: List<AlbumTrackEntry> = emptyList()
    private var currentTrackIndex: Int = -1
    private var playJob: Job? = null
    private var catalogJob: Job? = null
    private var preserveShuffleOrder = false

    init {
        playbackClient.connect()
        viewModelScope.launch {
            var lastCatalogTrackId: String? = null
            playbackClient.snapshot.collect { snapshot ->
                val downloading = musicDownloadNotifier.state.value.isTrackDownloading
                _uiState.update {
                    it.copy(
                        title = if (downloading) it.title else snapshot.trackTitle,
                        subtitle = if (downloading) it.subtitle else formatSubtitle(snapshot.artist, snapshot.albumTitle),
                        coverSeed = if (downloading) {
                            it.coverSeed
                        } else {
                            snapshot.trackId ?: snapshot.trackTitle
                        },
                        isPlaying = if (downloading) false else snapshot.isPlaying,
                        positionMs = if (downloading) it.positionMs else snapshot.positionMs,
                        durationMs = if (downloading) it.durationMs else snapshot.durationMs,
                        contentType = snapshot.contentType ?: it.contentType,
                    )
                }
                val trackId = snapshot.trackId
                if (
                    trackId != null &&
                    trackId != lastCatalogTrackId &&
                    !musicDownloadNotifier.state.value.isTrackDownloading
                ) {
                    lastCatalogTrackId = trackId
                    scheduleAlbumRefresh(trackId)
                }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect {
                if (_uiState.value.contentType == ContentType.MUSIC && libraryTracks.size > 1) {
                    skipNext()
                }
            }
        }
    }

    fun refreshCatalogContext() {
        val trackId = playbackClient.snapshot.value.trackId ?: return
        preserveShuffleOrder = true
        scheduleAlbumRefresh(trackId)
    }

    private fun scheduleAlbumRefresh(trackId: String) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch(Dispatchers.IO) {
            refreshUpNext(trackId)
        }
    }

    fun pauseOrResume() {
        if (musicDownloadNotifier.state.value.isTrackDownloading) return
        if (_uiState.value.isPlaying) playbackClient.pause() else playbackClient.play()
    }

    fun seekTo(positionMs: Long) {
        playbackClient.seekTo(positionMs)
    }

    fun seekBy(deltaMs: Long) {
        playbackClient.seekBy(deltaMs)
    }

    fun skipPrevious() {
        if (musicDownloadNotifier.state.value.isTrackDownloading) return
        if (_uiState.value.contentType == ContentType.MUSIC && libraryTracks.size > 1) {
            if (_uiState.value.positionMs > 3_000L) {
                playbackClient.seekTo(0)
            } else {
                preserveShuffleOrder = true
                skipToIndex(MusicShuffleQueue.previousIndex(currentTrackIndex, libraryTracks.size))
            }
            return
        }
        when {
            currentTrackIndex > 0 -> skipToIndex(currentTrackIndex - 1)
            _uiState.value.positionMs > 3_000L -> playbackClient.seekTo(0)
            else -> playbackClient.seekTo(0)
        }
    }

    fun skipNext() {
        if (musicDownloadNotifier.state.value.isTrackDownloading) return
        if (libraryTracks.size <= 1) return
        if (_uiState.value.contentType == ContentType.MUSIC) {
            preserveShuffleOrder = true
            skipToIndex(MusicShuffleQueue.nextIndex(currentTrackIndex, libraryTracks.size))
            return
        }
        if (currentTrackIndex in 0 until libraryTracks.lastIndex) {
            skipToIndex(currentTrackIndex + 1)
        }
    }

    fun playTrack(track: Track) {
        if (musicDownloadNotifier.state.value.isTrackDownloading) return
        val index = libraryTracks.indexOfFirst { it.track.id == track.id }
        if (index >= 0) {
            preserveShuffleOrder = true
            skipToIndex(index)
        }
    }

    private fun skipToIndex(index: Int) {
        if (index !in libraryTracks.indices) return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            playQueueAt(index)
        }
    }

    private suspend fun playQueueAt(index: Int) {
        val entry = libraryTracks.getOrNull(index) ?: return
        val book = entry.book
        val target = entry.track
        val needsDownload = withContext(Dispatchers.IO) {
            target.localPath.isNullOrBlank() && !trackDownloadEnsurer.isTrackLocal(book.id, target.id)
        }

        if (needsDownload) {
            playbackClient.pause()
        }

        if (needsDownload) {
            musicDownloadNotifier.beginTrack(target.id)
        }
        _uiState.update {
            it.copy(
                title = target.title,
                subtitle = formatSubtitle(book.author, book.title),
                coverSeed = target.id,
                isPlaying = false,
            )
        }
        if (needsDownload) yield()

        val ensured = withContext(Dispatchers.IO) {
            val fresh = catalogRepository.getTracksForBook(book.id)
                .find { it.id == target.id } ?: target
            val progress = if (needsDownload) createProgressReporter(target.id) else null
            val outcome = trackDownloadEnsurer.ensureTrackLocal(book.id, fresh, progress)
            outcome.track != null
        }
        if (!ensured) {
            musicDownloadNotifier.finishTrack()
            return
        }

        musicDownloadNotifier.finishTrack()

        val queue = buildLocalQueue()
        if (queue.isEmpty()) return
        val startIndex = queue.indexOfFirst { it.trackId == target.id }.takeIf { it >= 0 } ?: return
        playbackClient.playQueue(queue, startIndex)
        updateAlbumNavigation(index, book)
    }

    private suspend fun buildLocalQueue() =
        playbackQueueBuilder.buildLocalMusicLibraryQueue(
            libraryTracks.map { MusicLibraryTrack(it.book, it.track) },
        ) { entry ->
            catalogRepository.getTracksForBook(entry.book.id)
                .find { it.id == entry.track.id }
                ?.takeIf { !it.localPath.isNullOrBlank() }
        }

    private fun updateAlbumNavigation(index: Int, book: Book) {
        currentTrackIndex = index
        _uiState.update {
            it.copy(
                activeBook = book,
                upNext = when (book.contentType) {
                    ContentType.MUSIC -> musicUpNext(index, libraryTracks.size)
                    else -> libraryTracks.drop(index + 1).map { entry -> entry.track }
                },
                canSkipPrevious = when (book.contentType) {
                    ContentType.MUSIC -> libraryTracks.size > 1
                    else -> index > 0 || it.positionMs > 3_000L
                },
                canSkipNext = when (book.contentType) {
                    ContentType.MUSIC -> libraryTracks.size > 1
                    else -> index < libraryTracks.lastIndex
                },
            )
        }
    }

    private fun musicUpNext(index: Int, trackCount: Int): List<Track> {
        if (trackCount <= 1) return emptyList()
        val entries = libraryTracks
        return (1 until trackCount.coerceAtMost(4)).map { offset ->
            entries[(index + offset) % trackCount].track
        }
    }

    private fun createProgressReporter(trackId: String): (Float) -> Unit {
        val reporter = object {
            var lastBucket = -1
        }
        return progress@{ progress ->
            val bucket = (progress * 50).toInt()
            if (bucket > reporter.lastBucket || progress >= 1f) {
                reporter.lastBucket = bucket
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    musicDownloadNotifier.updateTrack(trackId, progress)
                }
            }
        }
    }

    private suspend fun refreshUpNext(activeTrackId: String) {
        val book = catalogRepository.findBookForTrack(activeTrackId) ?: run {
            clearAlbumContext()
            return
        }

        val entries = when (book.contentType) {
            ContentType.MUSIC -> resolveMusicShuffle(activeTrackId)
            ContentType.AUDIOBOOK -> catalogRepository.getTracksForBook(book.id)
                .sortedBy { it.sortOrder }
                .map { AlbumTrackEntry(book, it) }
        }
        val index = entries.indexOfFirst { it.track.id == activeTrackId }
        if (index < 0) {
            clearAlbumContext()
            return
        }

        libraryTracks = entries
        currentTrackIndex = index
        _uiState.update {
            it.copy(
                activeBook = entries[index].book,
                contentType = book.contentType,
                upNext = if (book.contentType == ContentType.MUSIC) {
                    musicUpNext(index, entries.size)
                } else {
                    entries.drop(index + 1).map { entry -> entry.track }
                },
                canSkipPrevious = when (book.contentType) {
                    ContentType.MUSIC -> entries.size > 1
                    else -> index > 0 || it.positionMs > 3_000L
                },
                canSkipNext = when (book.contentType) {
                    ContentType.MUSIC -> entries.size > 1
                    else -> index < entries.lastIndex
                },
            )
        }
        preserveShuffleOrder = false
    }

    private suspend fun resolveMusicShuffle(activeTrackId: String): List<AlbumTrackEntry> {
        val sessionQueue = musicPlaybackQueue.get()
        if (sessionQueue.isNotEmpty()) {
            return sessionQueue.map { AlbumTrackEntry(it.book, it.track) }
        }

        val catalog = catalogRepository.resolveMusicLibraryTracks()
            .map { AlbumTrackEntry(it.book, it.track) }
        if (catalog.isEmpty()) return emptyList()

        val catalogIds = catalog.map { it.track.id }.toSet()
        val currentIds = libraryTracks.map { it.track.id }.toSet()
        val newIndex = libraryTracks.indexOfFirst { it.track.id == activeTrackId }
        val adjacentSkip = libraryTracks.isNotEmpty() && currentTrackIndex >= 0 && newIndex >= 0 && (
            newIndex == MusicShuffleQueue.nextIndex(currentTrackIndex, libraryTracks.size) ||
                newIndex == MusicShuffleQueue.previousIndex(currentTrackIndex, libraryTracks.size) ||
                newIndex == currentTrackIndex
            )

        val useExistingOrder = preserveShuffleOrder || adjacentSkip || (
            catalogIds == currentIds && newIndex >= 0
            )

        if (useExistingOrder && libraryTracks.isNotEmpty() && catalogIds == currentIds) {
            return libraryTracks
        }

        return MusicShuffleQueue.order(
            catalog.map { MusicLibraryTrack(it.book, it.track) },
            activeTrackId,
        ).map { AlbumTrackEntry(it.book, it.track) }
    }

    private fun clearAlbumContext() {
        libraryTracks = emptyList()
        currentTrackIndex = -1
        preserveShuffleOrder = false
        _uiState.update {
            it.copy(
                upNext = emptyList(),
                canSkipPrevious = true,
                canSkipNext = false,
            )
        }
    }

    private fun formatSubtitle(artist: String?, album: String?): String? {
        val cleanArtist = artist?.takeIf { it.isNotBlank() }
        val cleanAlbum = album?.takeIf { it.isNotBlank() }
        return when {
            cleanArtist != null && cleanAlbum != null -> "$cleanArtist · $cleanAlbum"
            cleanArtist != null -> cleanArtist
            cleanAlbum != null -> cleanAlbum
            else -> null
        }
    }
}
