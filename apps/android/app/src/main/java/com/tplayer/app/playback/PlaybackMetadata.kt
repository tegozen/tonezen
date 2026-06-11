package com.tplayer.app.playback

import android.net.Uri
import com.tplayer.app.domain.model.ContentType

data class PlaybackMetadata(
    val trackTitle: String,
    val artist: String,
    val albumTitle: String,
    val trackNumber: Int,
    val totalTracks: Int,
    val contentType: ContentType,
    val artworkUri: Uri? = null,
)

data class QueuePlayItem(
    val trackId: String,
    val localPath: String,
    val metadata: PlaybackMetadata,
)
