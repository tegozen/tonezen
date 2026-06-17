package com.tonezen.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val TonezenScreenHorizontalPadding = 20.dp
internal val TonezenChromeHorizontalMargin = 8.dp
internal val TonezenChromeBarOuterVerticalMargin = 4.dp
internal val TonezenChromeBarVerticalPadding = 8.dp
internal val TonezenBottomNavContentHeight = 58.dp

internal fun tonezenBottomChromeContentHeight(
    showMiniPlayer: Boolean,
    showBottomNav: Boolean,
): Dp {
    if (!showMiniPlayer && !showBottomNav) return 0.dp
    val divider = if (showMiniPlayer) 1.dp else 0.dp
    val topPad = if (showMiniPlayer) TonezenChromeBarVerticalPadding else 0.dp
    val miniBody = if (showMiniPlayer) TonezenMiniPlayerBodyHeight else 0.dp
    val nav = if (showBottomNav) TonezenBottomNavContentHeight else 0.dp
    return divider + topPad + miniBody + nav + TonezenChromeBarVerticalPadding
}
internal val TonezenOverlayScrollGap = 16.dp
internal val TonezenTopChromeScrollPaddingBooks = 144.dp
internal val TonezenTopChromeOfflineBannerExtra = 44.dp
internal val TonezenChromeHeaderRowHeight = 40.dp
internal val TonezenPageChromeHeight =
    TonezenChromeBarOuterVerticalMargin * 2 +
        TonezenChromeBarVerticalPadding * 2 +
        TonezenChromeHeaderRowHeight +
        1.dp
internal val TonezenPageChromeScrollPadding = TonezenPageChromeHeight + TonezenOverlayScrollGap
internal val TonezenProfileBottomExtraScrollPadding = 20.dp
internal val TonezenMiniPlayerBodyHeight = 75.dp
internal val TonezenBottomChromeMiniPlayerHeight =
    1.dp + TonezenChromeBarVerticalPadding + TonezenMiniPlayerBodyHeight + TonezenChromeBarVerticalPadding

internal val TonezenSheetTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

internal fun tonezenScreenContentPadding(
    top: Dp = 16.dp,
    bottom: Dp = 24.dp,
): PaddingValues = PaddingValues(
    start = TonezenScreenHorizontalPadding,
    top = top,
    end = TonezenScreenHorizontalPadding,
    bottom = bottom,
)

internal fun tonezenScrollContentPadding(
    top: Dp = 0.dp,
    bottom: Dp = 24.dp,
): PaddingValues = PaddingValues(
    start = TonezenScreenHorizontalPadding,
    top = top,
    end = TonezenScreenHorizontalPadding,
    bottom = bottom,
)

internal val TonezenFixedHeaderVerticalPadding = 16.dp
