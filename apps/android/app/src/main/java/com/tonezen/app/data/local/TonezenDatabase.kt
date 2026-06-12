package com.tonezen.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        TrackEntity::class,
        AudiobookProgressEntity::class,
        FavoriteEntity::class,
        CycleEntity::class,
    ],
    version = 2,
)
abstract class TonezenDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
