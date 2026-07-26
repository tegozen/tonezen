package com.tonezen.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenChromeBarBorder
import com.tonezen.app.ui.theme.TonezenChromeBarVerticalPadding
import com.tonezen.app.ui.theme.TonezenChromeHeaderRowHeight
import com.tonezen.app.ui.theme.TonezenChromeHorizontalMargin
import com.tonezen.app.ui.theme.TonezenScreenHorizontalPadding
import dev.chrisbanes.haze.HazeState

@Composable
internal fun TonezenTopChromeBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    TonezenChromeBarShell(hazeState = hazeState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TonezenScreenHorizontalPadding,
                    end = TonezenScreenHorizontalPadding,
                    top = TonezenChromeBarVerticalPadding,
                    bottom = TonezenChromeBarVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = TonezenChromeBarBorder,
        )
    }
}

@Composable
internal fun TonezenTitleChromeBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TonezenChromeBarShell(hazeState = hazeState, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TonezenScreenHorizontalPadding,
                    end = TonezenScreenHorizontalPadding,
                    top = TonezenChromeBarVerticalPadding,
                    bottom = TonezenChromeBarVerticalPadding,
                )
                .heightIn(min = TonezenChromeHeaderRowHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = TonezenChromeBarBorder,
        )
    }
}

@Composable
internal fun TonezenBackChromeBar(
    hazeState: HazeState,
    onBack: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    TonezenChromeBarShell(hazeState = hazeState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TonezenScreenHorizontalPadding,
                    end = TonezenScreenHorizontalPadding,
                    top = TonezenChromeBarVerticalPadding,
                    bottom = TonezenChromeBarVerticalPadding,
                ),
        ) {
            TonezenBackHeaderRow(
                modifier = Modifier.heightIn(min = TonezenChromeHeaderRowHeight),
                onBack = onBack,
                title = title,
                trailing = trailing,
            )
        }
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = TonezenChromeBarBorder,
        )
    }
}

@Composable
internal fun TonezenBottomChromeBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    showMiniPlayerSlot: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TonezenChromeHorizontalMargin)
                .tonezenGlassChrome(hazeState),
        ) {
            if (showMiniPlayerSlot) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = TonezenChromeBarBorder,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (showMiniPlayerSlot) TonezenChromeBarVerticalPadding else 0.dp,
                        bottom = TonezenChromeBarVerticalPadding,
                    ),
                content = content,
            )
        }
    }
}
