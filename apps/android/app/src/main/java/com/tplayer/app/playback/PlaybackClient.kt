package com.tplayer.app.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackSnapshot(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val trackTitle: String? = null,
)

@Singleton
class PlaybackClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var listener: Player.Listener? = null

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
                refreshSnapshot(mediaController)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun playTrack(localPath: String, metadata: PlaybackMetadata, startMs: Long = 0L) {
        connect()
        val mediaController = controller ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(localPath)))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(metadata.trackTitle)
                    .setArtist(metadata.artist)
                    .setAlbumTitle(metadata.albumTitle)
                    .build(),
            )
            .build()
        mediaController.setMediaItem(mediaItem, startMs)
        mediaController.prepare()
        mediaController.play()
        refreshSnapshot(mediaController)
    }

    fun pause() {
        controller?.pause()
    }

    fun play() {
        controller?.play()
    }

    fun isPlaying(): Boolean = controller?.isPlaying == true

    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun stopAndRelease() {
        listener?.let { controller?.removeListener(it) }
        listener = null
        controller?.release()
        controller = null
        controllerFuture = null
        _snapshot.value = PlaybackSnapshot()
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
                refreshSnapshot(mediaController)
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
            )
        }
    }
}
