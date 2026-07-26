package com.tonezen.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TonezenDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cycles` (
                    `id` TEXT NOT NULL,
                    `slug` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `bookOrderJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `favorites`")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tracks` ADD COLUMN `artist` TEXT")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tracks` ADD COLUMN `localDownloadedAt` INTEGER")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_queue` (
                    `bookId` TEXT NOT NULL,
                    `trackId` TEXT NOT NULL,
                    `priority` TEXT NOT NULL,
                    `batchId` TEXT,
                    `enqueuedAt` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `subtitle` TEXT,
                    `contentType` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `bytesDownloaded` INTEGER NOT NULL,
                    `totalBytes` INTEGER,
                    `tempPath` TEXT,
                    PRIMARY KEY(`bookId`, `trackId`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tracks` ADD COLUMN `waveformPeaksJson` TEXT")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `audiobook_progress`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `audiobook_progress` (
                    `userId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `trackId` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `updatedAtEpochMs` INTEGER NOT NULL,
                    `pendingSync` INTEGER NOT NULL,
                    `revision` INTEGER NOT NULL,
                    `serverTrackId` TEXT,
                    `serverPositionMs` INTEGER,
                    `serverRevision` INTEGER,
                    `conflictChoiceKey` TEXT,
                    PRIMARY KEY(`userId`, `bookId`)
                )
                """.trimIndent(),
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
    )
}
