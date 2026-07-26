package com.tonezen.app.domain.progress

/** Wire/domain models for Android↔Android nearby audiobook progress exchange. */
data class PeerProgressItem(
    val bookId: String,
    val trackId: String,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
)

data class PeerProgressOffer(
    val protocol: Int = 1,
    val userId: String,
    val deviceLabel: String,
    val cycleId: String,
    val cycleTitle: String,
    val progress: List<PeerProgressItem>,
)

data class PeerCycleChoice(
    val cycleId: String,
    val cycleTitle: String,
    /** Books in this cycle with positionMs > 0. */
    val progress: List<PeerProgressItem>,
)

data class PeerDeviceInfo(
    val endpointId: String,
    val deviceLabel: String,
    val userId: String,
)

enum class PeerBookMergeAction {
    TAKE_PEER,
    KEEP_LOCAL,
    CONFLICT,
    SKIP,
}

data class PeerCycleMergeResult(
    val takePeer: List<PeerProgressItem>,
    val conflicts: List<PeerProgressItem>,
    val skipped: Int,
)
