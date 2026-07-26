package com.tonezen.app.playback

import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import com.google.common.util.concurrent.ListenableFuture
import com.tonezen.app.domain.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PendingQueuePlay(
    val items: List<QueuePlayItem>,
    val startIndex: Int,
    val startPositionMs: Long,
)

/** Mutable MediaController runtime shared by PlaybackClient collaborators. */
internal class PlaybackClientShared(
    val context: Context,
    val playbackMediaFactory: PlaybackMediaFactory,
) {
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()
    fun snapshotFlow(): MutableStateFlow<PlaybackSnapshot> = _snapshot

    private val _activeTrackId = MutableSharedFlow<String>(extraBufferCapacity = 1, replay = 1)
    val activeTrackId: SharedFlow<String> = _activeTrackId.asSharedFlow()
    fun emitActiveTrackId(trackId: String) {
        _activeTrackId.tryEmit(trackId)
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var positionTickJob: Job? = null
    var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
    var listener: Player.Listener? = null
    var pendingQueuePlay: PendingQueuePlay? = null
    var lastContentType: ContentType? = null
}
