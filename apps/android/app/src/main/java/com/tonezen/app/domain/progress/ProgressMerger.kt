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

    fun hasConflict(
        playHead: AudiobookProgress?,
        snapshot: ProgressServerSnapshot?,
    ): Boolean {
        if (playHead == null || snapshot == null) return false
        if (playHead.trackId != snapshot.trackId) return true
        return kotlin.math.abs(playHead.positionMs - snapshot.positionMs) >= PROGRESS_CONFLICT_THRESHOLD_MS
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
