package com.tonezen.app.ui.player

internal fun bookDetailTrackListIndex(
    trackIndex: Int,
    hasContinueButton: Boolean,
): Int {
    var offset = 0
    if (hasContinueButton) offset++
    return offset + trackIndex
}

internal fun isLazyItemVisibleAboveBottomPadding(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
    bottomPaddingPx: Int,
): Boolean {
    val visibleBottom = viewportEnd - bottomPaddingPx
    return itemOffset >= viewportStart && itemOffset + itemSize <= visibleBottom
}

internal fun lazyItemScrollOffsetAboveBottomPadding(
    viewportHeight: Int,
    itemSize: Int,
    bottomPaddingPx: Int,
): Int {
    val visibleHeight = (viewportHeight - bottomPaddingPx).coerceAtLeast(0)
    return if (itemSize <= visibleHeight) {
        visibleHeight - itemSize
    } else {
        0
    }
}
