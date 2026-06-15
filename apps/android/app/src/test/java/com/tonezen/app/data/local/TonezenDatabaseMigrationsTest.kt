package com.tonezen.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class TonezenDatabaseMigrationsTest {
    @Test
    fun migrationsCoverEveryReleasedSchemaVersion() {
        val migrations = TonezenDatabaseMigrations.ALL.map { it.startVersion to it.endVersion }

        assertEquals(listOf(1 to 2, 2 to 3, 3 to 4), migrations)
    }

    @Test
    fun migrationOneToTwoCreatesCyclesWithoutTouchingExistingTables() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        TonezenDatabaseMigrations.MIGRATION_1_2.migrate(db)

        verify {
            db.execSQL(
                match {
                    it.contains("CREATE TABLE IF NOT EXISTS `cycles`") &&
                        it.contains("`bookOrderJson` TEXT NOT NULL")
                },
            )
        }
    }

    @Test
    fun migrationTwoToThreeDropsOnlyFavoritesTable() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        TonezenDatabaseMigrations.MIGRATION_2_3.migrate(db)

        verify { db.execSQL("DROP TABLE IF EXISTS `favorites`") }
    }

    @Test
    fun migrationThreeToFourAddsTrackArtistColumn() {
        val db = mockk<SupportSQLiteDatabase>(relaxed = true)

        TonezenDatabaseMigrations.MIGRATION_3_4.migrate(db)

        verify { db.execSQL("ALTER TABLE `tracks` ADD COLUMN `artist` TEXT") }
    }
}
