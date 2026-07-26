package com.tonezen.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.CatalogSyncRepository
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class MusicViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    catalogSyncRepository: CatalogSyncRepository,
    sessionRepository: SessionRepository,
    downloadRepository: DownloadRepository,
    networkMonitor: NetworkMonitor,
    playbackClient: PlaybackClient,
    playbackQueueBuilder: PlaybackQueueBuilder,
    trackDownloadEnsurer: TrackDownloadEnsurer,
    downloadQueueController: TrackDownloadQueueController,
    downloadQueueNotifier: DownloadQueueNotifier,
    localLibraryNotifier: LocalLibraryNotifier,
    playbackEvents: PlaybackEvents,
    musicPlaybackQueue: MusicPlaybackQueue,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val session = MusicPlaybackSession()
    private val musicHandler = MusicHandler(
        uiState = _uiState,
        scope = viewModelScope,
        session = session,
        catalogRepository = catalogRepository,
        downloadRepository = downloadRepository,
        trackDownloadEnsurer = trackDownloadEnsurer,
        downloadQueueController = downloadQueueController,
        downloadQueueNotifier = downloadQueueNotifier,
        localLibraryNotifier = localLibraryNotifier,
        playbackClient = playbackClient,
        playbackQueueBuilder = playbackQueueBuilder,
        musicPlaybackQueue = musicPlaybackQueue,
        playbackErrorMessage = ::musicPlaybackErrorMessage,
    )
    private val catalogLoader = MusicCatalogLoader(
        uiState = _uiState,
        scope = viewModelScope,
        session = session,
        catalogRepository = catalogRepository,
        sessionRepository = sessionRepository,
        networkMonitor = networkMonitor,
        musicHandler = musicHandler,
    )
    private val sessionObserver = MusicSessionObserver(
        scope = viewModelScope,
        uiState = _uiState,
        musicHandler = musicHandler,
        catalogLoader = catalogLoader,
        sessionRepository = sessionRepository,
        catalogSyncRepository = catalogSyncRepository,
        networkMonitor = networkMonitor,
        playbackClient = playbackClient,
        playbackEvents = playbackEvents,
        downloadQueueNotifier = downloadQueueNotifier,
        localLibraryNotifier = localLibraryNotifier,
    )

    init {
        sessionObserver.start()
    }

    fun onMusicTabSelected() = musicHandler.onMusicTabSelected()

    fun onMiniPlayerPlayPause() = musicHandler.onMiniPlayerPlayPause()

    fun playMusicWave() = musicHandler.playMusicWave()

    fun onMusicTrackClick(track: MusicListTrack) = musicHandler.onMusicTrackClick(track)

    fun downloadMusicTrack(track: MusicListTrack) = musicHandler.downloadMusicTrack(track)

    fun deleteMusicTrack(track: MusicListTrack) = musicHandler.deleteMusicTrack(track)

    fun downloadAllMusic() = musicHandler.downloadAllMusic()

    fun cancelAllDownloads() = musicHandler.cancelAllDownloads()

    fun clearMusicPlaybackError() {
        _uiState.update { it.copy(musicPlaybackErrorMessage = null) }
    }
}

private fun musicPlaybackErrorMessage(failure: EnsureTrackOutcome.Failure?): String = when (failure) {
    EnsureTrackOutcome.Failure.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
    EnsureTrackOutcome.Failure.NO_SESSION -> "Войдите в аккаунт, чтобы скачать трек"
    EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> "Не удалось скачать трек"
}
