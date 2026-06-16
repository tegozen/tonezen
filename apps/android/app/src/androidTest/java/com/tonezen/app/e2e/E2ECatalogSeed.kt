package com.tonezen.app.e2e

import com.tonezen.app.data.local.BookEntity
import com.tonezen.app.data.local.CatalogDao
import com.tonezen.app.data.local.DownloadQueueDao
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.data.local.TrackEntity
import android.content.Context
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track

object E2ECatalogSeed {
    const val BOOK_ID = "e2e-music-book"
    const val TRACK_ID = "e2e-music-track"
    const val TRACK_TITLE = "E2E Test Song"
    const val ARTIST = "E2E Artist"

    fun testMusicBook(): Book = Book(
        id = BOOK_ID,
        slug = "e2e-album",
        contentType = ContentType.MUSIC,
        title = "E2E Album",
        author = ARTIST,
    )

    fun testMusicTrack(): Track = Track(
        id = TRACK_ID,
        bookId = BOOK_ID,
        sortOrder = 0,
        title = TRACK_TITLE,
        filename = "e2e.mp3",
        artist = ARTIST,
        durationMs = 180_000,
        localPath = null,
    )

    suspend fun seedMusicTrack(catalogDao: CatalogDao) {
        catalogDao.upsertBooks(
            listOf(
                BookEntity(
                    id = BOOK_ID,
                    slug = "e2e-album",
                    contentType = "music",
                    title = "E2E Album",
                    author = ARTIST,
                    coverPath = null,
                    updatedAt = "",
                ),
            ),
        )
        catalogDao.upsertTracks(
            listOf(
                TrackEntity(
                    id = TRACK_ID,
                    bookId = BOOK_ID,
                    sortOrder = 0,
                    title = TRACK_TITLE,
                    filename = "e2e.mp3",
                    artist = ARTIST,
                    durationMs = 180_000,
                    localPath = null,
                    localDownloadedAt = null,
                ),
            ),
        )
    }

    suspend fun clearDownloadState(
        context: Context,
        catalogDao: CatalogDao,
        downloadQueueDao: DownloadQueueDao,
    ) {
        downloadQueueDao.deleteAll()
        SafeLocalStorage.trackFile(context.filesDir, BOOK_ID, TRACK_ID)?.delete()
        SafeLocalStorage.trackPartFile(context.filesDir, BOOK_ID, TRACK_ID)?.delete()
        catalogDao.upsertTracks(
            listOf(
                TrackEntity(
                    id = TRACK_ID,
                    bookId = BOOK_ID,
                    sortOrder = 0,
                    title = TRACK_TITLE,
                    filename = "e2e.mp3",
                    artist = ARTIST,
                    durationMs = 180_000,
                    localPath = null,
                    localDownloadedAt = null,
                ),
            ),
        )
    }
}
