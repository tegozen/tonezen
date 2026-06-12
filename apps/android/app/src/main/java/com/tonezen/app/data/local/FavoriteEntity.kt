package com.tonezen.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val bookId: String,
    val pendingSync: Boolean = false,
)
