package com.tonezen.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import com.tonezen.app.ui.player.isLazyItemVisibleAboveBottomPadding
import com.tonezen.app.ui.player.lazyItemScrollOffsetAboveBottomPadding

internal suspend fun LazyListState.animateItemAboveBottomPadding(
    index: Int,
    bottomPaddingPx: Int,
) {
    val initial = layoutInfo
    initial.visibleItemsInfo.find { it.index == index }?.let { item ->
        if (
            isLazyItemVisibleAboveBottomPadding(
                itemOffset = item.offset,
                itemSize = item.size,
                viewportStart = initial.viewportStartOffset,
                viewportEnd = initial.viewportEndOffset,
                bottomPaddingPx = bottomPaddingPx,
            )
        ) {
            return
        }
    }

    animateScrollToItem(index)

    val info = layoutInfo
    val item = info.visibleItemsInfo.find { it.index == index } ?: return
    val viewportStart = info.viewportStartOffset
    val viewportEnd = info.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart
    val visibleBottom = viewportEnd - bottomPaddingPx

    if (
        isLazyItemVisibleAboveBottomPadding(
            itemOffset = item.offset,
            itemSize = item.size,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            bottomPaddingPx = bottomPaddingPx,
        )
    ) {
        return
    }

    val scrollOffset = when {
        item.offset + item.size > visibleBottom -> {
            lazyItemScrollOffsetAboveBottomPadding(
                viewportHeight = viewportHeight,
                itemSize = item.size,
                bottomPaddingPx = bottomPaddingPx,
            )
        }
        item.offset < viewportStart -> 0
        else -> return
    }

    animateScrollToItem(index, scrollOffset.coerceAtLeast(0))
}
