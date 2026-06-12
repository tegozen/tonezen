package com.tonezen.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audiobook_progress")
data class AudiobookProgressEntity(
    @PrimaryKey val bookId: String,
    val trackId: String,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
    val pendingSync: Boolean = false,
)
