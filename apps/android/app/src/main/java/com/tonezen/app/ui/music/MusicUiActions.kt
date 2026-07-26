package com.tonezen.app.ui.music

import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.music.MusicDownloadInteractionRules
import com.tonezen.app.domain.music.resolveMusicWaveDisplayTrack
import com.tonezen.app.playback.PlaybackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MusicUiActions(
    private val ctx: MusicHandlerContext,
    private val catalogLists: MusicCatalogLists,
    private val prefetch: MusicPrefetch,
    private val playExecutor: MusicPlayExecutor,
) {
    fun onMusicTabSelected() {
        ctx.scope.launch {
            if (ctx.session.musicCandidates.isEmpty()) return@launch
            val list = if (ctx.uiState.value.musicTrackList.isEmpty()) {
                if (ctx.session.musicStartedInSession) return@launch
                catalogLists.buildMusicTrackList(shuffle = true)
            } else {
                catalogLists.refreshMusicTrackListDownloadState(ctx.uiState.value.musicTrackList)
            }
            ctx.uiState.update { it.copy(musicTrackList = list) }
        }
    }

    fun onNetworkOffline() {
        prefetch.cancelPrefetchJobs()
    }

    fun onMiniPlayerPlayPause() {
        val playback = ctx.uiState.value.musicPlayback
        if (!playback.isActive || playback.trackId == null) {
            if (playback.isPlaying) ctx.playbackClient.pause() else ctx.playbackClient.play()
            return
        }
        val listedTrack = ctx.uiState.value.musicTrackList.find { it.trackId == playback.trackId }
        if (listedTrack != null) {
            onMusicTrackClick(listedTrack)
            return
        }
        ctx.scope.launch {
            val track = catalogLists.resolvePlaybackTrack(playback) ?: return@launch
            onMusicTrackClick(track)
        }
    }

    fun onMusicTrackClick(track: MusicListTrack) {
        if (!ctx.uiState.value.isNetworkOnline && !track.isDownloaded) return
        if (MusicDownloadInteractionRules.blocksUndownloadedTap(ctx.musicDownloadInteractionState()) &&
            !track.isDownloaded
        ) {
            return
        }
        val playback = ctx.uiState.value.musicPlayback
        if (playback.trackId == track.trackId && playback.isActive) {
            if (playback.isPlaying) {
                ctx.playbackClient.pause()
            } else if (!track.isDownloaded) {
                ctx.playJob?.cancel()
                ctx.uiState.update { it.copy(musicPlaybackErrorMessage = null) }
                ctx.playJob = ctx.scope.launch {
                    playExecutor.playMusicTrack(track, showDownloadProgress = true)
                }
            } else {
                ctx.playbackClient.play()
            }
            return
        }
        ctx.playJob?.cancel()
        ctx.uiState.update { it.copy(musicPlaybackErrorMessage = null) }
        ctx.playJob = ctx.scope.launch {
            playExecutor.playMusicTrack(track, showDownloadProgress = !track.isDownloaded)
        }
    }

    fun playMusicWave() {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(ctx.musicDownloadInteractionState())) {
            return
        }
        val playback = ctx.uiState.value.musicPlayback
        if (playback.isActive && playback.trackId != null) {
            onMiniPlayerPlayPause()
            return
        }
        val list = catalogLists.visibleMusicTrackList()
        val displayTrack = resolveMusicWaveDisplayTrack(
            tracks = list,
            activeTrackId = playback.trackId,
            isMusicActive = playback.isActive,
            trackIdOf = { it.trackId },
        ) ?: run {
            if (!ctx.uiState.value.isNetworkOnline) {
                ctx.uiState.update {
                    it.copy(musicPlaybackErrorMessage = ctx.playbackErrorMessage(EnsureTrackOutcome.Failure.OFFLINE))
                }
            }
            return
        }
        ctx.playJob?.cancel()
        ctx.uiState.update { it.copy(musicPlaybackErrorMessage = null) }
        ctx.playJob = ctx.scope.launch {
            playExecutor.playMusicTrack(
                track = displayTrack,
                showDownloadProgress = !displayTrack.isDownloaded,
                advancePlayback = true,
            )
        }
    }

    suspend fun onMusicSnapshot(snapshot: PlaybackSnapshot) {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(ctx.musicDownloadInteractionState())) {
            return
        }
        val trackId = snapshot.trackId ?: return
        ctx.session.musicStartedInSession = true
        val libraryTracks = prefetch.activeMusicLibraryTracks()
        if (libraryTracks.isNotEmpty() && trackId != ctx.session.lastPrefetchSourceTrackId) {
            if (ctx.session.musicLibraryTracks.isEmpty()) {
                ctx.session.musicLibraryTracks = libraryTracks
            }
            ctx.session.lastPrefetchSourceTrackId = trackId
            val index = libraryTracks.indexOfFirst { it.track.id == trackId }
            if (index >= 0) {
                prefetch.scheduleMusicPrefetch(index + 1)
                prefetch.appendMusicQueueWindowIfNeeded(libraryTracks)
            }
        }
    }

    fun musicPlaybackUi(snapshot: PlaybackSnapshot): MusicPlaybackUi {
        val trackId = snapshot.trackId
        val isMusic = snapshot.contentType == ContentType.MUSIC ||
            (trackId != null && trackId in ctx.session.musicBookIdByTrackId)
        return MusicPlaybackUi(
            isActive = isMusic && trackId != null,
            trackId = trackId,
            trackTitle = snapshot.trackTitle,
            artist = snapshot.artist,
            albumTitle = snapshot.albumTitle,
            bookId = trackId?.let { id -> ctx.session.musicBookIdByTrackId[id] },
            isPlaying = snapshot.isPlaying && isMusic,
        )
    }

    fun isMusicSnapshot(snapshot: PlaybackSnapshot): Boolean {
        val trackId = snapshot.trackId
        return snapshot.contentType == ContentType.MUSIC ||
            (trackId != null && trackId in ctx.session.musicBookIdByTrackId)
    }

    suspend fun invalidatePlaybackIfLocalFilesMissing() {
        val snapshot = ctx.playbackClient.snapshot.value
        val trackId = snapshot.trackId ?: return
        val isMusic = snapshot.contentType == ContentType.MUSIC || trackId in ctx.session.musicBookIdByTrackId
        if (!isMusic) return
        val bookId = ctx.session.musicBookIdByTrackId[trackId]
            ?: withContext(Dispatchers.IO) { ctx.catalogRepository.findBookForTrack(trackId)?.id }
            ?: return
        val isLocal = withContext(Dispatchers.IO) {
            ctx.trackDownloadEnsurer.isTrackLocal(bookId, trackId)
        }
        if (!isLocal) {
            ctx.playJob?.cancel()
            prefetch.cancelPrefetchJobs()
            val currentIndex = ctx.session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
            if (currentIndex >= 0) {
                ctx.playJob?.cancel()
                ctx.playJob = ctx.scope.launch {
                    playExecutor.playNextAvailableFrom(currentIndex)
                }
            } else {
                prefetch.clearMusicPrefetchState()
                ctx.playbackClient.stopAndRelease()
                ctx.uiState.update {
                    it.copy(
                        musicPlayback = MusicPlaybackUi(),
                        musicPlaybackErrorMessage = null,
                    )
                }
            }
        }
    }

    fun handleMusicTrackEnded() {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(ctx.musicDownloadInteractionState())) {
            return
        }
        val snapshot = ctx.playbackClient.snapshot.value
        val trackId = snapshot.trackId ?: return
        val isMusic = snapshot.contentType == ContentType.MUSIC || trackId in ctx.session.musicBookIdByTrackId
        if (!isMusic || ctx.session.musicLibraryTracks.isEmpty()) return
        val currentIndex = ctx.session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
        if (currentIndex < 0) return
        ctx.playJob?.cancel()
        ctx.playJob = ctx.scope.launch {
            playExecutor.playNextAvailableFrom(currentIndex)
        }
    }
}
