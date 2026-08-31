package com.tonezen.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_watch_events")
data class BookWatchEventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val watchId: String,
    val kind: String,
    val title: String,
    val author: String?,
    val bookNumber: Int?,
    val status: String,
    val readAt: Long?,
    val firstSeenAt: Long,
    val occurrenceCount: Int,
    val linksJson: String,
)

@Entity(tableName = "book_watches")
data class BookWatchEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val cycleId: String,
    val displayTitle: String,
    val enabled: Boolean,
    val lastSuccessAt: Long?,
    val queriesJson: String,
)
