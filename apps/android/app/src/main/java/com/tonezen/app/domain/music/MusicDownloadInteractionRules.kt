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
}
