package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Facade ViewModel for the now-playing sheet.
 * Album/up-next context lives in [NowPlayingCatalogContext], queue building/playback in
 * [NowPlayingQueueExecutor]; this class owns [NowPlayingUiState] and playback transport.
 */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackClient: PlaybackClient,
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    playbackQueueBuilder: PlaybackQueueBuilder,
    trackDownloadEnsurer: TrackDownloadEnsurer,
    downloadQueueController: TrackDownloadQueueController,
    musicPlaybackQueue: MusicPlaybackQueue,
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    private val catalogContext = NowPlayingCatalogContext(
        uiState = _uiState,
        catalogRepository = catalogRepository,
        musicPlaybackQueue = musicPlaybackQueue,
        networkMonitor = networkMonitor,
    )
    private val queueExecutor = NowPlayingQueueExecutor(
        uiState = _uiState,
        catalogContext = catalogContext,
        catalogRepository = catalogRepository,
        playbackClient = playbackClient,
        playbackQueueBuilder = playbackQueueBuilder,
        trackDownloadEnsurer = trackDownloadEnsurer,
        downloadQueueController = downloadQueueController,
        networkMonitor = networkMonitor,
    )

    private var playJob: Job? = null
    private var catalogJob: Job? = null

    init {
        playbackClient.connect()
        viewModelScope.launch {
            var lastCatalogTrackId: String? = null
            playbackClient.snapshot.collect { snapshot ->
                val blocksPlaybackUi = false
                _uiState.update {
                    val nextCoverSeed = snapshot.trackId ?: snapshot.trackTitle
                    it.copy(
                        title = if (blocksPlaybackUi) it.title else snapshot.trackTitle,
                        subtitle = if (blocksPlaybackUi) {
                            it.subtitle
                        } else {
                            formatSubtitle(snapshot.artist, snapshot.albumTitle)
                        },
                        coverSeed = if (blocksPlaybackUi) {
                            it.coverSeed
                        } else {
                            nextCoverSeed
                        },
                        waveformPeaks = if (blocksPlaybackUi || nextCoverSeed == it.coverSeed) {
                            it.waveformPeaks
                        } else {
                            null
                        },
                        isPlaying = if (blocksPlaybackUi) false else snapshot.isPlaying,
                        positionMs = if (blocksPlaybackUi) it.positionMs else snapshot.positionMs,
                        durationMs = if (blocksPlaybackUi) it.durationMs else snapshot.durationMs,
                        contentType = snapshot.contentType ?: it.contentType,
                        canSkipNext = snapshot.canSeekToNextMediaItem,
                        canSkipPrevious = snapshot.canSeekToPreviousMediaItem,
                    )
                }
                val trackId = snapshot.trackId
                if (trackId != null && trackId != lastCatalogTrackId) {
                    lastCatalogTrackId = trackId
                    scheduleAlbumRefresh(trackId)
                }
            }
        }
    }

    fun refreshCatalogContext() {
        val trackId = playbackClient.snapshot.value.trackId ?: return
        catalogContext.preserveShuffleOrder = true
        scheduleAlbumRefresh(trackId)
    }

    private fun scheduleAlbumRefresh(trackId: String) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch(Dispatchers.IO) {
            catalogContext.refreshUpNext(trackId)
        }
    }

    fun pauseOrResume() {
        if (_uiState.value.isPlaying) playbackClient.pause() else playbackClient.play()
    }

    fun seekTo(positionMs: Long) {
        playbackClient.seekTo(positionMs)
        persistAudiobookSeek(positionMs)
    }

    fun seekBy(deltaMs: Long) {
        playbackClient.seekBy(deltaMs)
        persistAudiobookSeek(playbackClient.snapshot.value.positionMs)
    }

    private fun persistAudiobookSeek(positionMs: Long) {
        val snapshot = playbackClient.snapshot.value
        if (snapshot.contentType != ContentType.AUDIOBOOK) return
        val trackId = snapshot.trackId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val bookId = catalogRepository.findBookForTrack(trackId)?.id ?: return@launch
            val existing = catalogRepository.getProgress(bookId)
            val effective = when {
                positionMs > 0L -> positionMs
                existing?.trackId == trackId && existing.positionMs > 0L -> existing.positionMs
                else -> 1L
            }
            val progress = AudiobookProgress(
                bookId = bookId,
                trackId = trackId,
                positionMs = effective,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
            progressSyncRepository.saveLocal(progress, pendingSync = true, session?.accessToken)
        }
    }

    fun skipPrevious() {
        playbackClient.skipToPrevious()
    }

    fun skipNext() {
        playbackClient.skipToNext()
    }

    fun playTrack(track: Track) {
        val index = catalogContext.libraryTracks.indexOfFirst { it.track.id == track.id }
        if (index >= 0) {
            catalogContext.preserveShuffleOrder = true
            skipToIndex(index)
        }
    }

    private fun skipToIndex(index: Int) {
        if (index !in catalogContext.libraryTracks.indices) return
        playJob?.cancel()
        playJob = viewModelScope.launch {
            queueExecutor.playQueueAt(index)
        }
    }
}
