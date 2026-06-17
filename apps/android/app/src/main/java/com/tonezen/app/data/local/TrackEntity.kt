package com.tonezen.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val sortOrder: Int,
    val title: String,
    val filename: String,
    val artist: String?,
    val durationMs: Long?,
    val localPath: String?,
    val localDownloadedAt: Long? = null,
    val waveformPeaksJson: String? = null,
)
