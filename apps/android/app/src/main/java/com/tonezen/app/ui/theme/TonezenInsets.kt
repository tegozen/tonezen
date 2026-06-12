package com.tonezen.app.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun tonezenBottomChromeScrollPadding(
    showMiniPlayer: Boolean,
    showBottomNav: Boolean = true,
    extraBottom: Dp = 0.dp,
): Dp {
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val chromeHeight = tonezenBottomChromeContentHeight(
        showMiniPlayer = showMiniPlayer,
        showBottomNav = showBottomNav,
    )
    return navigationBarInset + chromeHeight + TonezenOverlayScrollGap + extraBottom
}

@Composable
internal fun PaddingValues.withoutBottom(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = 0.dp,
    )
}
