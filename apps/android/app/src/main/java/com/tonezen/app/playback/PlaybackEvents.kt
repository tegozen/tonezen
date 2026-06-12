package com.tonezen.app.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackEvents @Inject constructor() {
    private val _skipToNext = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _skipToPrevious = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _trackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val skipToNext: SharedFlow<Unit> = _skipToNext.asSharedFlow()
    val skipToPrevious: SharedFlow<Unit> = _skipToPrevious.asSharedFlow()
    val trackEnded: SharedFlow<Unit> = _trackEnded.asSharedFlow()

    fun requestSkipToNext() {
        _skipToNext.tryEmit(Unit)
    }

    fun requestSkipToPrevious() {
        _skipToPrevious.tryEmit(Unit)
    }

    fun notifyTrackEnded() {
        _trackEnded.tryEmit(Unit)
    }
}
