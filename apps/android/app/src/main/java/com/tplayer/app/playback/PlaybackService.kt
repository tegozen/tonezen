package com.tplayer.app.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playbackEvents.notifyTrackEnded()
                }
            }
        }.also { player?.addListener(it) }

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult {
                        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                            .add(Player.COMMAND_SEEK_TO_NEXT)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .build()
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailablePlayerCommands(playerCommands)
                            .build()
                    }

                    override fun onPlayerCommandRequest(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        playerCommand: Int,
                    ): Int {
                        return when (playerCommand) {
                            Player.COMMAND_SEEK_TO_NEXT -> {
                                playbackEvents.requestSkipToNext()
                                Player.COMMAND_SEEK_TO_NEXT
                            }
                            Player.COMMAND_SEEK_TO_PREVIOUS -> {
                                playbackEvents.requestSkipToPrevious()
                                Player.COMMAND_SEEK_TO_PREVIOUS
                            }
                            else -> super.onPlayerCommandRequest(session, controller, playerCommand)
                        }
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
}
