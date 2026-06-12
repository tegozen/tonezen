package com.tonezen.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val trackTitle: String? = null,
    val trackId: String? = null,
)

private data class PendingQueuePlay(
    val items: List<QueuePlayItem>,
    val startIndex: Int,
    val startPositionMs: Long,
)

@Singleton
class PlaybackClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val _activeTrackId = MutableSharedFlow<String>(extraBufferCapacity = 1, replay = 1)
    val activeTrackId: SharedFlow<String> = _activeTrackId.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionTickJob: Job? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var listener: Player.Listener? = null
    private var pendingQueuePlay: PendingQueuePlay? = null

    fun connect() {
        if (controllerFuture != null) return

        context.startForegroundService(Intent(context, PlaybackService::class.java))

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                val mediaController = controllerFuture?.get() ?: return@addListener
                controller = mediaController
                attachListener(mediaController)
                pendingQueuePlay?.let { pending ->
                    executePlayQueue(mediaController, pending.items, pending.startIndex, pending.startPositionMs)
                    pendingQueuePlay = null
                }
                refreshSnapshot(mediaController)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun playQueue(items: List<QueuePlayItem>, startIndex: Int, startPositionMs: Long = 0L) {
        if (items.isEmpty()) return
        connect()
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        val mediaController = controller
        if (mediaController == null) {
            pendingQueuePlay = PendingQueuePlay(items, safeIndex, startPositionMs)
            return
        }
        executePlayQueue(mediaController, items, safeIndex, startPositionMs)
    }

    fun pause() {
        controller?.pause()
    }

    fun play() {
        controller?.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val controller = controller ?: return
        val target = (controller.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = controller.duration
        controller.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    fun playbackSpeed(): Float = controller?.playbackParameters?.speed ?: 1f

    fun skipToNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun isPlaying(): Boolean = controller?.isPlaying == true

    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun durationMs(): Long = controller?.duration?.coerceAtLeast(0L) ?: 0L

    fun stopAndRelease() {
        stopPositionTicks()
        listener?.let { controller?.removeListener(it) }
        listener = null
        controller?.release()
        controller = null
        controllerFuture = null
        pendingQueuePlay = null
        _snapshot.value = PlaybackSnapshot()
    }

    private fun executePlayQueue(
        mediaController: MediaController,
        items: List<QueuePlayItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        val mediaItems = items.map(PlaybackMediaFactory::toMediaItem)
        mediaController.setMediaItems(mediaItems, startIndex, startPositionMs)
        mediaController.prepare()
        mediaController.play()
        items.getOrNull(startIndex)?.trackId?.let { _activeTrackId.tryEmit(it) }
        refreshSnapshot(mediaController)
    }

    private fun attachListener(mediaController: MediaController) {
        listener?.let { mediaController.removeListener(it) }
        listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshSnapshot(mediaController)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshSnapshot(mediaController)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { _activeTrackId.tryEmit(it) }
                refreshSnapshot(mediaController)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) {
                    refreshSnapshot(mediaController)
                }
            }
        }.also { mediaController.addListener(it) }
    }

    private fun refreshSnapshot(mediaController: MediaController) {
        _snapshot.update {
            PlaybackSnapshot(
                isPlaying = mediaController.isPlaying,
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = mediaController.duration.coerceAtLeast(0L),
                trackTitle = mediaController.mediaMetadata.title?.toString(),
                trackId = mediaController.currentMediaItem?.mediaId,
            )
        }
        if (mediaController.isPlaying) {
            startPositionTicks(mediaController)
        } else {
            stopPositionTicks()
        }
    }

    private fun startPositionTicks(mediaController: MediaController) {
        if (positionTickJob?.isActive == true) return
        positionTickJob = scope.launch {
            while (isActive) {
                delay(1000)
                controller?.let { refreshSnapshot(it) }
            }
        }
    }

    private fun stopPositionTicks() {
        positionTickJob?.cancel()
        positionTickJob = null
    }
}
