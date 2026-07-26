package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicPlaybackAdvanceRules
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.playback.MusicPlaybackQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Владеет контекстом альбома/плейлиста (очередь "Далее", шафл) для экрана Now Playing. */
internal class NowPlayingCatalogContext(
    private val uiState: MutableStateFlow<NowPlayingUiState>,
    private val catalogRepository: CatalogRepository,
    private val musicPlaybackQueue: MusicPlaybackQueue,
    private val networkMonitor: NetworkMonitor,
) {
    var libraryTracks: List<AlbumTrackEntry> = emptyList()
    var currentTrackIndex: Int = -1
    var preserveShuffleOrder: Boolean = false

    suspend fun refreshUpNext(activeTrackId: String) {
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
        val waveformPeaks = catalogRepository.findTrackInCatalog(activeTrackId)?.waveformPeaks
            ?: entries[index].track.waveformPeaks
        uiState.update {
            it.copy(
                activeBook = entries[index].book,
                contentType = book.contentType,
                waveformPeaks = waveformPeaks,
                upNext = if (book.contentType == ContentType.MUSIC) {
                    musicUpNext(index, entries.size)
                } else {
                    entries.drop(index + 1).map { entry -> entry.track }
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

    fun musicUpNext(index: Int, trackCount: Int): List<Track> {
        if (trackCount <= 1) return emptyList()
        val entries = libraryTracks
        return (1 until trackCount.coerceAtMost(4)).map { offset ->
            entries[(index + offset) % trackCount].track
        }
    }

    suspend fun updateAlbumNavigation(index: Int, book: Book) {
        currentTrackIndex = index
        val trackId = libraryTracks.getOrNull(index)?.track?.id
        val waveformPeaks = trackId?.let { catalogRepository.findTrackInCatalog(it)?.waveformPeaks }
            ?: libraryTracks.getOrNull(index)?.track?.waveformPeaks
        uiState.update {
            it.copy(
                activeBook = book,
                waveformPeaks = waveformPeaks,
                upNext = when (book.contentType) {
                    ContentType.MUSIC -> musicUpNext(index, libraryTracks.size)
                    else -> libraryTracks.drop(index + 1).map { entry -> entry.track }
                },
            )
        }
    }

    fun clearAlbumContext() {
        libraryTracks = emptyList()
        currentTrackIndex = -1
        preserveShuffleOrder = false
        uiState.update {
            it.copy(upNext = emptyList(), waveformPeaks = null)
        }
    }

    fun isAlbumEntryPlayable(entry: AlbumTrackEntry): Boolean {
        val hasLocal = !entry.track.localPath.isNullOrBlank()
        return MusicPlaybackAdvanceRules.isTrackPlayable(
            isDownloaded = hasLocal,
            isNetworkOnline = networkMonitor.isOnline(),
        )
    }
}
