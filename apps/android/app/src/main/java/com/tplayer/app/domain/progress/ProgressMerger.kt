package com.tplayer.app.domain.progress

import com.tplayer.app.data.local.AudiobookProgressEntity

object ProgressMerger {
    fun merge(
        local: AudiobookProgressEntity?,
        remote: AudiobookProgressEntity,
    ): AudiobookProgressEntity? {
        if (local == null) return remote
        return if (local.updatedAtEpochMs >= remote.updatedAtEpochMs) local else remote
    }
}
