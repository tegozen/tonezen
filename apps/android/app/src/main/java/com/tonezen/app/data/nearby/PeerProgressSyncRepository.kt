package com.tonezen.app.data.nearby

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.ProgressRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.progress.PeerBookMergeAction
import com.tonezen.app.domain.progress.PeerCycleChoice
import com.tonezen.app.domain.progress.PeerCycleMergeResult
import com.tonezen.app.domain.progress.PeerDeviceInfo
import com.tonezen.app.domain.progress.PeerProgressItem
import com.tonezen.app.domain.progress.PeerProgressMerger
import com.tonezen.app.domain.progress.PeerProgressOffer
import com.tonezen.app.domain.progress.orderedCycleBooks
import com.tonezen.app.playback.PlaybackClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class PeerProgressSyncRepository @Inject constructor(
    private val transport: NearbyPeerTransport,
    private val deviceLabelResolver: DeviceLabelResolver,
    private val sessionRepository: SessionRepository,
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val playbackClient: PlaybackClient,
) {
    val devices: StateFlow<List<PeerDeviceInfo>> = transport.devices
    val incomingOffers: SharedFlow<NearbyPeerTransport.IncomingOffer> = transport.incomingOffers

    fun deviceLabel(): String = deviceLabelResolver.resolve()

    suspend fun startAccepting(): Result<Unit> {
        val session = sessionRepository.loadSession()
            ?: return Result.failure(IllegalStateException("no_session"))
        return transport.startAccepting(session.userId, deviceLabel())
    }

    suspend fun startDiscovering(): Result<Unit> {
        val session = sessionRepository.loadSession()
            ?: return Result.failure(IllegalStateException("no_session"))
        return transport.startDiscovering(session.userId, deviceLabel())
    }

    suspend fun stop() = transport.stop()

    fun stopSync() = transport.stopSync()

    /** Persist live audiobook play head so peer send includes the book currently playing. */
    suspend fun flushLiveAudiobookProgress() {
        val snap = playbackClient.snapshot.value
        if (snap.contentType != ContentType.AUDIOBOOK) return
        val trackId = snap.trackId ?: return
        val book = catalogRepository.findBookForTrack(trackId) ?: return
        val positionMs = snap.positionMs.coerceAtLeast(1L)
        val session = sessionRepository.loadSession()
        progressSyncRepository.saveLocal(
            AudiobookProgress(
                bookId = book.id,
                trackId = trackId,
                positionMs = positionMs,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
            pendingSync = true,
            accessToken = session?.accessToken,
        )
    }

    suspend fun listSendableCycles(): List<PeerCycleChoice> {
        flushLiveAudiobookProgress()
        val allProgress = progressRepository.getAllProgress().filter { it.positionMs > 0L }
        if (allProgress.isEmpty()) return emptyList()
        val progressByBook = allProgress.associateBy { it.bookId }
        val cycles = catalogRepository.getAllCycles()
        val choices = mutableListOf<PeerCycleChoice>()
        val coveredBooks = mutableSetOf<String>()
        for (cycle in cycles) {
            val items = progressItemsForCycle(cycle.id, cycle.title, progressByBook)
            if (items != null) {
                items.progress.forEach { coveredBooks += it.bookId }
                choices += items
            }
        }
        for (progress in allProgress) {
            if (progress.bookId in coveredBooks) continue
            val book = catalogRepository.getBook(progress.bookId) ?: continue
            if (book.contentType != ContentType.AUDIOBOOK) continue
            choices += PeerCycleChoice(
                cycleId = "book:${book.id}",
                cycleTitle = book.title,
                progress = listOf(progress.toPeerItem()),
            )
        }
        return choices.sortedBy { it.cycleTitle.lowercase() }
    }

    /**
     * Rebuild progress for [cycleId] from DB at send time (after live flush),
     * not the stale snapshot from the cycle picker.
     */
    suspend fun sendCycle(endpointId: String, cycleId: String, cycleTitle: String): Result<Boolean> {
        flushLiveAudiobookProgress()
        val session = sessionRepository.loadSession()
            ?: return Result.failure(IllegalStateException("no_session"))
        val progressByBook = progressRepository.getAllProgress()
            .filter { it.positionMs > 0L }
            .associateBy { it.bookId }
        val choice = progressItemsForCycle(cycleId, cycleTitle, progressByBook)
            ?: return Result.failure(IllegalStateException("empty_cycle"))
        val offer = PeerProgressOffer(
            userId = session.userId,
            deviceLabel = deviceLabel(),
            cycleId = choice.cycleId,
            cycleTitle = choice.cycleTitle,
            progress = choice.progress,
        )
        return transport.sendOffer(endpointId, offer)
    }

    fun replyToOffer(endpointId: String, accepted: Boolean) {
        transport.sendAck(endpointId, accepted)
    }

    suspend fun applyOffer(offer: PeerProgressOffer): PeerCycleMergeResult {
        val session = sessionRepository.loadSession()
        if (session == null || session.userId != offer.userId) {
            return PeerCycleMergeResult(emptyList(), emptyList(), offer.progress.size)
        }
        val bookIds = offer.progress.map { it.bookId }.distinct()
        val localByBook = progressRepository.getProgressForBooks(bookIds)
        val cycles = catalogRepository.getAllCycles()
        val bookIndexById = mutableMapOf<String, Int>()
        for (cycle in cycles) {
            orderedCycleBooks(cycle).forEachIndexed { index, book ->
                bookIndexById.putIfAbsent(book.id, index)
            }
        }
        val tracksByBook = catalogRepository.getTracksByBookIds(bookIds)
        val takePeer = mutableListOf<PeerProgressItem>()
        val conflicts = mutableListOf<PeerProgressItem>()
        var skipped = 0
        for (peer in offer.progress) {
            val book = catalogRepository.getBook(peer.bookId)
            if (book == null) {
                skipped += 1
                continue
            }
            when (
                PeerProgressMerger.resolveBook(
                    peer = peer,
                    local = localByBook[peer.bookId],
                    bookIndex = bookIndexById[peer.bookId] ?: 0,
                    tracks = tracksByBook[peer.bookId],
                )
            ) {
                PeerBookMergeAction.TAKE_PEER -> takePeer += peer
                PeerBookMergeAction.KEEP_LOCAL -> Unit
                PeerBookMergeAction.CONFLICT -> conflicts += peer
                PeerBookMergeAction.SKIP -> skipped += 1
            }
        }
        val result = PeerCycleMergeResult(takePeer, conflicts, skipped)
        applyPeerItems(result.takePeer, session.accessToken)
        return result
    }

    suspend fun applyConflictChoice(conflicts: List<PeerProgressItem>, takePeer: Boolean) {
        if (!takePeer) return
        val token = sessionRepository.loadSession()?.accessToken
        applyPeerItems(conflicts, token)
    }

    private suspend fun progressItemsForCycle(
        cycleId: String,
        fallbackTitle: String,
        progressByBook: Map<String, AudiobookProgress>,
    ): PeerCycleChoice? {
        if (cycleId.startsWith("book:")) {
            val bookId = cycleId.removePrefix("book:")
            val progress = progressByBook[bookId] ?: return null
            val book = catalogRepository.getBook(bookId) ?: return null
            return PeerCycleChoice(cycleId, book.title.ifBlank { fallbackTitle }, listOf(progress.toPeerItem()))
        }
        val cycle = catalogRepository.getAllCycles().find { it.id == cycleId } ?: return null
        // Include every cycle member (order list + books), not only bookOrder hits.
        val books = (orderedCycleBooks(cycle) + cycle.books).distinctBy { it.id }
        val items = books.mapNotNull { book ->
            if (book.contentType != ContentType.AUDIOBOOK) return@mapNotNull null
            val progress = progressByBook[book.id] ?: return@mapNotNull null
            if (progress.positionMs <= 0L) return@mapNotNull null
            progress.toPeerItem()
        }
        if (items.isEmpty()) return null
        return PeerCycleChoice(cycle.id, cycle.title, items)
    }

    private fun AudiobookProgress.toPeerItem() = PeerProgressItem(
        bookId = bookId,
        trackId = trackId,
        positionMs = positionMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private suspend fun applyPeerItems(items: List<PeerProgressItem>, accessToken: String?) {
        // Keep peer relative freshness so cycle Continue prefers the newest book.
        for (item in items) {
            progressSyncRepository.saveLocal(
                AudiobookProgress(
                    bookId = item.bookId,
                    trackId = item.trackId,
                    positionMs = item.positionMs,
                    updatedAtEpochMs = item.updatedAtEpochMs.coerceAtLeast(1L),
                ),
                pendingSync = true,
                accessToken = accessToken,
            )
        }
    }
}
