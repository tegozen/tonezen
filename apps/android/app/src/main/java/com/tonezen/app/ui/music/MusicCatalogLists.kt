package com.tonezen.app.ui.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.music.MusicLibraryTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MusicCatalogLists(
    private val ctx: MusicHandlerContext,
) {
    fun findKnownMusicBook(bookId: String): Book? =
        ctx.session.musicCandidates.firstOrNull { it.first.id == bookId }?.first

    suspend fun reloadMusicCatalogData() {
        val entries = withContext(Dispatchers.IO) {
            ctx.catalogRepository.resolveMusicLibraryTracks()
        }
        ctx.session.musicCandidates = entries.map { it.book to it.track }
        ctx.session.musicBookIdByTrackId = entries.associate { it.track.id to it.book.id }
        ctx.downloadQueueController.reconcileDownloadQueueBookIds()
    }

    suspend fun resolveDownloadedTrackIdsForUi(
        reconcileLocalPaths: Boolean = false,
    ): Set<String> = withContext(Dispatchers.IO) {
        val ids = if (reconcileLocalPaths) {
            ctx.catalogRepository.reconcileLocalDownloadPaths()
            ctx.catalogRepository.getDownloadedTrackIds()
        } else {
            ctx.catalogRepository.getDownloadedTrackIdsFromCatalog()
        }.toMutableSet()
        ids.addAll(localPlaybackDownloadedTrackIds())
        ids
    }

    private suspend fun localPlaybackDownloadedTrackIds(): Set<String> {
        val playback = ctx.uiState.value.musicPlayback
        val trackId = playback.trackId ?: return emptySet()
        if (!playback.isActive) return emptySet()
        val bookId = playback.bookId
            ?: ctx.session.musicBookIdByTrackId[trackId]
            ?: ctx.catalogRepository.findBookForTrack(trackId)?.id
            ?: return emptySet()
        return if (ctx.trackDownloadEnsurer.isTrackLocal(bookId, trackId)) setOf(trackId) else emptySet()
    }

    suspend fun buildMusicTrackListForCatalogUpdate(
        rebuildMusic: Boolean = false,
        reconcileLocalPaths: Boolean = false,
    ): List<MusicListTrack> {
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi(reconcileLocalPaths)
        return buildMusicTrackListForCatalogUpdate(
            existing = ctx.uiState.value.musicTrackList,
            candidates = ctx.session.musicCandidates,
            musicStartedInSession = ctx.session.musicStartedInSession,
            downloadedTrackIds = downloadedTrackIds,
        )
    }

    suspend fun refreshMusicTrackListForDownloads(): List<MusicListTrack> {
        refreshMusicLibraryTracksLocalPaths()
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return refreshMusicTrackListDownloadState(ctx.uiState.value.musicTrackList, downloadedTrackIds)
    }

    suspend fun refreshMusicLibraryTracksLocalPaths() {
        if (ctx.session.musicLibraryTracks.isEmpty()) return
        ctx.session.musicLibraryTracks = withContext(Dispatchers.IO) {
            ctx.session.musicLibraryTracks.map { entry ->
                val path = ctx.catalogRepository.resolveLocalTrackPath(entry.book.id, entry.track.id)
                if (path != null && path != entry.track.localPath) {
                    entry.copy(track = entry.track.copy(localPath = path))
                } else {
                    entry
                }
            }
        }
    }

    suspend fun refreshMusicTrackListWithDownloadedIds(downloadedTrackIds: Set<String>): List<MusicListTrack> =
        refreshMusicTrackListDownloadState(ctx.uiState.value.musicTrackList, downloadedTrackIds)

    suspend fun buildMusicTrackList(shuffle: Boolean): List<MusicListTrack> {
        if (ctx.session.musicCandidates.isEmpty()) return emptyList()
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return buildMusicTrackListFromCandidates(ctx.session.musicCandidates, shuffle, downloadedTrackIds)
    }

    suspend fun refreshMusicTrackListDownloadState(
        list: List<MusicListTrack>,
    ): List<MusicListTrack> {
        val downloadedTrackIds = resolveDownloadedTrackIdsForUi()
        return refreshMusicTrackListDownloadState(list, downloadedTrackIds)
    }

    suspend fun resolvePlaybackTrack(playback: MusicPlaybackUi): MusicListTrack? {
        val trackId = playback.trackId ?: return null
        val bookId = playback.bookId
            ?: ctx.catalogRepository.findBookForTrack(trackId)?.id
            ?: return null
        val domainTrack = ctx.catalogRepository.getTracksForBook(bookId).find { it.id == trackId }
            ?: return null
        val book = findKnownMusicBook(bookId)
            ?: ctx.catalogRepository.findBookForTrack(trackId)
            ?: return null
        return MusicListTrack(
            trackId = trackId,
            trackTitle = playback.trackTitle ?: domainTrack.title,
            artist = playback.artist ?: book.author ?: book.title,
            albumTitle = playback.albumTitle ?: book.title,
            bookId = bookId,
            durationMs = domainTrack.durationMs,
            isDownloaded = ctx.trackDownloadEnsurer.isTrackLocal(bookId, trackId),
        )
    }

    fun visibleMusicTrackList(): List<MusicListTrack> =
        visibleMusicTrackList(ctx.uiState.value.musicTrackList, ctx.uiState.value.isNetworkOnline)

    suspend fun buildMusicLibraryTracksFromList(): List<MusicLibraryTrack> {
        val visible = visibleMusicTrackList()
        if (visible.isEmpty()) {
            return withContext(Dispatchers.IO) {
                val all = ctx.catalogRepository.resolveMusicLibraryTracks()
                if (ctx.uiState.value.isNetworkOnline) {
                    all
                } else {
                    all.filter { entry ->
                        ctx.trackDownloadEnsurer.isTrackLocal(entry.book.id, entry.track.id)
                    }
                }
            }
        }
        return withContext(Dispatchers.IO) {
            val byTrackId = if (ctx.session.musicCandidates.isNotEmpty()) {
                ctx.session.musicCandidates.associate { (book, track) ->
                    track.id to MusicLibraryTrack(book, track)
                }
            } else {
                ctx.catalogRepository.resolveMusicLibraryTracks().associateBy { it.track.id }
            }
            visible.mapNotNull { item -> byTrackId[item.trackId] }
        }
    }

    fun musicListTrackFromEntry(entry: MusicLibraryTrack): MusicListTrack {
        val downloaded = entry.track.localPath?.isNotBlank() == true
        return MusicListTrack(
            trackId = entry.track.id,
            trackTitle = entry.track.title,
            artist = entry.book.author ?: entry.book.title,
            albumTitle = entry.book.title,
            bookId = entry.book.id,
            durationMs = entry.track.durationMs,
            isDownloaded = downloaded,
        )
    }
}
