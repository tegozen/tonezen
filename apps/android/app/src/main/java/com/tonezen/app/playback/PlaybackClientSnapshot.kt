package com.tonezen.app.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tonezen.app.domain.model.ContentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PlaybackClientSnapshot(
    private val shared: PlaybackClientShared,
    private val onControllerReady: (MediaController) -> Unit,
) {
    fun connect() {
        if (shared.controllerFuture != null) return

        shared.context.startForegroundService(Intent(shared.context, PlaybackService::class.java))

        val sessionToken = SessionToken(
            shared.context,
            ComponentName(shared.context, PlaybackService::class.java),
        )
        shared.controllerFuture = MediaController.Builder(shared.context, sessionToken).buildAsync()
        shared.controllerFuture?.addListener(
            {
                val mediaController = shared.controllerFuture?.get() ?: return@addListener
                shared.controller = mediaController
                attachListener(mediaController)
                onControllerReady(mediaController)
                refreshSnapshot(mediaController)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun attachListener(mediaController: MediaController) {
        shared.listener?.let { mediaController.removeListener(it) }
        shared.listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshSnapshot(mediaController)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshSnapshot(mediaController)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let { shared.emitActiveTrackId(it) }
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

    fun refreshSnapshot(mediaController: MediaController) {
        val currentItem = mediaController.currentMediaItem
        val metadata = currentItem?.mediaMetadata ?: mediaController.mediaMetadata
        shared.snapshotFlow().update {
            PlaybackSnapshot(
                isPlaying = mediaController.isPlaying,
                positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                durationMs = mediaController.duration.coerceAtLeast(0L),
                trackTitle = metadata.title?.toString(),
                trackId = currentItem?.mediaId,
                artist = metadata.artist?.toString(),
                albumTitle = metadata.albumTitle?.toString(),
                contentType = shared.lastContentType ?: metadata.mediaType?.toContentType(),
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

    fun startPositionTicks(mediaController: MediaController) {
        if (shared.positionTickJob?.isActive == true) return
        shared.positionTickJob = shared.scope.launch {
            while (isActive) {
                delay(1000)
                shared.controller?.let { refreshSnapshot(it) }
            }
        }
    }

    fun stopPositionTicks() {
        shared.positionTickJob?.cancel()
        shared.positionTickJob = null
    }

    fun stopAndRelease() {
        stopPositionTicks()
        shared.listener?.let { shared.controller?.removeListener(it) }
        shared.listener = null
        shared.controller?.run {
            stop()
            clearMediaItems()
            release()
        }
        shared.controller = null
        shared.controllerFuture = null
        shared.pendingQueuePlay = null
        shared.lastContentType = null
        shared.snapshotFlow().value = PlaybackSnapshot()
    }
}

internal fun Int.toContentType(): ContentType? =
    when (this) {
        MediaMetadata.MEDIA_TYPE_MUSIC -> ContentType.MUSIC
        MediaMetadata.MEDIA_TYPE_AUDIO_BOOK -> ContentType.AUDIOBOOK
        else -> null
    }
