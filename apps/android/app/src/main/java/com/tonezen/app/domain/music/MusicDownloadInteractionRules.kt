package com.tonezen.app.domain.music

data class MusicDownloadInteractionState(
    val isTrackDownloading: Boolean,
    val isBulkDownloading: Boolean,
    val activeTrackId: String?,
)

object MusicDownloadInteractionRules {
    fun blocksUndownloadedTap(state: MusicDownloadInteractionState): Boolean =
        state.isTrackDownloading || state.isBulkDownloading

    fun blocksTrackEndedDuringSingleTrackDownload(state: MusicDownloadInteractionState): Boolean =
        state.isTrackDownloading && !state.isBulkDownloading

    fun shouldFinishTrackDownloadUi(state: MusicDownloadInteractionState, trackId: String): Boolean =
        !state.isBulkDownloading && state.activeTrackId == trackId

    fun blocksDeletingTrack(state: MusicDownloadInteractionState, trackId: String): Boolean =
        state.isTrackDownloading && state.activeTrackId == trackId

    fun blocksNowPlayingPlaybackUi(state: MusicDownloadInteractionState, playingTrackId: String?): Boolean =
        playingTrackId != null && state.isTrackDownloading && state.activeTrackId == playingTrackId

    fun blocksPlaybackAdvanceDuringBulk(state: MusicDownloadInteractionState): Boolean =
        state.isBulkDownloading
}
