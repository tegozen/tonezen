package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress

object ProgressMerger {
    fun merge(
        local: AudiobookProgress?,
        remote: AudiobookProgress?,
    ): AudiobookProgress? {
        if (local == null) return remote
        if (remote == null) return local
        return if (local.updatedAtEpochMs >= remote.updatedAtEpochMs) local else remote
    }
}
