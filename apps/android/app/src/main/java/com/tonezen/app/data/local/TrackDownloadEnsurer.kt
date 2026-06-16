package com.tonezen.app.data.local

import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.downloads.EnsureTrackDownloadPolicy
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EnsureTrackOutcome(
    val track: Track?,
    val failure: Failure? = null,
) {
    enum class Failure {
        OFFLINE,
        NO_SESSION,
        DOWNLOAD_FAILED,
    }

    companion object {
        fun success(track: Track) = EnsureTrackOutcome(track = track)
        fun failed(failure: Failure) = EnsureTrackOutcome(track = null, failure = failure)
    }
}

@Singleton
class TrackDownloadEnsurer @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
) {
    private val downloadLocks = mutableMapOf<String, Mutex>()

    private fun lockFor(bookId: String, trackId: String): Mutex =
        synchronized(downloadLocks) {
            downloadLocks.getOrPut("$bookId:$trackId") { Mutex() }
        }

    suspend fun ensureTrackLocal(
        bookId: String,
        track: Track,
        onProgress: ((Float) -> Unit)? = null,
    ): EnsureTrackOutcome = lockFor(bookId, track.id).withLock {
        ensureTrackLocalUnlocked(bookId, track, onProgress)
    }

    private suspend fun ensureTrackLocalUnlocked(
        bookId: String,
        track: Track,
        onProgress: ((Float) -> Unit)?,
    ): EnsureTrackOutcome {
        catalogRepository.resolveLocalTrackPath(bookId, track.id)?.let { path ->
            return EnsureTrackOutcome.success(track.copy(localPath = path))
        }
        if (!networkMonitor.isOnline()) return EnsureTrackOutcome.failed(EnsureTrackOutcome.Failure.OFFLINE)
        val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
            ?: return EnsureTrackOutcome.failed(EnsureTrackOutcome.Failure.NO_SESSION)
        return try {
            val file = downloadRepository.downloadTrack(
                accessToken = session.accessToken,
                bookId = bookId,
                trackId = track.id,
                onProgress = onProgress ?: {},
            )
            if (!catalogRepository.markTrackDownloaded(bookId, track.id, file.absolutePath)) {
                catalogRepository.resolveLocalTrackPath(bookId, track.id)
            }
            EnsureTrackOutcome.success(track.copy(localPath = file.absolutePath))
        } catch (_: Exception) {
            val path = EnsureTrackDownloadPolicy.resolveLocalPathAfterFailure(
                catalogRepository.resolveLocalTrackPath(bookId, track.id),
            ) ?: return EnsureTrackOutcome.failed(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED)
            EnsureTrackOutcome.success(track.copy(localPath = path))
        }
    }

    suspend fun ensureTracksLocal(book: Book, tracks: List<Track>): List<Track> = buildList {
        for (track in tracks.sortedBy { it.sortOrder }) {
            val fresh = catalogRepository.getTracksForBook(book.id).find { it.id == track.id } ?: track
            ensureTrackLocal(book.id, fresh).track?.let { add(it) }
        }
    }

    suspend fun resolveLocalTrack(bookId: String, track: Track): Track? {
        val path = catalogRepository.resolveLocalTrackPath(bookId, track.id) ?: return null
        return track.copy(localPath = path)
    }

    suspend fun isTrackLocal(bookId: String, trackId: String): Boolean =
        catalogRepository.resolveLocalTrackPath(bookId, trackId) != null
}
