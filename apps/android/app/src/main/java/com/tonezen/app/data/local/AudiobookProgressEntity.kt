package com.tonezen.app.data.local

import androidx.room.Entity

@Entity(
    tableName = "audiobook_progress",
    primaryKeys = ["userId", "bookId"],
)
data class AudiobookProgressEntity(
    val userId: String,
    val bookId: String,
    val trackId: String,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
    val pendingSync: Boolean = false,
    val revision: Long = 0L,
    val serverTrackId: String? = null,
    val serverPositionMs: Long? = null,
    val serverRevision: Long? = null,
    val conflictChoiceKey: String? = null,
)
