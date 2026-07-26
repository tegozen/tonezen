package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress

const val PROGRESS_CONFLICT_THRESHOLD_MS = 30_000L

data class ProgressServerSnapshot(
    val trackId: String,
    val positionMs: Long,
    val revision: Long,
)

object ProgressMerger {
    fun mergeByRevision(
        local: AudiobookProgress?,
        remote: AudiobookProgress?,
    ): AudiobookProgress? {
        if (local == null) return remote
        if (remote == null) return local
        return if (local.revision >= remote.revision) local else remote
    }

    fun getServerSnapshot(progress: AudiobookProgress?): ProgressServerSnapshot? {
        val trackId = progress?.serverTrackId ?: return null
        val positionMs = progress.serverPositionMs ?: return null
        val revision = progress.serverRevision ?: return null
        return ProgressServerSnapshot(trackId, positionMs, revision)
    }

    /**
     * Client CAS/branch base. `revision == 0` with a known [serverRevision] is a stuck
     * uninitialized base (Kotlin `0L ?: serverRevision` never falls through) — treat as
     * branched from the known server revision so chapter-ahead listening can auto-flush.
     */
    fun alignedClientRevision(playHeadRevision: Long, serverRevision: Long?): Long {
        if (playHeadRevision > 0L) return playHeadRevision
        if (serverRevision != null && serverRevision > 0L) return serverRevision
        return playHeadRevision
    }

    fun alignedClientRevision(progress: AudiobookProgress): Long =
        alignedClientRevision(progress.revision, progress.serverRevision)

    /**
     * True only for a real multi-device fork — not for “local listened ahead of a stale snapshot”.
     *
     * - Same track, local position ≥ server → pending push, not a conflict.
     * - Same track, server ahead by ≥ [PROGRESS_CONFLICT_THRESHOLD_MS] → conflict.
     * - Different tracks → conflict only if server revision moved past this client’s branch
     *   base; same base means this device advanced chapters locally.
     */
    fun hasConflict(
        playHead: AudiobookProgress?,
        snapshot: ProgressServerSnapshot?,
    ): Boolean {
        if (playHead == null || snapshot == null) return false
        if (playHead.trackId == snapshot.trackId) {
            if (playHead.positionMs >= snapshot.positionMs) return false
            return snapshot.positionMs - playHead.positionMs >= PROGRESS_CONFLICT_THRESHOLD_MS
        }
        return snapshot.revision > alignedClientRevision(playHead)
    }

    fun conflictChoiceKey(
        playHead: AudiobookProgress,
        snapshot: ProgressServerSnapshot,
    ): String =
        listOf(
            playHead.trackId,
            playHead.positionMs.toString(),
            snapshot.trackId,
            snapshot.positionMs.toString(),
            snapshot.revision.toString(),
        ).joinToString("|")

    fun shouldPrompt(progress: AudiobookProgress?): Boolean {
        val snapshot = getServerSnapshot(progress) ?: return false
        if (!hasConflict(progress, snapshot)) return false
        return progress!!.conflictChoiceKey != conflictChoiceKey(progress, snapshot)
    }

    fun canAutoFlush(progress: AudiobookProgress): Boolean {
        val snapshot = getServerSnapshot(progress) ?: return true
        if (!hasConflict(progress, snapshot)) return true
        return progress.conflictChoiceKey == conflictChoiceKey(progress, snapshot)
    }
}
