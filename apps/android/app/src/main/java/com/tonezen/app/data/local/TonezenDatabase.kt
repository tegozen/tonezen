package com.tonezen.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        TrackEntity::class,
        AudiobookProgressEntity::class,
        CycleEntity::class,
        DownloadQueueEntity::class,
    ],
    version = 6,
)
abstract class TonezenDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    abstract fun downloadQueueDao(): DownloadQueueDao
}
