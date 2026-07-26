package com.tonezen.app.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val trackTitle: String? = null,
    val trackId: String? = null,
    val artist: String? = null,
    val albumTitle: String? = null,
    val contentType: com.tonezen.app.domain.model.ContentType? = null,
    val canSeekToNextMediaItem: Boolean = false,
    val canSeekToPreviousMediaItem: Boolean = false,
)

@Singleton
class PlaybackClient @Inject constructor(
    @ApplicationContext context: Context,
    playbackMediaFactory: PlaybackMediaFactory,
) {
    private val shared = PlaybackClientShared(context, playbackMediaFactory)
    private lateinit var queue: PlaybackClientQueue
    private val snaps = PlaybackClientSnapshot(shared) { mediaController ->
        shared.pendingQueuePlay?.let { pending ->
            queue.executePlayQueue(
                mediaController,
                pending.items,
                pending.startIndex,
                pending.startPositionMs,
            )
            shared.pendingQueuePlay = null
        }
    }

    init {
        queue = PlaybackClientQueue(
            shared = shared,
            refreshSnapshot = snaps::refreshSnapshot,
            connect = snaps::connect,
        )
    }

    val snapshot: StateFlow<PlaybackSnapshot> = shared.snapshot
    val activeTrackId: SharedFlow<String> = shared.activeTrackId

    fun connect() = snaps.connect()

    fun playQueue(items: List<QueuePlayItem>, startIndex: Int, startPositionMs: Long = 0L) =
        queue.playQueue(items, startIndex, startPositionMs)

    fun pause() {
        connect()
        shared.controller?.pause()
    }

    fun play() {
        connect()
        shared.controller?.play()
    }

    fun seekTo(positionMs: Long) {
        shared.controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val controller = shared.controller ?: return
        val target = (controller.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = controller.duration
        controller.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }

    fun setPlaybackSpeed(speed: Float) {
        shared.controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    fun playbackSpeed(): Float = shared.controller?.playbackParameters?.speed ?: 1f

    fun skipToNext() {
        shared.controller?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        shared.controller?.seekToPreviousMediaItem()
    }

    fun queuedTrackIds(): Set<String> = queue.queuedTrackIds()

    fun lastQueuedTrackId(): String? = queue.lastQueuedTrackId()

    fun shouldAppendQueueItems(): Boolean = queue.shouldAppendQueueItems()

    fun appendQueueItems(items: List<QueuePlayItem>) = queue.appendQueueItems(items)

    fun isPlaying(): Boolean = shared.controller?.isPlaying == true

    fun currentPositionMs(): Long = shared.controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun durationMs(): Long = shared.controller?.duration?.coerceAtLeast(0L) ?: 0L

    fun stopAndRelease() = snaps.stopAndRelease()
}
