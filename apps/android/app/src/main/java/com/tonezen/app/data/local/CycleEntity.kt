package com.tonezen.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cycles")
data class CycleEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val title: String,
    val bookOrderJson: String,
)
