package com.tonezen.app.domain.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track

data class MusicLibraryTrack(
    val book: Book,
    val track: Track,
)

object MusicLibraryResolver {
    private const val MUSIC_LIBRARY_SLUG = "music-library"

    fun resolve(
        allBooks: List<Book>,
        tracksForBook: (String) -> List<Track>,
    ): List<MusicLibraryTrack> {
        val musicBooks = allBooks.filter { it.contentType == ContentType.MUSIC }
        val libraryBooks = musicBooks.filter { it.slug == MUSIC_LIBRARY_SLUG }
        val sourceBooks = libraryBooks.ifEmpty { musicBooks }
        return sourceBooks
            .flatMap { book ->
                tracksForBook(book.id).map { track -> MusicLibraryTrack(book, track) }
            }
            .sortedWith(
                compareBy(
                    { it.track.sortOrder },
                    { it.track.filename.lowercase() },
                    { it.track.title.lowercase() },
                    { it.track.id },
                ),
            )
            .distinctBy { it.track.id }
    }
}
