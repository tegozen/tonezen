package com.tonezen.app.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class DownloadQueueNotifier @Inject constructor() {
    private val _state = MutableStateFlow(DownloadQueueState())
    val state: StateFlow<DownloadQueueState> = _state.asStateFlow()

    fun update(transform: (DownloadQueueState) -> DownloadQueueState) {
        _state.update { transform(it).trimHistory() }
    }

    fun snapshot(): DownloadQueueState = _state.value
}
