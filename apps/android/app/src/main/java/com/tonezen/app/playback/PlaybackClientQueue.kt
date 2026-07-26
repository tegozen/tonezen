package com.tonezen.app.playback

import androidx.media3.session.MediaController
import com.tonezen.app.domain.music.MusicQueueWindow
import kotlinx.coroutines.flow.update

internal class PlaybackClientQueue(
    private val shared: PlaybackClientShared,
    private val refreshSnapshot: (MediaController) -> Unit,
    private val connect: () -> Unit,
) {
    fun playQueue(items: List<QueuePlayItem>, startIndex: Int, startPositionMs: Long = 0L) {
        if (items.isEmpty()) return
        connect()
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        items.getOrNull(safeIndex)?.let { item ->
            shared.lastContentType = item.metadata.contentType
            shared.snapshotFlow().update {
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
        val mediaController = shared.controller
        if (mediaController == null) {
            shared.pendingQueuePlay = PendingQueuePlay(items, safeIndex, startPositionMs)
            return
        }
        executePlayQueue(mediaController, items, safeIndex, startPositionMs)
    }

    fun executePlayQueue(
        mediaController: MediaController,
        items: List<QueuePlayItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        val startItem = items.getOrNull(startIndex)
        startItem?.metadata?.contentType?.let { shared.lastContentType = it }
        val mediaItems = items.map(shared.playbackMediaFactory::toMediaItem)
        mediaController.setMediaItems(mediaItems, startIndex, startPositionMs)
        mediaController.prepare()
        mediaController.play()
        items.getOrNull(startIndex)?.trackId?.let { shared.emitActiveTrackId(it) }
        refreshSnapshot(mediaController)
    }

    fun queuedTrackIds(): Set<String> {
        val mediaController = shared.controller ?: return emptySet()
        return (0 until mediaController.mediaItemCount)
            .mapNotNull { index ->
                mediaController.getMediaItemAt(index).mediaId?.takeIf { it.isNotEmpty() }
            }
            .toSet()
    }

    fun lastQueuedTrackId(): String? {
        val mediaController = shared.controller ?: return null
        if (mediaController.mediaItemCount <= 0) return null
        return mediaController.getMediaItemAt(mediaController.mediaItemCount - 1)
            .mediaId
            ?.takeIf { it.isNotEmpty() }
    }

    fun shouldAppendQueueItems(): Boolean {
        val mediaController = shared.controller ?: return false
        return MusicQueueWindow.shouldAppend(
            currentIndex = mediaController.currentMediaItemIndex,
            queueSize = mediaController.mediaItemCount,
        )
    }

    fun appendQueueItems(items: List<QueuePlayItem>) {
        if (items.isEmpty()) return
        connect()
        val mediaController = shared.controller ?: return
        val existingIds = queuedTrackIds()
        val newItems = items.filter { it.trackId !in existingIds }
        if (newItems.isEmpty()) return
        mediaController.addMediaItems(newItems.map(shared.playbackMediaFactory::toMediaItem))
        refreshSnapshot(mediaController)
    }
}
