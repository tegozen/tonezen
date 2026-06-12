package com.tonezen.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

object PlaybackMediaFactory {
    fun toMediaItem(item: QueuePlayItem): MediaItem {
        val meta = item.metadata
        val subtitle = buildString {
            append(meta.albumTitle)
            if (meta.totalTracks > 1) {
                append(" · ")
                append(meta.trackNumber)
                append("/")
                append(meta.totalTracks)
            }
        }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(meta.trackTitle)
            .setArtist(meta.artist)
            .setAlbumTitle(meta.albumTitle)
            .setDisplayTitle(meta.trackTitle)
            .setSubtitle(subtitle)
            .setTrackNumber(meta.trackNumber)
            .setTotalTrackCount(meta.totalTracks)
            .setMediaType(
                when (meta.contentType) {
                    com.tonezen.app.domain.model.ContentType.AUDIOBOOK -> MediaMetadata.MEDIA_TYPE_AUDIO_BOOK
                    com.tonezen.app.domain.model.ContentType.MUSIC -> MediaMetadata.MEDIA_TYPE_MUSIC
                },
            )
        meta.artworkUri?.let { metadataBuilder.setArtworkUri(it) }
        return MediaItem.Builder()
            .setMediaId(item.trackId)
            .setUri(mediaUriFor(item.mediaUri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun mediaUriFor(pathOrUrl: String): Uri {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return Uri.parse(pathOrUrl)
        }
        require(!pathOrUrl.contains("..")) { "Invalid local media path" }
        return Uri.fromFile(File(pathOrUrl))
    }
}
