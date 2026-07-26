package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Track

/**
 * Merge peer cycle offer into local play heads without touching server snapshots.
 *
 * Total order: book index in cycle → track sortOrder → positionMs.
 * Empty tracks + no local head → [PeerBookMergeAction.TAKE_PEER].
 * Empty tracks + existing local head → [PeerBookMergeAction.CONFLICT].
 * Uncomparable track ids → [PeerBookMergeAction.CONFLICT].
 */
object PeerProgressMerger {
    fun mergeCycle(
        offer: PeerProgressOffer,
        localByBookId: Map<String, AudiobookProgress?>,
        bookIndexById: Map<String, Int>,
        tracksByBookId: Map<String, List<Track>>,
    ): PeerCycleMergeResult {
        val takePeer = mutableListOf<PeerProgressItem>()
        val conflicts = mutableListOf<PeerProgressItem>()
        var skipped = 0
        for (peer in offer.progress) {
            when (
                resolveBook(
                    peer = peer,
                    local = localByBookId[peer.bookId],
                    bookIndex = bookIndexById[peer.bookId],
                    tracks = tracksByBookId[peer.bookId],
                )
            ) {
                PeerBookMergeAction.TAKE_PEER -> takePeer += peer
                PeerBookMergeAction.KEEP_LOCAL -> Unit
                PeerBookMergeAction.CONFLICT -> conflicts += peer
                PeerBookMergeAction.SKIP -> skipped += 1
            }
        }
        return PeerCycleMergeResult(
            takePeer = takePeer,
            conflicts = conflicts,
            skipped = skipped,
        )
    }

    fun resolveBook(
        peer: PeerProgressItem,
        local: AudiobookProgress?,
        bookIndex: Int?,
        tracks: List<Track>?,
    ): PeerBookMergeAction {
        val localEmpty = local == null || local.positionMs <= 0L
        if (tracks.isNullOrEmpty()) {
            return if (localEmpty) PeerBookMergeAction.TAKE_PEER else PeerBookMergeAction.CONFLICT
        }
        val index = bookIndex ?: 0
        if (localEmpty) return PeerBookMergeAction.TAKE_PEER
        val peerKey = listenKey(index, tracks, peer.trackId, peer.positionMs)
            ?: return PeerBookMergeAction.CONFLICT
        val localKey = listenKey(index, tracks, local!!.trackId, local.positionMs)
            ?: return PeerBookMergeAction.CONFLICT
        return when {
            peerKey > localKey -> PeerBookMergeAction.TAKE_PEER
            else -> PeerBookMergeAction.KEEP_LOCAL
        }
    }

    private fun listenKey(
        bookIndex: Int,
        tracks: List<Track>,
        trackId: String,
        positionMs: Long,
    ): Long? {
        val sorted = tracks.sortedBy { it.sortOrder }
        val trackIndex = sorted.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) return null
        val pos = positionMs.coerceIn(0L, 999_999_999L)
        return bookIndex * 1_000_000_000_000L + trackIndex * 1_000_000_000L + pos
    }
}
