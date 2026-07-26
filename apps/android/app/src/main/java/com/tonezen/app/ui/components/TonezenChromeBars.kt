package com.tonezen.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenChromeBarBorder
import com.tonezen.app.ui.theme.TonezenChromeBarVerticalPadding
import com.tonezen.app.ui.theme.TonezenChromeHeaderRowHeight
import com.tonezen.app.ui.theme.TonezenChromeHorizontalMargin
import com.tonezen.app.ui.theme.TonezenInk
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

@Composable
internal fun BackNavButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .wrapContentSize(Alignment.CenterStart)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronLeftGlyph()
        Text("Назад", color = TonezenInk)
    }
}

@Composable
internal fun TonezenBackHeaderRow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var backWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackNavButton(
            onClick = onBack,
            modifier = Modifier.onSizeChanged { backWidth = it.width },
        )
        if (title != null) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                title()
            }
            Box(
                modifier = if (trailing != null) {
                    Modifier.wrapContentWidth(Alignment.End)
                } else if (backWidth > 0) {
                    Modifier.width(with(density) { backWidth.toDp() })
                } else {
                    Modifier.wrapContentWidth(Alignment.End)
                },
                contentAlignment = Alignment.CenterEnd,
            ) {
                trailing?.invoke()
            }
        } else {
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}
