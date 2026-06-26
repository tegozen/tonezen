package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicDownloadInteractionRules
import com.tonezen.app.domain.music.MusicDownloadInteractionState
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicPlaybackAdvanceRules
import com.tonezen.app.domain.music.resolveMusicWaveDisplayTrack
import com.tonezen.app.domain.music.MusicQueueWindow
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.forMusic
import com.tonezen.app.playback.TrackDownloadQueueController
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.PlaybackSnapshot
import com.tonezen.app.playback.QueuePlayItem
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
    private val downloadQueueController: TrackDownloadQueueController,
    private val downloadQueueNotifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val musicPlaybackQueue: MusicPlaybackQueue,
    private val playbackErrorMessage: (EnsureTrackOutcome.Failure?) -> String,
    private val refreshCycleCardStates: suspend (List<com.tonezen.app.domain.model.Cycle>, Set<String>) -> Unit,
) {
    var onBulkDownloadFinished: () -> Unit = {}
    var playJob: Job? = null
    var musicPrefetchJob: Job? = null
    private var prefetchTargetIndex: Int = -1
    private var lastBulkBatchId: String? = null

    private suspend fun resolveMusicDownloadBookId(track: MusicListTrack): String =
        catalogRepository.canonicalBookIdForTrack(track.trackId) ?: track.bookId

    private suspend fun musicEnqueueRequest(
        track: MusicListTrack,
        priority: DownloadPriority,
        batchId: String? = null,
    ): EnqueueDownloadRequest {
        val bookId = resolveMusicDownloadBookId(track)
        return EnqueueDownloadRequest(
            bookId = bookId,
            trackId = track.trackId,
            priority = priority,
            batchId = batchId,
            title = track.trackTitle,
            subtitle = track.artist,
            contentType = ContentType.MUSIC.name.lowercase(),
        )
    }

    private suspend fun catalogTrackNeedsDownload(track: MusicListTrack): Boolean {
        val catalogTrack = catalogRepository.findTrackInCatalog(track.trackId) ?: return false
        val bookId = catalogTrack.bookId
        if (!catalogTrack.localPath.isNullOrBlank() &&
            trackDownloadEnsurer.isTrackLocal(bookId, track.trackId)
        ) {
            return false
        }
        return !trackDownloadEnsurer.isTrackLocal(bookId, track.trackId)
    }

    private fun reportMusicDownloadError(failure: EnsureTrackOutcome.Failure? = EnsureTrackOutcome.Failure.DOWNLOAD_FAILED) {
        uiState.update {
            it.copy(musicPlaybackErrorMessage = playbackErrorMessage(failure))
        }
    }

    private fun reportMusicDownloadError(awaitResult: DownloadAwaitResult) {
        val failure = when (awaitResult) {
            DownloadAwaitResult.OFFLINE -> EnsureTrackOutcome.Failure.OFFLINE
            DownloadAwaitResult.FAILED,
            DownloadAwaitResult.CANCELLED,
            DownloadAwaitResult.COMPLETED,
            -> EnsureTrackOutcome.Failure.DOWNLOAD_FAILED
        }
        reportMusicDownloadError(failure)
    }

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
        musicPrefetchJob?.cancel()
        musicPrefetchJob = null
        prefetchTargetIndex = -1
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

    private fun musicDownloadInteractionState(): MusicDownloadInteractionState {
        val snapshot = downloadQueueNotifier.snapshot().forMusic()
        return MusicDownloadInteractionState(
            isTrackDownloading = snapshot.isTrackDownloading,
            isBulkDownloading = snapshot.isBulkDownloading,
            activeTrackId = snapshot.activeTrackId,
        )
    }

    private fun pauseMusicForBulkDownload() {
        playJob?.cancel()
        musicPrefetchJob?.cancel()
        musicPrefetchJob = null
        prefetchTargetIndex = -1
        if (uiState.value.musicPlayback.isActive) {
            playbackClient.pause()
        }
    }

    fun onMusicTrackClick(track: MusicListTrack) {
        if (!uiState.value.isNetworkOnline && !track.isDownloaded) return
        if (MusicDownloadInteractionRules.blocksUndownloadedTap(musicDownloadInteractionState()) &&
            !track.isDownloaded
        ) {
            return
        }
        val playback = uiState.value.musicPlayback
        if (playback.trackId == track.trackId && playback.isActive) {
            if (playback.isPlaying) {
                playbackClient.pause()
            } else if (!track.isDownloaded) {
                playJob?.cancel()
                uiState.update { it.copy(musicPlaybackErrorMessage = null) }
                playJob = scope.launch {
                    playMusicTrack(track, showDownloadProgress = true)
                }
            } else {
                playbackClient.play()
            }
            return
        }
        playJob?.cancel()
        uiState.update { it.copy(musicPlaybackErrorMessage = null) }
        playJob = scope.launch {
            playMusicTrack(track, showDownloadProgress = !track.isDownloaded)
        }
    }

    fun playMusicWave() {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(musicDownloadInteractionState())) {
            return
        }
        val playback = uiState.value.musicPlayback
        if (playback.isActive && playback.trackId != null) {
            onMiniPlayerPlayPause()
            return
        }
        val list = visibleMusicTrackList()
        val displayTrack = resolveMusicWaveDisplayTrack(
            tracks = list,
            activeTrackId = playback.trackId,
            isMusicActive = playback.isActive,
            trackIdOf = { it.trackId },
        ) ?: run {
            if (!uiState.value.isNetworkOnline) {
                uiState.update {
                    it.copy(musicPlaybackErrorMessage = playbackErrorMessage(EnsureTrackOutcome.Failure.OFFLINE))
                }
            }
            return
        }
        playJob?.cancel()
        uiState.update { it.copy(musicPlaybackErrorMessage = null) }
        playJob = scope.launch {
            playMusicTrack(
                track = displayTrack,
                showDownloadProgress = !displayTrack.isDownloaded,
                advancePlayback = true,
            )
        }
    }


    fun downloadMusicTrack(track: MusicListTrack) {
        if (!uiState.value.isNetworkOnline) {
            reportMusicDownloadError(EnsureTrackOutcome.Failure.OFFLINE)
            return
        }
        scope.launch {
            if (!withContext(Dispatchers.IO) { catalogTrackNeedsDownload(track) }) {
                val updatedList = refreshMusicTrackListDownloadState(uiState.value.musicTrackList)
                uiState.update { it.copy(musicTrackList = updatedList) }
                return@launch
            }
            downloadQueueController.enqueue(
                withContext(Dispatchers.IO) {
                    musicEnqueueRequest(track, DownloadPriority.USER)
                },
            )
        }
    }

    fun cancelAllDownloads() {
        downloadQueueController.cancelAll()
    }

    fun deleteMusicTrack(track: MusicListTrack) {
        scope.launch {
            val bookId = withContext(Dispatchers.IO) { resolveMusicDownloadBookId(track) }
            val isPlaying = uiState.value.musicPlayback.trackId == track.trackId
            downloadQueueController.cancelTrack(bookId, track.trackId)
            if (isPlaying) {
                playJob?.cancel()
                musicPrefetchJob?.cancel()
                musicPrefetchJob = null
                prefetchTargetIndex = -1
                playbackClient.stopAndRelease()
            }
            withContext(Dispatchers.IO) {
                downloadRepository.deleteLocalTrack(bookId, track.trackId)
                catalogRepository.clearTrackLocalPath(bookId, track.trackId)
            }
            val updatedList = refreshMusicTrackListDownloadState(
                uiState.value.musicTrackList,
                withContext(Dispatchers.IO) { resolveDownloadedTrackIdsForUi() },
            )
            uiState.update { state ->
                state.copy(
                    musicTrackList = updatedList,
                    musicPlayback = if (isPlaying) MusicPlaybackUi() else state.musicPlayback,
                    musicPlaybackErrorMessage = null,
                )
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
        }
    }

    fun downloadAllMusic() {
        if (!uiState.value.isNetworkOnline) {
            reportMusicDownloadError(EnsureTrackOutcome.Failure.OFFLINE)
            return
        }
        val snapshot = downloadQueueNotifier.snapshot().forMusic()
        if (snapshot.isBulkDownloading) {
            snapshot.activeBatchId?.let { downloadQueueController.cancelBatch(it) }
            lastBulkBatchId = null
            return
        }
        scope.launch {
            val missingTracks = withContext(Dispatchers.IO) {
                uiState.value.musicTrackList.filter { catalogTrackNeedsDownload(it) }
            }
            if (missingTracks.isEmpty()) {
                val updatedList = refreshMusicTrackListDownloadState(uiState.value.musicTrackList)
                uiState.update { it.copy(musicTrackList = updatedList) }
                return@launch
            }
            pauseMusicForBulkDownload()
            val batchId = java.util.UUID.randomUUID().toString()
            lastBulkBatchId = batchId
            downloadQueueController.enqueueBatch(
                missingTracks.map { listTrack ->
                    musicEnqueueRequest(listTrack, DownloadPriority.BULK, batchId)
                },
                batchId,
            )
        }
    }

    suspend fun onMusicSnapshot(snapshot: PlaybackSnapshot) {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(musicDownloadInteractionState())) {
            return
        }
        val trackId = snapshot.trackId ?: return
        session.musicStartedInSession = true
        val libraryTracks = activeMusicLibraryTracks()
        if (libraryTracks.isNotEmpty() && trackId != session.lastPrefetchSourceTrackId) {
            if (session.musicLibraryTracks.isEmpty()) {
                session.musicLibraryTracks = libraryTracks
            }
            session.lastPrefetchSourceTrackId = trackId
            val index = libraryTracks.indexOfFirst { it.track.id == trackId }
            if (index >= 0) {
                scheduleMusicPrefetch(index + 1)
                appendMusicQueueWindowIfNeeded(libraryTracks)
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
                        musicPlaybackErrorMessage = null,
                    )
                }
            }
        }
    }

    fun handleMusicTrackEnded() {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(musicDownloadInteractionState())) {
            return
        }
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
        refreshMusicLibraryTracksLocalPaths()
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
        downloadQueueController.reconcileDownloadQueueBookIds()
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

    suspend fun resolveDownloadedTrackIdsForUi(
        reconcileLocalPaths: Boolean = true,
    ): Set<String> = withContext(Dispatchers.IO) {
        val ids = if (reconcileLocalPaths) {
            catalogRepository.reconcileLocalDownloadPaths()
            catalogRepository.getDownloadedTrackIds()
        } else {
            catalogRepository.getDownloadedTrackIdsFromCatalog()
        }.toMutableSet()
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

    suspend fun buildMusicTrackListForCatalogUpdate(
        rebuildMusic: Boolean = false,
        reconcileLocalPaths: Boolean = true,
    ): List<MusicListTrack> {
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi(reconcileLocalPaths)
        return buildMusicTrackListForCatalogUpdate(
            existing = uiState.value.musicTrackList,
            candidates = session.musicCandidates,
            musicStartedInSession = session.musicStartedInSession,
            downloadedTrackIds = downloadedTrackIds,
        )
    }

    suspend fun refreshMusicTrackListForDownloads(): List<MusicListTrack> {
        refreshMusicLibraryTracksLocalPaths()
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return refreshMusicTrackListDownloadState(uiState.value.musicTrackList, downloadedTrackIds)
    }

    private suspend fun refreshMusicLibraryTracksLocalPaths() {
        if (session.musicLibraryTracks.isEmpty()) return
        session.musicLibraryTracks = withContext(Dispatchers.IO) {
            session.musicLibraryTracks.map { entry ->
                val path = catalogRepository.resolveLocalTrackPath(entry.book.id, entry.track.id)
                if (path != null && path != entry.track.localPath) {
                    entry.copy(track = entry.track.copy(localPath = path))
                } else {
                    entry
                }
            }
        }
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

    private fun activeMusicLibraryTracks(): List<MusicLibraryTrack> =
        session.musicLibraryTracks.ifEmpty { musicPlaybackQueue.get() }

    private fun scheduleMusicPrefetch(fromIndex: Int) {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(musicDownloadInteractionState())) {
            return
        }
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
        if (withContext(Dispatchers.IO) { trackDownloadEnsurer.isTrackLocal(bookId, trackId) }) {
            val localTrack = withContext(Dispatchers.IO) {
                trackDownloadEnsurer.resolveLocalTrack(bookId, entry.track)
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
            return
        }
        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(bookId).find { it.id == trackId } ?: entry.track
        }
        downloadQueueController.enqueue(
            EnqueueDownloadRequest(
                bookId = bookId,
                trackId = trackId,
                priority = DownloadPriority.PREFETCH,
                title = resolvedTrack.title,
                subtitle = entry.book.title,
                contentType = ContentType.MUSIC.name.lowercase(),
            ),
        )
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

    private suspend fun appendMusicQueueWindowIfNeeded(libraryTracks: List<MusicLibraryTrack>) {
        val shouldAppend = withContext(Dispatchers.Main.immediate) {
            playbackClient.shouldAppendQueueItems()
        }
        if (!shouldAppend) return
        val queuedIds = withContext(Dispatchers.Main.immediate) {
            playbackClient.queuedTrackIds()
        }
        val lastQueuedTrackId = withContext(Dispatchers.Main.immediate) {
            playbackClient.lastQueuedTrackId()
        } ?: return
        val windowEntries = MusicQueueWindow.appendWindow(
            items = libraryTracks,
            lastMaterializedTrackId = lastQueuedTrackId,
            materializedTrackIds = queuedIds,
            idOf = { it.track.id },
        )
        if (windowEntries.isEmpty()) return
        val queueItems = withContext(Dispatchers.IO) {
            buildLocalMusicQueueItems(libraryTracks, windowEntries)
        }
        withContext(Dispatchers.Main.immediate) {
            playbackClient.appendQueueItems(queueItems)
        }
    }

    private suspend fun buildMusicLibraryTracksFromList(): List<MusicLibraryTrack> {
        val visible = visibleMusicTrackList()
        if (visible.isEmpty()) {
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
        return withContext(Dispatchers.IO) {
            val byTrackId = if (session.musicCandidates.isNotEmpty()) {
                session.musicCandidates.associate { (book, track) ->
                    track.id to MusicLibraryTrack(book, track)
                }
            } else {
                catalogRepository.resolveMusicLibraryTracks().associateBy { it.track.id }
            }
            visible.mapNotNull { item -> byTrackId[item.trackId] }
        }
    }

    private suspend fun musicBookAvailable(bookId: String): Boolean {
        if (uiState.value.books.any { it.id == bookId }) return true
        return withContext(Dispatchers.IO) { catalogRepository.getBook(bookId) != null }
    }

    private suspend fun playMusicTrack(
        track: MusicListTrack,
        showDownloadProgress: Boolean,
        advancePlayback: Boolean = false,
    ) {
        val downloadState = musicDownloadInteractionState()
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(downloadState)) {
            if (!track.isDownloaded || advancePlayback) return
        }
        if (!musicBookAvailable(track.bookId)) {
            reportMusicDownloadError()
            return
        }
        val libraryTracks = buildMusicLibraryTracksFromList()
        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.findTrackInCatalog(track.trackId)
        } ?: run {
            reportMusicDownloadError()
            return
        }
        val bookId = resolvedTrack.bookId
        val needsDownload = withContext(Dispatchers.IO) {
            !trackDownloadEnsurer.isTrackLocal(bookId, resolvedTrack.id)
        }
        if (needsDownload) {
            val awaitResult = downloadQueueController.awaitTrack(
                bookId = bookId,
                trackId = resolvedTrack.id,
                priority = DownloadPriority.PLAY,
                title = track.trackTitle,
                subtitle = track.artist,
                contentType = ContentType.MUSIC.name.lowercase(),
            )
            if (awaitResult != DownloadAwaitResult.COMPLETED) {
                if (advancePlayback) {
                    val failedIndex = session.musicLibraryTracks.indexOfFirst { it.track.id == track.trackId }
                    if (failedIndex >= 0) playNextAvailableFrom(failedIndex)
                } else {
                    reportMusicDownloadError(awaitResult)
                }
                return
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
        }
        val localTrack = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.resolveLocalTrack(bookId, resolvedTrack)
        } ?: run {
            if (advancePlayback) {
                val failedIndex = session.musicLibraryTracks.indexOfFirst { it.track.id == track.trackId }
                if (failedIndex >= 0) playNextAvailableFrom(failedIndex)
            } else {
                reportMusicDownloadError()
            }
            return
        }
        val queueWindow = MusicQueueWindow.initialWindow(
            items = libraryTracks,
            startTrackId = localTrack.id,
            idOf = { it.track.id },
        )
        val queue = withContext(Dispatchers.IO) {
            buildLocalMusicQueueItems(
                libraryTracks = libraryTracks,
                windowEntries = queueWindow,
                forcedLocalTrack = localTrack,
            )
        }
        if (queue.isEmpty()) {
            uiState.update {
                it.copy(musicPlaybackErrorMessage = playbackErrorMessage(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED))
            }
            return
        }
        session.musicStartedInSession = true
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        uiState.update { state ->
            state.copy(
                musicPlaybackErrorMessage = null,
                musicTrackList = state.musicTrackList.map { row ->
                    if (row.trackId == track.trackId) row.copy(isDownloaded = true) else row
                },
                nowPlayingTitle = resolvedTrack.title,
            )
        }
        session.musicBookIdByTrackId = session.musicBookIdByTrackId + (resolvedTrack.id to bookId)
        session.musicLibraryTracks = libraryTracks
        musicPlaybackQueue.set(libraryTracks)
        val libraryStartIndex = libraryTracks.indexOfFirst { it.track.id == localTrack.id }.coerceAtLeast(0)
        session.lastPrefetchSourceTrackId = localTrack.id
        playbackClient.playQueue(queue, startIndex)
        scheduleMusicPrefetch(libraryStartIndex + 1)
        refreshDownloadedBooks()
        localLibraryNotifier.notifyLocalLibraryChanged()
    }

    private suspend fun buildLocalMusicQueueItems(
        libraryTracks: List<MusicLibraryTrack>,
        windowEntries: List<MusicLibraryTrack>,
        forcedLocalTrack: Track? = null,
    ): List<QueuePlayItem> {
        if (windowEntries.isEmpty()) return emptyList()
        return windowEntries.mapNotNull { entry ->
            val localTrack = if (entry.track.id == forcedLocalTrack?.id) {
                forcedLocalTrack
            } else {
                trackDownloadEnsurer.resolveLocalTrack(entry.book.id, entry.track)
            } ?: return@mapNotNull null
            val index = libraryTracks.indexOfFirst { it.track.id == entry.track.id }.coerceAtLeast(0)
            playbackQueueBuilder.itemForMusicLibraryTrack(
                entry = entry,
                localTrack = localTrack,
                indexInLibrary = index,
                librarySize = libraryTracks.size,
            )
        }
    }

    private suspend fun refreshDownloadedBooks() {
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(uiState.value.books)
        }
        uiState.update { it.copy(downloadedBookIds = downloaded) }
    }
}
