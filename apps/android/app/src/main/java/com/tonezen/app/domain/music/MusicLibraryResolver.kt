package com.tonezen.app.domain.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track

data class MusicLibraryTrack(
    val book: Book,
    val track: Track,
)

object MusicLibraryResolver {
    fun resolve(
        allBooks: List<Book>,
        tracksForBook: (String) -> List<Track>,
    ): List<MusicLibraryTrack> =
        allBooks
            .asSequence()
            .filter { it.contentType == ContentType.MUSIC }
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
            .toList()
}
