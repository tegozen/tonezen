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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
