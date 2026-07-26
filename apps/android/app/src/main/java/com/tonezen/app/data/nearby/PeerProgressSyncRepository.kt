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

    suspend fun listSendableCycles(): List<PeerCycleChoice> {
        val allProgress = progressRepository.getAllProgress().filter { it.positionMs > 0L }
        if (allProgress.isEmpty()) return emptyList()
        val progressByBook = allProgress.associateBy { it.bookId }
        val cycles = catalogRepository.getAllCycles()
        val choices = mutableListOf<PeerCycleChoice>()
        val coveredBooks = mutableSetOf<String>()
        for (cycle in cycles) {
            val items = orderedCycleBooks(cycle).mapNotNull { book ->
                if (book.contentType != ContentType.AUDIOBOOK) return@mapNotNull null
                val progress = progressByBook[book.id] ?: return@mapNotNull null
                if (progress.positionMs <= 0L) return@mapNotNull null
                coveredBooks += book.id
                PeerProgressItem(
                    bookId = book.id,
                    trackId = progress.trackId,
                    positionMs = progress.positionMs,
                    updatedAtEpochMs = progress.updatedAtEpochMs,
                )
            }
            if (items.isNotEmpty()) {
                choices += PeerCycleChoice(cycle.id, cycle.title, items)
            }
        }
        for (progress in allProgress) {
            if (progress.bookId in coveredBooks) continue
            val book = catalogRepository.getBook(progress.bookId) ?: continue
            if (book.contentType != ContentType.AUDIOBOOK) continue
            choices += PeerCycleChoice(
                cycleId = "book:${book.id}",
                cycleTitle = book.title,
                progress = listOf(
                    PeerProgressItem(
                        bookId = progress.bookId,
                        trackId = progress.trackId,
                        positionMs = progress.positionMs,
                        updatedAtEpochMs = progress.updatedAtEpochMs,
                    ),
                ),
            )
        }
        return choices.sortedBy { it.cycleTitle.lowercase() }
    }

    suspend fun sendCycle(endpointId: String, choice: PeerCycleChoice): Result<Boolean> {
        val session = sessionRepository.loadSession()
            ?: return Result.failure(IllegalStateException("no_session"))
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

    private suspend fun applyPeerItems(items: List<PeerProgressItem>, accessToken: String?) {
        val now = System.currentTimeMillis()
        for (item in items) {
            progressSyncRepository.saveLocal(
                AudiobookProgress(
                    bookId = item.bookId,
                    trackId = item.trackId,
                    positionMs = item.positionMs,
                    updatedAtEpochMs = now,
                ),
                pendingSync = true,
                accessToken = accessToken,
            )
        }
    }
}
