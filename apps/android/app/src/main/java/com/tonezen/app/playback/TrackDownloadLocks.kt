package com.tonezen.app.playback

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One in-flight download per track id across queue worker and [TrackDownloadEnsurer]. */
@Singleton
class TrackDownloadLocks @Inject constructor() {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forTrack(trackId: String): Mutex = locks.getOrPut(trackId) { Mutex() }

    suspend inline fun <T> withTrackLock(trackId: String, block: () -> T): T =
        forTrack(trackId).withLock { block() }
}
