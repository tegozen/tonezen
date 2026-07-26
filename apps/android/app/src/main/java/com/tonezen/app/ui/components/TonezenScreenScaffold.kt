package com.tonezen.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.tonezenScrollContentPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
internal fun TonezenFixedHeaderScreen(
    hazeState: HazeState,
    padding: PaddingValues,
    onBack: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    bottomScrollPadding: Dp = 24.dp,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TonezenSurface)
            .padding(padding),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState),
            contentPadding = tonezenScrollContentPadding(
                top = TonezenPageChromeScrollPadding,
                bottom = bottomScrollPadding,
            ),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        TonezenBackChromeBar(
            modifier = Modifier.align(Alignment.TopCenter),
            hazeState = hazeState,
            onBack = onBack,
            title = title,
            trailing = trailing,
        )
    }
}
