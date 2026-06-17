package com.tonezen.app.domain.session

import com.tonezen.app.domain.model.StoredSession

fun mergeProfileOnRefresh(previous: StoredSession, refreshed: StoredSession): StoredSession =
    refreshed.copy(
        avatarUrl = refreshed.avatarUrl ?: previous.avatarUrl,
        memberSinceEpochMs = refreshed.memberSinceEpochMs ?: previous.memberSinceEpochMs,
    )
