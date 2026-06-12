package com.tonezen.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val contentType: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val updatedAt: String,
)
