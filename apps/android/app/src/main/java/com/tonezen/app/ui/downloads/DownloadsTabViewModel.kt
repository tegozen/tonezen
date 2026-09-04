package com.tonezen.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.domain.downloads.DownloadResumePolicy
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.DownloadQueueItem
import com.tonezen.app.playback.DownloadQueueItemStatus
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.DownloadQueueState
import com.tonezen.app.playback.TrackDownloadQueueController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadsTabUiState(
    val activeItems: List<DownloadQueueItem> = emptyList(),
    val completedItems: List<DownloadListItem> = emptyList(),
    val pausedForNetwork: Boolean = false,
)

data class DownloadListItem(
    val bookId: String,
    val trackId: String,
    val title: String,
    val subtitle: String?,
    val contentType: String,
    val durationMs: Long?,
    val completedAt: Long,
)

@HiltViewModel
class DownloadsTabViewModel @Inject constructor(
    downloadQueueNotifier: DownloadQueueNotifier,
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadQueueController: TrackDownloadQueueController,
    private val localLibraryNotifier: LocalLibraryNotifier,
) : ViewModel() {
    private val completedFromDb = kotlinx.coroutines.flow.MutableStateFlow<List<DownloadListItem>>(emptyList())

    val uiState: StateFlow<DownloadsTabUiState> = combine(
        downloadQueueNotifier.state,
        completedFromDb,
    ) { queueState, completed ->
        queueState.toTabUiState(completed)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DownloadsTabUiState(),
    )

    init {
        refreshCompletedFromDb()
        viewModelScope.launch {
            downloadQueueNotifier.state
                .scan(false to false) { (_, wasActive), state ->
                    wasActive to state.isActive
                }
                .drop(1)
                .map { (wasActive, isActive) -> wasActive && !isActive }
                .filter { it }
                .debounce(DOWNLOADS_TAB_REFRESH_DEBOUNCE_MS)
                .collect { refreshCompletedFromDb() }
        }
    }

    fun refreshCompletedFromDb() {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksOrderedByDownloadedAt(DownloadResumePolicy.COMPLETED_HISTORY_LIMIT)
            }
            completedFromDb.value = tracks.map { it.toDownloadListItem() }
        }
    }

    fun cancelTrack(bookId: String, trackId: String) {
        downloadQueueController.cancelTrack(bookId, trackId)
    }

    fun cancelAll() {
        downloadQueueController.cancelAll()
    }

    fun deleteCompleted(bookId: String, trackId: String) {
        viewModelScope.launch {
            downloadQueueController.cancelTrack(bookId, trackId)
            withContext(Dispatchers.IO) {
                downloadRepository.deleteLocalTrack(bookId, trackId)
                catalogRepository.clearTrackLocalPath(bookId, trackId)
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshCompletedFromDb()
        }
    }

    private fun DownloadQueueState.toTabUiState(
        completedFromDb: List<DownloadListItem>,
    ): DownloadsTabUiState {
        val active = queuedItems
            .filter {
                it.status == DownloadQueueItemStatus.QUEUED ||
                    it.status == DownloadQueueItemStatus.DOWNLOADING ||
                    it.status == DownloadQueueItemStatus.PAUSED_OFFLINE
            }
            .map { item ->
                if (item.bookId == activeBookId && item.trackId == activeTrackId) {
                    item.copy(progress = activeProgress)
                } else {
                    item
                }
            }
        val liveCompleted = completedHistory
            .filter { it.status == DownloadQueueItemStatus.COMPLETED }
            .map { it.toDownloadListItem() }
        val mergedCompleted = mergeCompleted(liveCompleted, completedFromDb)
        return DownloadsTabUiState(
            activeItems = active,
            completedItems = mergedCompleted,
            pausedForNetwork = pausedForNetwork,
        )
    }

    private fun mergeCompleted(
        live: List<DownloadListItem>,
        fromDb: List<DownloadListItem>,
    ): List<DownloadListItem> {
        val byTrackId = linkedMapOf<String, DownloadListItem>()
        fromDb.sortedBy { it.completedAt }.forEach { byTrackId[it.trackId] = it }
        live.forEach { item ->
            byTrackId[item.trackId] = item
        }
        return byTrackId.values
            .sortedBy { it.completedAt }
            .takeLast(DownloadResumePolicy.COMPLETED_HISTORY_LIMIT)
    }

    private fun DownloadQueueItem.toDownloadListItem(): DownloadListItem =
        DownloadListItem(
            bookId = bookId,
            trackId = trackId,
            title = title,
            subtitle = subtitle,
            contentType = contentType,
            durationMs = null,
            completedAt = completedAt ?: enqueuedAt,
        )

    private fun Track.toDownloadListItem(): DownloadListItem =
        DownloadListItem(
            bookId = bookId,
            trackId = id,
            title = title,
            subtitle = null,
            contentType = "music",
            durationMs = durationMs,
            completedAt = localDownloadedAt ?: 0L,
        )
}

private const val DOWNLOADS_TAB_REFRESH_DEBOUNCE_MS = 300L
