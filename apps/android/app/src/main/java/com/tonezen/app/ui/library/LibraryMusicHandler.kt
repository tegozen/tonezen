package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.playback.MusicDownloadNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

internal class LibraryMusicHandler(
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val scope: CoroutineScope,
    private val session: LibraryPlaybackSession,
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val musicDownloadNotifier: MusicDownloadNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val musicPlaybackQueue: MusicPlaybackQueue,
    private val playbackErrorRes: (EnsureTrackOutcome.Failure?) -> Int,
    private val refreshCycleCardStates: suspend (List<com.tonezen.app.domain.model.Cycle>, Set<String>) -> Unit,
) {
    var playJob: Job? = null
    var downloadTrackJob: Job? = null
    var downloadAllJob: Job? = null
    var musicPrefetchJob: Job? = null

    fun onMusicTabSelected() {
        scope.launch {
            if (session.musicCandidates.isEmpty() || session.musicStartedInSession) return@launch
            if (uiState.value.musicTrackList.isNotEmpty()) return@launch
            uiState.update { it.copy(musicTrackList = buildMusicTrackList(shuffle = true)) }
        }
    }

    fun onMiniPlayerPlayPause() {
        val playback = uiState.value.musicPlayback
        if (!playback.isActive || playback.trackId == null) {
            if (playback.isPlaying) playbackClient.pause() else playbackClient.play()
            return
        }
        val listedTrack = uiState.value.musicTrackList.find { it.trackId == playback.trackId }
        if (listedTrack != null) {
            onMusicTrackClick(listedTrack)
            return
        }
        scope.launch {
            val track = resolvePlaybackTrack(playback) ?: return@launch
            onMusicTrackClick(track)
        }
    }

    fun onMusicTrackClick(track: MusicListTrack) {
        if (musicDownloadNotifier.state.value.isActive) return
        val playback = uiState.value.musicPlayback
        if (playback.trackId == track.trackId && playback.isActive) {
            if (playback.isPlaying) {
                playbackClient.pause()
            } else if (!track.isDownloaded) {
                playJob?.cancel()
                musicDownloadNotifier.beginTrack(track.trackId)
                uiState.update { it.copy(musicPlaybackErrorRes = null) }
                playJob = scope.launch {
                    playMusicTrack(track, showDownloadProgress = true)
                }
            } else {
                playbackClient.play()
            }
            return
        }
        playJob?.cancel()
        if (!track.isDownloaded) {
            musicDownloadNotifier.beginTrack(track.trackId)
        }
        uiState.update { it.copy(musicPlaybackErrorRes = null) }
        playJob = scope.launch {
            playMusicTrack(track, showDownloadProgress = !track.isDownloaded)
        }
    }

    fun downloadMusicTrack(track: MusicListTrack) {
        if (track.isDownloaded || musicDownloadNotifier.state.value.isActive) return
        downloadTrackJob?.cancel()
        downloadTrackJob = scope.launch {
            downloadTrackOnly(track)
        }
    }

    fun deleteMusicTrack(track: MusicListTrack) {
        if (musicDownloadNotifier.state.value.isActive) return
        scope.launch {
            val isPlaying = uiState.value.musicPlayback.trackId == track.trackId
            if (isPlaying) {
                playJob?.cancel()
                clearMusicPrefetchState()
                playbackClient.stopAndRelease()
                musicDownloadNotifier.clear()
            }
            withContext(Dispatchers.IO) {
                downloadRepository.deleteLocalTrack(track.bookId, track.trackId)
                catalogRepository.clearTrackLocalPath(track.bookId, track.trackId)
            }
            session.musicCandidates = session.musicCandidates.filterNot { it.second.id == track.trackId }
            session.musicLibraryTracks = session.musicLibraryTracks.filterNot { it.track.id == track.trackId }
            session.musicBookIdByTrackId = session.musicBookIdByTrackId - track.trackId
            uiState.update { state ->
                state.copy(
                    musicTrackList = state.musicTrackList.filterNot { it.trackId == track.trackId },
                    musicPlayback = if (isPlaying) MusicPlaybackUi() else state.musicPlayback,
                    musicPlaybackErrorRes = null,
                )
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
        }
    }

    fun downloadAllMusic() {
        if (musicDownloadNotifier.state.value.isActive) return
        val pending = uiState.value.musicTrackList.filter { !it.isDownloaded }
        if (pending.isEmpty()) return
        downloadAllJob?.cancel()
        downloadAllJob = scope.launch {
            val total = uiState.value.musicTrackList.size
            var completed = uiState.value.musicTrackList.count { it.isDownloaded }
            musicDownloadNotifier.beginBulk(completed, total)
            for (item in pending) {
                val track = withContext(Dispatchers.IO) {
                    catalogRepository.getTracksForBook(item.bookId).find { it.id == item.trackId }
                } ?: continue
                val outcome = withContext(Dispatchers.IO) {
                    trackDownloadEnsurer.ensureTrackLocal(item.bookId, track) { trackProgress ->
                        scope.launch(Dispatchers.Main.immediate) {
                            musicDownloadNotifier.updateBulk(completed, total, item.trackId, trackProgress)
                        }
                    }
                }
                if (outcome.track != null) {
                    completed++
                    uiState.update { state ->
                        state.copy(
                            musicTrackList = state.musicTrackList.map { row ->
                                if (row.trackId == item.trackId) row.copy(isDownloaded = true) else row
                            },
                        )
                    }
                    musicDownloadNotifier.incrementBulkDownloaded(completed, total)
                }
            }
            musicDownloadNotifier.clear()
            refreshDownloadedBooks()
        }
    }

    suspend fun onMusicSnapshot(snapshot: PlaybackSnapshot) {
        val trackId = snapshot.trackId ?: return
        session.musicStartedInSession = true
        if (session.musicLibraryTracks.isNotEmpty() && trackId != session.lastPrefetchSourceTrackId) {
            session.lastPrefetchSourceTrackId = trackId
            val index = session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
            if (index >= 0) {
                scheduleMusicPrefetch(index + 1)
            }
        }
    }

    fun musicPlaybackUi(snapshot: PlaybackSnapshot): MusicPlaybackUi {
        val trackId = snapshot.trackId
        val isMusic = snapshot.contentType == ContentType.MUSIC ||
            (trackId != null && trackId in session.musicBookIdByTrackId)
        return MusicPlaybackUi(
            isActive = isMusic && trackId != null,
            trackId = trackId,
            trackTitle = snapshot.trackTitle,
            artist = snapshot.artist,
            albumTitle = snapshot.albumTitle,
            bookId = trackId?.let { id -> session.musicBookIdByTrackId[id] },
            isPlaying = snapshot.isPlaying && isMusic,
        )
    }

    fun isMusicSnapshot(snapshot: PlaybackSnapshot): Boolean {
        val trackId = snapshot.trackId
        return snapshot.contentType == ContentType.MUSIC ||
            (trackId != null && trackId in session.musicBookIdByTrackId)
    }

    suspend fun invalidatePlaybackIfLocalFilesMissing() {
        val snapshot = playbackClient.snapshot.value
        val trackId = snapshot.trackId ?: return
        val isMusic = snapshot.contentType == ContentType.MUSIC || trackId in session.musicBookIdByTrackId
        if (!isMusic) return
        val bookId = session.musicBookIdByTrackId[trackId]
            ?: catalogRepository.findBookForTrack(trackId)?.id
            ?: return
        val isLocal = trackDownloadEnsurer.isTrackLocal(bookId, trackId)
        if (!isLocal) {
            playJob?.cancel()
            clearMusicPrefetchState()
            playbackClient.stopAndRelease()
            musicDownloadNotifier.clear()
            uiState.update {
                it.copy(
                    musicPlayback = MusicPlaybackUi(),
                    musicPlaybackErrorRes = null,
                )
            }
        }
    }

    fun handleMusicTrackEnded() {
        if (musicDownloadNotifier.state.value.isActive) return
        val snapshot = playbackClient.snapshot.value
        val trackId = snapshot.trackId ?: return
        val isMusic = snapshot.contentType == ContentType.MUSIC || trackId in session.musicBookIdByTrackId
        if (!isMusic || session.musicLibraryTracks.isEmpty()) return
        val currentIndex = session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
        if (currentIndex < 0) return
        val nextIndex = MusicShuffleQueue.nextIndex(currentIndex, session.musicLibraryTracks.size)
        val nextEntry = session.musicLibraryTracks[nextIndex]
        val listTrack = uiState.value.musicTrackList.find { it.trackId == nextEntry.track.id }
            ?: MusicListTrack(
                trackId = nextEntry.track.id,
                trackTitle = nextEntry.track.title,
                artist = nextEntry.book.author ?: nextEntry.book.title,
                albumTitle = nextEntry.book.title,
                bookId = nextEntry.book.id,
                durationMs = nextEntry.track.durationMs,
                isDownloaded = false,
            )
        playJob?.cancel()
        playJob = scope.launch {
            playMusicTrack(listTrack, showDownloadProgress = false)
        }
    }

    suspend fun rebuildMusicCandidates(books: List<Book>) {
        session.musicCandidates = withContext(Dispatchers.IO) {
            catalogRepository.resolveMusicLibraryTracks().map { entry ->
                entry.book to entry.track
            }
        }
    }

    suspend fun buildMusicTrackBookMap(books: List<Book>): Map<String, String> = buildMap {
        for (entry in catalogRepository.resolveMusicLibraryTracks()) {
            put(entry.track.id, entry.book.id)
        }
    }

    suspend fun buildMusicTrackListForCatalogUpdate(): List<MusicListTrack> = when {
        uiState.value.musicTrackList.isNotEmpty() ->
            refreshMusicTrackListDownloadState(uiState.value.musicTrackList)
        session.musicStartedInSession ->
            buildMusicTrackList(shuffle = false)
        else ->
            buildMusicTrackList(shuffle = true)
    }

    suspend fun refreshMusicTrackListForDownloads(): List<MusicListTrack> =
        refreshMusicTrackListDownloadState(uiState.value.musicTrackList)

    fun cancelPlayJob() {
        playJob?.cancel()
    }

    private suspend fun buildMusicTrackList(shuffle: Boolean): List<MusicListTrack> {
        if (session.musicCandidates.isEmpty()) return emptyList()
        val ordered = if (shuffle) session.musicCandidates.shuffled() else session.musicCandidates
        return withContext(Dispatchers.IO) {
            ordered.map { (book, track) -> toListTrack(book, track) }
        }
    }

    private suspend fun refreshMusicTrackListDownloadState(
        list: List<MusicListTrack>,
    ): List<MusicListTrack> = withContext(Dispatchers.IO) {
        list.map { item ->
            item.copy(isDownloaded = trackDownloadEnsurer.isTrackLocal(item.bookId, item.trackId))
        }
    }

    private suspend fun toListTrack(book: Book, track: Track): MusicListTrack = MusicListTrack(
        trackId = track.id,
        trackTitle = track.title,
        artist = book.author ?: book.title,
        albumTitle = book.title,
        bookId = book.id,
        durationMs = track.durationMs,
        isDownloaded = trackDownloadEnsurer.isTrackLocal(book.id, track.id),
    )

    private suspend fun resolvePlaybackTrack(playback: MusicPlaybackUi): MusicListTrack? {
        val trackId = playback.trackId ?: return null
        val bookId = playback.bookId
            ?: catalogRepository.findBookForTrack(trackId)?.id
            ?: return null
        val domainTrack = catalogRepository.getTracksForBook(bookId).find { it.id == trackId }
            ?: return null
        val book = uiState.value.books.find { it.id == bookId }
            ?: catalogRepository.findBookForTrack(trackId)
            ?: return null
        return MusicListTrack(
            trackId = trackId,
            trackTitle = playback.trackTitle ?: domainTrack.title,
            artist = playback.artist ?: book.author ?: book.title,
            albumTitle = playback.albumTitle ?: book.title,
            bookId = bookId,
            durationMs = domainTrack.durationMs,
            isDownloaded = trackDownloadEnsurer.isTrackLocal(bookId, trackId),
        )
    }

    private fun clearMusicPrefetchState() {
        musicPrefetchJob?.cancel()
        musicPrefetchJob = null
        session.musicLibraryTracks = emptyList()
        session.lastPrefetchSourceTrackId = null
        musicPlaybackQueue.clear()
    }

    private fun scheduleMusicPrefetch(fromIndex: Int) {
        if (fromIndex !in session.musicLibraryTracks.indices) return
        if (musicDownloadNotifier.state.value.isBulkDownloading) return
        musicPrefetchJob?.cancel()
        musicPrefetchJob = scope.launch {
            prefetchMusicTrack(fromIndex)
        }
    }

    private suspend fun prefetchMusicTrack(index: Int) {
        if (index !in session.musicLibraryTracks.indices) return
        val entry = session.musicLibraryTracks[index]
        val bookId = entry.book.id
        val trackId = entry.track.id
        val alreadyQueued = withContext(Dispatchers.Main.immediate) {
            trackId in playbackClient.queuedTrackIds()
        }
        if (alreadyQueued) return

        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(bookId).find { it.id == trackId } ?: entry.track
        }
        val needsDownload = withContext(Dispatchers.IO) {
            !trackDownloadEnsurer.isTrackLocal(bookId, trackId)
        }
        val progressReporter = if (needsDownload && !musicDownloadNotifier.state.value.isActive) {
            withContext(Dispatchers.Main.immediate) {
                musicDownloadNotifier.beginTrack(trackId)
            }
            createTrackProgressReporter(trackId)
        } else {
            null
        }
        val localTrack = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(bookId, resolvedTrack, progressReporter).track
        } ?: run {
            if (progressReporter != null) {
                withContext(Dispatchers.Main.immediate) {
                    musicDownloadNotifier.finishTrack()
                }
            }
            return
        }
        if (progressReporter != null) {
            withContext(Dispatchers.Main.immediate) {
                musicDownloadNotifier.finishTrack()
            }
        }

        val queueItem = playbackQueueBuilder.itemForMusicLibraryTrack(
            entry = entry,
            localTrack = localTrack,
            indexInLibrary = index,
            librarySize = session.musicLibraryTracks.size,
        )
        withContext(Dispatchers.Main) {
            uiState.update { state ->
                state.copy(
                    musicTrackList = state.musicTrackList.map { row ->
                        if (row.trackId == trackId) row.copy(isDownloaded = true) else row
                    },
                )
            }
            playbackClient.appendQueueItems(listOf(queueItem))
        }
        refreshDownloadedBooks()
    }

    private suspend fun buildMusicLibraryTracksFromList(): List<MusicLibraryTrack> {
        val list = uiState.value.musicTrackList
        if (list.isEmpty()) {
            return withContext(Dispatchers.IO) {
                catalogRepository.resolveMusicLibraryTracks()
            }
        }
        val booksById = uiState.value.books.associateBy { it.id }
        return withContext(Dispatchers.IO) {
            list.mapNotNull { item ->
                val book = booksById[item.bookId] ?: return@mapNotNull null
                val domainTrack = catalogRepository.getTracksForBook(book.id).find { it.id == item.trackId }
                    ?: return@mapNotNull null
                MusicLibraryTrack(book, domainTrack)
            }
        }
    }

    private suspend fun playMusicTrack(track: MusicListTrack, showDownloadProgress: Boolean) {
        val book = uiState.value.books.find { it.id == track.bookId } ?: return
        val libraryTracks = buildMusicLibraryTracksFromList()
        val targetEntry = libraryTracks.find { it.track.id == track.trackId } ?: return
        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(targetEntry.book.id).find { it.id == track.trackId }
        } ?: targetEntry.track
        val needsDownload = withContext(Dispatchers.IO) {
            resolvedTrack.localPath == null &&
                !trackDownloadEnsurer.isTrackLocal(targetEntry.book.id, resolvedTrack.id)
        }
        if (showDownloadProgress && !needsDownload) {
            musicDownloadNotifier.finishTrack()
        } else if (showDownloadProgress && needsDownload) {
            musicDownloadNotifier.beginTrack(track.trackId)
            yield()
        }
        val progressReporter = if (showDownloadProgress && needsDownload) {
            createTrackProgressReporter(track.trackId)
        } else {
            null
        }
        val outcome = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(targetEntry.book.id, resolvedTrack, progressReporter)
        }
        val localTrack = outcome.track ?: run {
            musicDownloadNotifier.finishTrack()
            uiState.update {
                it.copy(musicPlaybackErrorRes = playbackErrorRes(outcome.failure))
            }
            return
        }
        val queue = withContext(Dispatchers.IO) {
            playbackQueueBuilder.buildLocalMusicLibraryQueue(libraryTracks) { entry ->
                catalogRepository.getTracksForBook(entry.book.id)
                    .find { it.id == entry.track.id }
                    ?.takeIf { !it.localPath.isNullOrBlank() }
            }
        }
        if (queue.isEmpty()) return
        session.musicStartedInSession = true
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        musicDownloadNotifier.finishTrack()
        uiState.update { state ->
            state.copy(
                musicPlaybackErrorRes = null,
                musicTrackList = state.musicTrackList.map { row ->
                    if (row.trackId == track.trackId) row.copy(isDownloaded = true) else row
                },
                nowPlayingTitle = resolvedTrack.title,
            )
        }
        session.musicBookIdByTrackId = session.musicBookIdByTrackId + (resolvedTrack.id to targetEntry.book.id)
        session.musicLibraryTracks = libraryTracks
        musicPlaybackQueue.set(libraryTracks)
        val libraryStartIndex = libraryTracks.indexOfFirst { it.track.id == localTrack.id }.coerceAtLeast(0)
        session.lastPrefetchSourceTrackId = localTrack.id
        playbackClient.playQueue(queue, startIndex)
        scheduleMusicPrefetch(libraryStartIndex + 1)
        refreshDownloadedBooks()
    }

    private suspend fun downloadTrackOnly(track: MusicListTrack) {
        musicDownloadNotifier.beginTrack(track.trackId)
        uiState.update { it.copy(musicPlaybackErrorRes = null) }
        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(track.bookId).find { it.id == track.trackId }
        } ?: run {
            musicDownloadNotifier.finishTrack()
            return
        }
        val outcome = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(
                track.bookId,
                resolvedTrack,
                createTrackProgressReporter(track.trackId),
            )
        }
        if (outcome.track != null) {
            musicDownloadNotifier.finishTrack()
            uiState.update { state ->
                state.copy(
                    musicTrackList = state.musicTrackList.map { row ->
                        if (row.trackId == track.trackId) row.copy(isDownloaded = true) else row
                    },
                )
            }
            refreshDownloadedBooks()
        } else {
            musicDownloadNotifier.finishTrack()
            uiState.update {
                it.copy(musicPlaybackErrorRes = playbackErrorRes(outcome.failure))
            }
        }
    }

    private fun createTrackProgressReporter(trackId: String): (Float) -> Unit {
        val reporter = object {
            var lastBucket = -1
        }
        return progress@{ progress ->
            val bucket = (progress * 50).toInt()
            if (bucket > reporter.lastBucket || progress >= 1f) {
                reporter.lastBucket = bucket
                scope.launch(Dispatchers.Main.immediate) {
                    musicDownloadNotifier.updateTrack(trackId, progress)
                }
            }
        }
    }

    private suspend fun refreshDownloadedBooks() {
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(uiState.value.books)
        }
        uiState.update { it.copy(downloadedBookIds = downloaded) }
    }
}
