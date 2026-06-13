package com.tonezen.app.domain.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDownloadInteractionRulesTest {
    private fun state(
        trackDownloading: Boolean = false,
        bulkDownloading: Boolean = false,
        activeTrackId: String? = null,
    ) = MusicDownloadInteractionState(
        isTrackDownloading = trackDownloading,
        isBulkDownloading = bulkDownloading,
        activeTrackId = activeTrackId,
    )

    @Test
    fun blocksUndownloadedTapDuringSingleOrBulkDownload() {
        assertTrue(MusicDownloadInteractionRules.blocksUndownloadedTap(state(trackDownloading = true, activeTrackId = "t1")))
        assertTrue(MusicDownloadInteractionRules.blocksUndownloadedTap(state(bulkDownloading = true)))
        assertFalse(MusicDownloadInteractionRules.blocksUndownloadedTap(state()))
    }

    @Test
    fun allowsTrackEndedDuringBulkButNotSingleDownload() {
        assertTrue(
            MusicDownloadInteractionRules.blocksTrackEndedDuringSingleTrackDownload(
                state(trackDownloading = true, activeTrackId = "t1"),
            ),
        )
        assertFalse(
            MusicDownloadInteractionRules.blocksTrackEndedDuringSingleTrackDownload(
                state(trackDownloading = true, bulkDownloading = true, activeTrackId = "t2"),
            ),
        )
    }

    @Test
    fun finishesTrackUiOnlyForMatchingTrackAndNotDuringBulk() {
        assertTrue(
            MusicDownloadInteractionRules.shouldFinishTrackDownloadUi(
                state(trackDownloading = true, activeTrackId = "t1"),
                "t1",
            ),
        )
        assertFalse(
            MusicDownloadInteractionRules.shouldFinishTrackDownloadUi(
                state(trackDownloading = true, bulkDownloading = true, activeTrackId = "t2"),
                "t2",
            ),
        )
        assertFalse(
            MusicDownloadInteractionRules.shouldFinishTrackDownloadUi(
                state(trackDownloading = true, activeTrackId = "t1"),
                "t2",
            ),
        )
    }

    @Test
    fun blocksDeletingOnlyActiveDownloadingTrack() {
        assertTrue(MusicDownloadInteractionRules.blocksDeletingTrack(state(trackDownloading = true, activeTrackId = "t1"), "t1"))
        assertFalse(MusicDownloadInteractionRules.blocksDeletingTrack(state(trackDownloading = true, activeTrackId = "t1"), "t2"))
        assertFalse(MusicDownloadInteractionRules.blocksDeletingTrack(state(bulkDownloading = true, activeTrackId = "t2"), "t1"))
    }
}
