package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.domain.music.MusicDownloadInteractionRules
import com.tonezen.app.domain.music.MusicDownloadInteractionState
import com.tonezen.app.domain.music.MusicPlaybackAdvanceRules
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
    var onBulkDownloadFinished: () -> Unit = {}
    var playJob: Job? = null
    var downloadTrackJob: Job? = null
    var downloadAllJob: Job? = null
    var musicPrefetchJob: Job? = null
    private var prefetchTargetIndex: Int = -1

    private fun downloadInteractionState(): MusicDownloadInteractionState =
        MusicDownloadInteractionState(
            isTrackDownloading = musicDownloadNotifier.state.value.isTrackDownloading,
            isBulkDownloading = musicDownloadNotifier.state.value.isBulkDownloading,
            activeTrackId = musicDownloadNotifier.state.value.activeTrackId,
        )

    private fun isBlockingUndownloadedTap(): Boolean =
        MusicDownloadInteractionRules.blocksUndownloadedTap(downloadInteractionState())

    fun onMusicTabSelected() {
        scope.launch {
            if (session.musicCandidates.isEmpty()) return@launch
            val list = if (uiState.value.musicTrackList.isEmpty()) {
                if (session.musicStartedInSession) return@launch
                buildMusicTrackList(shuffle = true)
            } else {
                refreshMusicTrackListDownloadState(uiState.value.musicTrackList)
            }
            uiState.update { it.copy(musicTrackList = list) }
        }
    }

    fun onNetworkOffline() {
        downloadAllJob?.cancel()
        downloadTrackJob?.cancel()
        musicPrefetchJob?.cancel()
        musicPrefetchJob = null
        prefetchTargetIndex = -1
        if (musicDownloadNotifier.state.value.isActive) {
            musicDownloadNotifier.clear()
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
        if (!uiState.value.isNetworkOnline && !track.isDownloaded) return
        if (isBlockingUndownloadedTap() && !track.isDownloaded) return
        val playback = uiState.value.musicPlayback
        if (playback.trackId == track.trackId && playback.isActive) {
            if (playback.isPlaying) {
                playbackClient.pause()
            } else if (!track.isDownloaded) {
                playJob?.cancel()
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
        uiState.update { it.copy(musicPlaybackErrorRes = null) }
        playJob = scope.launch {
            playMusicTrack(track, showDownloadProgress = !track.isDownloaded)
        }
    }

    fun downloadMusicTrack(track: MusicListTrack) {
        if (!uiState.value.isNetworkOnline) return
        if (track.isDownloaded || isBlockingUndownloadedTap()) return
        downloadTrackJob?.cancel()
        downloadTrackJob = scope.launch {
            downloadTrackOnly(track)
        }
    }

    fun deleteMusicTrack(track: MusicListTrack) {
        if (MusicDownloadInteractionRules.blocksDeletingTrack(downloadInteractionState(), track.trackId)) return
        scope.launch {
            val isPlaying = uiState.value.musicPlayback.trackId == track.trackId
            if (isPlaying) {
                playJob?.cancel()
                musicPrefetchJob?.cancel()
                musicPrefetchJob = null
                prefetchTargetIndex = -1
                playbackClient.stopAndRelease()
                finishTrackDownloadUi(track.trackId)
            }
            withContext(Dispatchers.IO) {
                downloadRepository.deleteLocalTrack(track.bookId, track.trackId)
                catalogRepository.clearTrackLocalPath(track.bookId, track.trackId)
            }
            val updatedList = refreshMusicTrackListDownloadState(
                uiState.value.musicTrackList,
                withContext(Dispatchers.IO) { resolveDownloadedTrackIdsForUi() },
            )
            uiState.update { state ->
                state.copy(
                    musicTrackList = updatedList,
                    musicPlayback = if (isPlaying) MusicPlaybackUi() else state.musicPlayback,
                    musicPlaybackErrorRes = null,
                )
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
        }
    }

    fun downloadAllMusic() {
        if (!uiState.value.isNetworkOnline) return
        if (musicDownloadNotifier.state.value.isTrackDownloading) return
        if (musicDownloadNotifier.state.value.isBulkDownloading) return
        val pending = uiState.value.musicTrackList.filter { !it.isDownloaded }
        if (pending.isEmpty()) return
        downloadAllJob?.cancel()
        musicPrefetchJob?.cancel()
        musicPrefetchJob = null
        prefetchTargetIndex = -1
        downloadAllJob = scope.launch {
            val total = uiState.value.musicTrackList.size
            var completed = uiState.value.musicTrackList.count { it.isDownloaded }
            try {
                musicDownloadNotifier.beginBulk(completed, total)
                for (item in pending) {
                    val track = withContext(Dispatchers.IO) {
                        catalogRepository.getTracksForBook(item.bookId).find { it.id == item.trackId }
                    } ?: continue
                    val outcome = withContext(Dispatchers.IO) {
                        trackDownloadEnsurer.ensureTrackLocal(item.bookId, track) { trackProgress ->
                            musicDownloadNotifier.updateBulk(completed, total, item.trackId, trackProgress)
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
                        appendPrefetchedQueueItem(item.trackId, outcome.track)
                    }
                }
            } finally {
                musicDownloadNotifier.clear()
                val downloadedIds = withContext(Dispatchers.IO) { resolveDownloadedTrackIdsForUi() }
                uiState.update { state ->
                    state.copy(
                        musicTrackList = state.musicTrackList.map { row ->
                            row.copy(isDownloaded = row.trackId in downloadedIds || row.isDownloaded)
                        },
                    )
                }
                refreshDownloadedBooks()
                localLibraryNotifier.notifyLocalLibraryChanged()
                onBulkDownloadFinished()
                val snapshot = playbackClient.snapshot.value
                val playingTrackId = snapshot.trackId
                if (playingTrackId != null && isMusicSnapshot(snapshot) && session.musicLibraryTracks.isNotEmpty()) {
                    val playingIndex = session.musicLibraryTracks.indexOfFirst { it.track.id == playingTrackId }
                    if (playingIndex >= 0) {
                        scheduleMusicPrefetch(playingIndex + 1)
                    }
                }
            }
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
            ?: withContext(Dispatchers.IO) { catalogRepository.findBookForTrack(trackId)?.id }
            ?: return
        val isLocal = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.isTrackLocal(bookId, trackId)
        }
        if (!isLocal) {
            playJob?.cancel()
            musicPrefetchJob?.cancel()
            musicPrefetchJob = null
            prefetchTargetIndex = -1
            if (musicDownloadNotifier.state.value.isTrackDownloading) {
                musicDownloadNotifier.finishTrack()
            }
            val currentIndex = session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
            if (currentIndex >= 0) {
                playJob?.cancel()
                playJob = scope.launch {
                    playNextAvailableFrom(currentIndex)
                }
            } else {
                clearMusicPrefetchState()
                playbackClient.stopAndRelease()
                uiState.update {
                    it.copy(
                        musicPlayback = MusicPlaybackUi(),
                        musicPlaybackErrorRes = null,
                    )
                }
            }
        }
    }

    fun handleMusicTrackEnded() {
        if (MusicDownloadInteractionRules.blocksTrackEndedDuringSingleTrackDownload(downloadInteractionState())) return
        val snapshot = playbackClient.snapshot.value
        val trackId = snapshot.trackId ?: return
        val isMusic = snapshot.contentType == ContentType.MUSIC || trackId in session.musicBookIdByTrackId
        if (!isMusic || session.musicLibraryTracks.isEmpty()) return
        val currentIndex = session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
        if (currentIndex < 0) return
        playJob?.cancel()
        playJob = scope.launch {
            playNextAvailableFrom(currentIndex)
        }
    }

    private suspend fun playNextAvailableFrom(currentIndex: Int) {
        val library = session.musicLibraryTracks
        if (library.isEmpty()) return
        val nextIndex = MusicPlaybackAdvanceRules.findNextPlayable(
            items = library,
            currentIndex = currentIndex,
            isPlayable = { entry -> isMusicEntryPlayable(entry) },
        ) ?: run {
            playbackClient.pause()
            return
        }
        playMusicTrack(
            track = musicListTrackFromEntry(library[nextIndex]),
            showDownloadProgress = false,
            advancePlayback = true,
        )
    }

    private fun isMusicEntryPlayable(entry: MusicLibraryTrack): Boolean {
        val hasLocal = !entry.track.localPath.isNullOrBlank()
        return MusicPlaybackAdvanceRules.isTrackPlayable(
            isDownloaded = hasLocal,
            isNetworkOnline = uiState.value.isNetworkOnline,
        )
    }

    private fun musicListTrackFromEntry(entry: MusicLibraryTrack): MusicListTrack {
        val downloaded = entry.track.localPath?.isNotBlank() == true
        return MusicListTrack(
            trackId = entry.track.id,
            trackTitle = entry.track.title,
            artist = entry.book.author ?: entry.book.title,
            albumTitle = entry.book.title,
            bookId = entry.book.id,
            durationMs = entry.track.durationMs,
            isDownloaded = downloaded,
        )
    }

    suspend fun reloadMusicCatalogData() {
        val entries = withContext(Dispatchers.IO) {
            catalogRepository.resolveMusicLibraryTracks()
        }
        session.musicCandidates = entries.map { it.book to it.track }
        session.musicBookIdByTrackId = entries.associate { it.track.id to it.book.id }
    }

    suspend fun rebuildMusicCandidates(books: List<Book>) {
        reloadMusicCatalogData()
    }

    suspend fun buildMusicTrackBookMap(books: List<Book>): Map<String, String> {
        if (session.musicBookIdByTrackId.isEmpty()) {
            reloadMusicCatalogData()
        }
        return session.musicBookIdByTrackId
    }

    suspend fun resolveDownloadedTrackIdsForUi(): Set<String> = withContext(Dispatchers.IO) {
        catalogRepository.reconcileLocalDownloadPaths()
        val ids = catalogRepository.getDownloadedTrackIds().toMutableSet()
        ids.addAll(localPlaybackDownloadedTrackIds())
        ids
    }

    private suspend fun localPlaybackDownloadedTrackIds(): Set<String> {
        val playback = uiState.value.musicPlayback
        val trackId = playback.trackId ?: return emptySet()
        if (!playback.isActive) return emptySet()
        val bookId = playback.bookId
            ?: session.musicBookIdByTrackId[trackId]
            ?: catalogRepository.findBookForTrack(trackId)?.id
            ?: return emptySet()
        return if (trackDownloadEnsurer.isTrackLocal(bookId, trackId)) setOf(trackId) else emptySet()
    }

    suspend fun buildMusicTrackListForCatalogUpdate(rebuildMusic: Boolean = false): List<MusicListTrack> {
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return buildMusicTrackListForCatalogUpdate(
            existing = uiState.value.musicTrackList,
            candidates = session.musicCandidates,
            musicStartedInSession = session.musicStartedInSession,
            downloadedTrackIds = downloadedTrackIds,
        )
    }

    suspend fun refreshMusicTrackListForDownloads(): List<MusicListTrack> {
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return refreshMusicTrackListDownloadState(uiState.value.musicTrackList, downloadedTrackIds)
    }

    suspend fun refreshMusicTrackListWithDownloadedIds(downloadedTrackIds: Set<String>): List<MusicListTrack> =
        refreshMusicTrackListDownloadState(uiState.value.musicTrackList, downloadedTrackIds)

    fun cancelPlayJob() {
        playJob?.cancel()
    }

    private suspend fun buildMusicTrackList(shuffle: Boolean): List<MusicListTrack> {
        if (session.musicCandidates.isEmpty()) return emptyList()
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return buildMusicTrackListFromCandidates(session.musicCandidates, shuffle, downloadedTrackIds)
    }

    private suspend fun refreshMusicTrackListDownloadState(
        list: List<MusicListTrack>,
    ): List<MusicListTrack> {
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return refreshMusicTrackListDownloadState(list, downloadedTrackIds)
    }

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
        prefetchTargetIndex = -1
        session.musicLibraryTracks = emptyList()
        session.lastPrefetchSourceTrackId = null
        musicPlaybackQueue.clear()
    }

    private fun visibleMusicTrackList(): List<MusicListTrack> =
        visibleMusicTrackList(uiState.value.musicTrackList, uiState.value.isNetworkOnline)

    private fun scheduleMusicPrefetch(fromIndex: Int) {
        if (!uiState.value.isNetworkOnline) return
        if (fromIndex !in session.musicLibraryTracks.indices) return
        if (prefetchTargetIndex == fromIndex && musicPrefetchJob?.isActive == true) return
        prefetchTargetIndex = fromIndex
        musicPrefetchJob?.cancel()
        musicPrefetchJob = scope.launch {
            try {
                prefetchMusicTrack(fromIndex)
            } finally {
                if (prefetchTargetIndex == fromIndex) {
                    prefetchTargetIndex = -1
                }
            }
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
        val localTrack = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(bookId, resolvedTrack).track
        } ?: return

        withContext(Dispatchers.Main) {
            uiState.update { state ->
                state.copy(
                    musicTrackList = state.musicTrackList.map { row ->
                        if (row.trackId == trackId) row.copy(isDownloaded = true) else row
                    },
                )
            }
            appendPrefetchedQueueItem(trackId, localTrack)
        }
        refreshDownloadedBooks()
    }

    private suspend fun appendPrefetchedQueueItem(trackId: String, localTrack: Track) {
        val index = session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
        if (index < 0) return
        val entry = session.musicLibraryTracks[index]
        val alreadyQueued = withContext(Dispatchers.Main.immediate) {
            trackId in playbackClient.queuedTrackIds()
        }
        if (alreadyQueued) return
        val queueItem = playbackQueueBuilder.itemForMusicLibraryTrack(
            entry = entry,
            localTrack = localTrack,
            indexInLibrary = index,
            librarySize = session.musicLibraryTracks.size,
        )
        withContext(Dispatchers.Main) {
            playbackClient.appendQueueItems(listOf(queueItem))
        }
    }

    private suspend fun buildMusicLibraryTracksFromList(): List<MusicLibraryTrack> {
        val list = visibleMusicTrackList()
        if (list.isEmpty()) {
            return withContext(Dispatchers.IO) {
                val all = catalogRepository.resolveMusicLibraryTracks()
                if (uiState.value.isNetworkOnline) {
                    all
                } else {
                    all.filter { entry ->
                        trackDownloadEnsurer.isTrackLocal(entry.book.id, entry.track.id)
                    }
                }
            }
        }
        val booksById = uiState.value.books.associateBy { it.id }
        return withContext(Dispatchers.IO) {
            val tracksByBookId = catalogRepository.getTracksByBookIds(list.map { it.bookId }.distinct())
            list.mapNotNull { item ->
                val book = booksById[item.bookId] ?: return@mapNotNull null
                val domainTrack = tracksByBookId[item.bookId]?.find { it.id == item.trackId }
                    ?: return@mapNotNull null
                MusicLibraryTrack(book, domainTrack)
            }
        }
    }

    private suspend fun playMusicTrack(
        track: MusicListTrack,
        showDownloadProgress: Boolean,
        advancePlayback: Boolean = false,
    ) {
        try {
            if (uiState.value.books.none { it.id == track.bookId }) return
            val libraryTracks = buildMusicLibraryTracksFromList()
            val targetEntry = libraryTracks.find { it.track.id == track.trackId } ?: return
            val resolvedTrack = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(targetEntry.book.id).find { it.id == track.trackId }
            } ?: targetEntry.track
            val needsDownload = withContext(Dispatchers.IO) {
                !trackDownloadEnsurer.isTrackLocal(targetEntry.book.id, resolvedTrack.id)
            }
            if (showDownloadProgress && !needsDownload) {
                finishTrackDownloadUi(track.trackId)
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
                if (advancePlayback) {
                    val failedIndex = session.musicLibraryTracks.indexOfFirst { it.track.id == track.trackId }
                    if (failedIndex >= 0) {
                        playNextAvailableFrom(failedIndex)
                    }
                } else {
                    uiState.update {
                        it.copy(musicPlaybackErrorRes = playbackErrorRes(outcome.failure))
                    }
                }
                return
            }
            val queue = withContext(Dispatchers.IO) {
                playbackQueueBuilder.buildLocalMusicLibraryQueue(libraryTracks) { entry ->
                    if (entry.track.id == localTrack.id) {
                        localTrack
                    } else {
                        trackDownloadEnsurer.resolveLocalTrack(entry.book.id, entry.track)
                    }
                }
            }
            if (queue.isEmpty()) {
                uiState.update {
                    it.copy(musicPlaybackErrorRes = playbackErrorRes(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED))
                }
                return
            }
            session.musicStartedInSession = true
            val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
            finishTrackDownloadUi(track.trackId)
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
            localLibraryNotifier.notifyLocalLibraryChanged()
        } finally {
            finishTrackDownloadUi(track.trackId)
        }
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
            localLibraryNotifier.notifyLocalLibraryChanged()
        } else {
            musicDownloadNotifier.finishTrack()
            uiState.update {
                it.copy(musicPlaybackErrorRes = playbackErrorRes(outcome.failure))
            }
        }
    }

    private fun finishTrackDownloadUi(trackId: String) {
        if (MusicDownloadInteractionRules.shouldFinishTrackDownloadUi(downloadInteractionState(), trackId)) {
            musicDownloadNotifier.finishTrack()
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
                musicDownloadNotifier.updateTrack(trackId, progress)
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
