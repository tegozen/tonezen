package com.tonezen.app.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val TonezenScreenHorizontalPadding = 20.dp

internal fun tonezenScreenContentPadding(
    top: Dp = 16.dp,
    bottom: Dp = 24.dp,
): PaddingValues = PaddingValues(
    start = TonezenScreenHorizontalPadding,
    top = top,
    end = TonezenScreenHorizontalPadding,
    bottom = bottom,
)

internal fun tonezenScrollContentPadding(bottom: Dp = 24.dp): PaddingValues = PaddingValues(
    start = TonezenScreenHorizontalPadding,
    end = TonezenScreenHorizontalPadding,
    bottom = bottom,
)

internal val TonezenFixedHeaderVerticalPadding = 16.dp
