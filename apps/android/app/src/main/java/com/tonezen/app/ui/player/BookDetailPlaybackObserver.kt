package com.tonezen.app.ui.player

import com.tonezen.app.playback.PlaybackClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class BookDetailPlaybackObserver(
    private val uiState: MutableStateFlow<BookDetailUiState>,
    private val playbackProgress: MutableStateFlow<BookDetailPlaybackProgress>,
    private val playbackClient: PlaybackClient,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            playbackClient.activeTrackId.collect { trackId ->
                val activeTrackId = uiState.value.tracks.find { it.id == trackId }?.id
                uiState.update { it.copy(activeTrackId = activeTrackId) }
            }
        }
        scope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                val playbackState = resolveBookDetailPlaybackState(uiState.value.tracks, snapshot)
                playbackProgress.value = BookDetailPlaybackProgress(
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs,
                )
                uiState.update {
                    val next = it.copy(
                        activeTrackId = playbackState.activeTrackId,
                        isPlaying = playbackState.isPlaying,
                        isPlaybackActiveForBook = playbackState.isActiveForBook,
                    )
                    if (next == it) it else next
                }
            }
        }
    }
}
