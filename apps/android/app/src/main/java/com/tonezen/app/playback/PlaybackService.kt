package com.tonezen.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.tonezen.app.MainActivity
import com.tonezen.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var playbackEvents: PlaybackEvents

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Воспроизведение",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(NOTIFICATION_ID)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .build(),
        )

        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
            .also { exoPlayer ->
                exoPlayer.setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                exoPlayer.setHandleAudioBecomingNoisy(true)
            }

        playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playbackEvents.notifyTrackEnded()
                }
                updateConnectedControllerCommands()
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                updateConnectedControllerCommands()
            }
        }.also { player?.addListener(it) }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionActivity)
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult {
                        val playerCommands = buildPlayerCommands(controller)
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailablePlayerCommands(playerCommands)
                            .build()
                    }

                    override fun onPlayerCommandRequest(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        playerCommand: Int,
                    ): Int {
                        if (!canHandleQueueSkipCommand(controller, playerCommand)) {
                            return SessionResult.RESULT_ERROR_NOT_SUPPORTED
                        }
                        return SessionResult.RESULT_SUCCESS
                    }
                },
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tonezen_playback"
        const val SEEK_BACK_MS = 15_000L
        const val SEEK_FORWARD_MS = 30_000L
    }

    private fun updateConnectedControllerCommands() {
        val session = mediaSession ?: return
        session.connectedControllers.forEach { controller ->
            session.setAvailableCommands(
                controller,
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                buildPlayerCommands(controller),
            )
        }
    }

    private fun buildPlayerCommands(controller: MediaSession.ControllerInfo): Player.Commands {
        val exoPlayer = player ?: return MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        val commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
        if (canHandleQueueSkipCommand(controller, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
            exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        ) {
            commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (canHandleQueueSkipCommand(controller, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
            exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        ) {
            commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        return commands.build()
    }

    private fun canHandleQueueSkipCommand(
        controller: MediaSession.ControllerInfo,
        playerCommand: Int,
    ): Boolean {
        if (
            playerCommand != Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM &&
            playerCommand != Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ) {
            return true
        }
        return isOwnController(controller) || currentMediaType() == MediaMetadata.MEDIA_TYPE_MUSIC
    }

    private fun isOwnController(controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == packageName && controller.uid == Process.myUid()

    private fun currentMediaType(): Int? =
        player?.currentMediaItem?.mediaMetadata?.mediaType
            ?: player?.mediaMetadata?.mediaType
}
