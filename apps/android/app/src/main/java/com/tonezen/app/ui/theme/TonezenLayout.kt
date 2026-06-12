package com.tonezen.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val TonezenScreenHorizontalPadding = 20.dp
internal val TonezenChromeHorizontalMargin = 8.dp
internal val TonezenChromeBarOuterVerticalMargin = 4.dp
internal val TonezenChromeBarVerticalPadding = 8.dp
internal val TonezenBottomChromeScrollPadding = 104.dp
internal val TonezenBottomChromeScrollPaddingWithMiniPlayer = 176.dp

internal fun tonezenBottomChromeScrollPadding(showMiniPlayer: Boolean): Dp =
    if (showMiniPlayer) TonezenBottomChromeScrollPaddingWithMiniPlayer else TonezenBottomChromeScrollPadding
internal val TonezenTopChromeScrollPaddingAudiobooks = 144.dp
internal val TonezenTopChromeScrollPaddingMusic = 72.dp
internal val TonezenTopChromeOfflineBannerExtra = 44.dp
internal val TonezenBackChromeHeight = 68.dp
internal val TonezenBackChromeScrollPadding = TonezenBackChromeHeight + 16.dp
internal val TonezenProfileChromeScrollPadding = 80.dp
internal val TonezenProfileBottomExtraScrollPadding = 20.dp
internal val TonezenMiniPlayerBodyHeight = 75.dp
internal val TonezenBottomChromeMiniPlayerHeight =
    1.dp + TonezenChromeBarVerticalPadding + TonezenMiniPlayerBodyHeight + TonezenChromeBarVerticalPadding
internal val TonezenOverlayScrollGap = 16.dp

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
