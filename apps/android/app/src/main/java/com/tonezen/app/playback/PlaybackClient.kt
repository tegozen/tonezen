package com.tonezen.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.MediaMetadata
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.music.MusicQueueWindow
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
    val artist: String? = null,
    val albumTitle: String? = null,
    val contentType: ContentType? = null,
    val canSeekToNextMediaItem: Boolean = false,
    val canSeekToPreviousMediaItem: Boolean = false,
)

private data class PendingQueuePlay(
    val items: List<QueuePlayItem>,
    val startIndex: Int,
    val startPositionMs: Long,
)

@Singleton
class PlaybackClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackMediaFactory: PlaybackMediaFactory,
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
    private var lastContentType: ContentType? = null

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
        items.getOrNull(safeIndex)?.let { item ->
            lastContentType = item.metadata.contentType
            _snapshot.update {
                PlaybackSnapshot(
                    isPlaying = true,
                    trackTitle = item.metadata.trackTitle,
                    trackId = item.trackId,
                    artist = item.metadata.artist,
                    albumTitle = item.metadata.albumTitle,
                    contentType = item.metadata.contentType,
                )
            }
        }
        val mediaController = controller
        if (mediaController == null) {
            pendingQueuePlay = PendingQueuePlay(items, safeIndex, startPositionMs)
            return
        }
        executePlayQueue(mediaController, items, safeIndex, startPositionMs)
    }

    fun pause() {
        connect()
        controller?.pause()
    }

    fun play() {
        connect()
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

    fun queuedTrackIds(): Set<String> {
        val mediaController = controller ?: return emptySet()
        return (0 until mediaController.mediaItemCount)
            .mapNotNull { index ->
                mediaController.getMediaItemAt(index).mediaId?.takeIf { it.isNotEmpty() }
            }
            .toSet()
    }

    fun lastQueuedTrackId(): String? {
        val mediaController = controller ?: return null
        if (mediaController.mediaItemCount <= 0) return null
        return mediaController.getMediaItemAt(mediaController.mediaItemCount - 1)
            .mediaId
            ?.takeIf { it.isNotEmpty() }
    }

    fun shouldAppendQueueItems(): Boolean {
        val mediaController = controller ?: return false
        return MusicQueueWindow.shouldAppend(
            currentIndex = mediaController.currentMediaItemIndex,
            queueSize = mediaController.mediaItemCount,
        )
    }

    fun appendQueueItems(items: List<QueuePlayItem>) {
        if (items.isEmpty()) return
        connect()
        val mediaController = controller ?: return
        val existingIds = queuedTrackIds()
        val newItems = items.filter { it.trackId !in existingIds }
        if (newItems.isEmpty()) return
        mediaController.addMediaItems(newItems.map(playbackMediaFactory::toMediaItem))
        refreshSnapshot(mediaController)
    }

    fun isPlaying(): Boolean = controller?.isPlaying == true

    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun durationMs(): Long = controller?.duration?.coerceAtLeast(0L) ?: 0L

    fun stopAndRelease() {
        stopPositionTicks()
        listener?.let { controller?.removeListener(it) }
        listener = null
        controller?.run {
            stop()
            clearMediaItems()
            release()
        }
        controller = null
        controllerFuture = null
        pendingQueuePlay = null
        lastContentType = null
        _snapshot.value = PlaybackSnapshot()
    }

    private fun executePlayQueue(
        mediaController: MediaController,
        items: List<QueuePlayItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        val startItem = items.getOrNull(startIndex)
        startItem?.metadata?.contentType?.let { lastContentType = it }
        val mediaItems = items.map(playbackMediaFactory::toMediaItem)
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
        val currentItem = mediaController.currentMediaItem
        val metadata = currentItem?.mediaMetadata ?: mediaController.mediaMetadata
        _snapshot.update {
            PlaybackSnapshot(
                isPlaying = mediaController.isPlaying,
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = mediaController.duration.coerceAtLeast(0L),
                trackTitle = metadata.title?.toString(),
                trackId = currentItem?.mediaId,
                artist = metadata.artist?.toString(),
                albumTitle = metadata.albumTitle?.toString(),
                contentType = lastContentType ?: metadata.mediaType?.toContentType(),
                canSeekToNextMediaItem = mediaController.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
                canSeekToPreviousMediaItem = mediaController.isCommandAvailable(
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                ),
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

private fun Int.toContentType(): ContentType? =
    when (this) {
        MediaMetadata.MEDIA_TYPE_MUSIC -> ContentType.MUSIC
        MediaMetadata.MEDIA_TYPE_AUDIO_BOOK -> ContentType.AUDIOBOOK
        else -> null
    }
