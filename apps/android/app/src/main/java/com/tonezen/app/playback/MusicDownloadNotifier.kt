package com.tonezen.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class MusicDownloadState(
    val activeTrackId: String? = null,
    val trackProgress: Float? = null,
    val bulkDownloaded: Int = 0,
    val bulkTotal: Int = 0,
    val queuedTrackIds: Set<String> = emptySet(),
) {
    val bulkProgress: Float?
        get() = when {
            bulkTotal <= 0 -> null
            activeTrackId != null && trackProgress != null ->
                ((bulkDownloaded + trackProgress) / bulkTotal).coerceIn(0f, 1f)
            else -> (bulkDownloaded.toFloat() / bulkTotal).coerceIn(0f, 1f)
        }

    val isActive: Boolean
        get() = isTrackDownloading || isBulkDownloading

    val isTrackDownloading: Boolean
        get() = activeTrackId != null && trackProgress != null

    val isBulkDownloading: Boolean
        get() = bulkTotal > 0 && bulkDownloaded < bulkTotal

    fun progressForTrack(trackId: String): Float? =
        if (activeTrackId == trackId) trackProgress else null

    fun isTrackQueued(trackId: String): Boolean = trackId in queuedTrackIds
}

@Singleton
class MusicDownloadNotifier @Inject constructor() {
    private val _state = MutableStateFlow(MusicDownloadState())
    val state: StateFlow<MusicDownloadState> = _state.asStateFlow()

    fun beginTrack(trackId: String) {
        _state.update { it.copy(activeTrackId = trackId, trackProgress = 0f) }
    }

    fun updateTrack(trackId: String, progress: Float) {
        _state.update {
            it.copy(
                activeTrackId = trackId,
                trackProgress = progress.coerceIn(0f, 1f),
            )
        }
    }

    fun finishTrack() {
        _state.update { it.copy(activeTrackId = null, trackProgress = null) }
    }

    fun beginBulk(downloaded: Int, total: Int) {
        _state.update {
            MusicDownloadState(
                bulkDownloaded = downloaded,
                bulkTotal = total,
            )
        }
    }

    fun updateBulk(
        downloaded: Int,
        total: Int,
        currentTrackId: String,
        currentTrackProgress: Float,
    ) {
        _state.update {
            it.copy(
                bulkDownloaded = downloaded,
                bulkTotal = total,
                activeTrackId = currentTrackId,
                trackProgress = currentTrackProgress.coerceIn(0f, 1f),
            )
        }
    }

    fun incrementBulkDownloaded(downloaded: Int, total: Int) {
        _state.update {
            it.copy(
                bulkDownloaded = downloaded,
                bulkTotal = total,
                activeTrackId = null,
                trackProgress = null,
            )
        }
    }

    fun clear() {
        _state.value = MusicDownloadState()
    }
}
