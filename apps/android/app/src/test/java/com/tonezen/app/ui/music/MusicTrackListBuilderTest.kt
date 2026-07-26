package com.tonezen.app.ui.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTrackListBuilderTest {
    private val book = Book(
        id = "b1",
        slug = "music-library",
        title = "Library",
        author = "Miyagi",
        contentType = ContentType.MUSIC,
    )

    private val trackA = Track(
        id = "t1",
        bookId = "b1",
        sortOrder = 0,
        title = "One",
        filename = "1.mp3",
        durationMs = 1000,
        localPath = null,
    )

    private val trackB = Track(
        id = "t2",
        bookId = "b1",
        sortOrder = 1,
        title = "Two",
        filename = "2.mp3",
        durationMs = 2000,
        localPath = null,
    )

    private val trackC = Track(
        id = "t3",
        bookId = "b1",
        sortOrder = 2,
        title = "Three",
        filename = "3.mp3",
        durationMs = 3000,
        localPath = null,
    )

    private val trackD = Track(
        id = "t4",
        bookId = "b1",
        sortOrder = 3,
        title = "Four",
        filename = "4.mp3",
        durationMs = 4000,
        localPath = null,
    )

    @Test
    fun keepsOrderWhenCatalogGainsTracks() {
        val stale = listOf(toMusicListTrack(book, trackB, emptySet()), toMusicListTrack(book, trackA, emptySet()))
        val updated = buildMusicTrackListForCatalogUpdate(
            existing = stale,
            candidates = listOf(book to trackA, book to trackB),
            musicStartedInSession = false,
            downloadedTrackIds = emptySet(),
        )
        assertEquals(listOf("t2", "t1"), updated.map { it.trackId })
    }

    @Test
    fun rebuildsListWhenCatalogGainsTracks() {
        val stale = listOf(toMusicListTrack(book, trackA, emptySet()))
        val updated = buildMusicTrackListForCatalogUpdate(
            existing = stale,
            candidates = listOf(book to trackA, book to trackB),
            musicStartedInSession = false,
            downloadedTrackIds = emptySet(),
        )
        assertEquals(2, updated.size)
        assertEquals(setOf("t1", "t2"), updated.map { it.trackId }.toSet())
    }

    @Test
    fun appendsBackendTracksAsSeparatelyShuffledSuffix() {
        val localShuffle = listOf(toMusicListTrack(book, trackB, emptySet()), toMusicListTrack(book, trackA, emptySet()))
        val updated = buildMusicTrackListForCatalogUpdate(
            existing = localShuffle,
            candidates = listOf(book to trackA, book to trackB, book to trackC, book to trackD),
            musicStartedInSession = false,
            downloadedTrackIds = emptySet(),
            shuffleNewTracks = { it.asReversed() },
        )

        assertEquals(listOf("t2", "t1", "t4", "t3"), updated.map { it.trackId })
    }

    @Test
    fun keepsDownloadFlagsWhenCatalogUnchanged() {
        val existing = listOf(
            toMusicListTrack(book, trackA, emptySet()),
            toMusicListTrack(book, trackB, emptySet()),
        )
        val refreshed = buildMusicTrackListForCatalogUpdate(
            existing = existing,
            candidates = listOf(book to trackA, book to trackB),
            musicStartedInSession = false,
            downloadedTrackIds = setOf("t2"),
        )
        assertEquals(listOf(false, true), refreshed.map { it.isDownloaded })
    }

    @Test
    fun clearsDownloadFlagWhenTrackRemovedFromDownloadedSet() {
        val existing = listOf(
            toMusicListTrack(book, trackA, setOf("t1")),
            toMusicListTrack(book, trackB, setOf("t2")),
        )
        val refreshed = refreshMusicTrackListDownloadState(existing, emptySet())
        assertEquals(listOf(false, false), refreshed.map { it.isDownloaded })
    }

    @Test
    fun rebuildsListWhenTrackMetadataChanges() {
        val stale = listOf(
            MusicListTrack(
                trackId = "t1",
                trackTitle = "Miyagi__Ugly_Name",
                artist = "Music",
                albumTitle = "Library",
                bookId = "b1",
                durationMs = 1000,
                isDownloaded = false,
            ),
        )
        val updated = buildMusicTrackListForCatalogUpdate(
            existing = stale,
            candidates = listOf(book to trackA),
            musicStartedInSession = false,
            downloadedTrackIds = emptySet(),
        )
        assertEquals("One", updated.single().trackTitle)
        assertEquals("Miyagi", updated.single().artist)
    }
}
