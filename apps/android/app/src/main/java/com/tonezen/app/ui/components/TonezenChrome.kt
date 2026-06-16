package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenChromeBarBackground
import com.tonezen.app.ui.theme.TonezenChromeBarBorder
import com.tonezen.app.ui.theme.TonezenChromeHeaderRowHeight
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenChromeBarOuterVerticalMargin
import com.tonezen.app.ui.theme.TonezenChromeBarVerticalPadding
import com.tonezen.app.ui.theme.TonezenChromeHorizontalMargin
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenHorizontalPadding
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.tonezenScrollContentPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

private val TonezenChromeCornerRadius = 16.dp
private val TonezenChromeBarShape = RoundedCornerShape(TonezenChromeCornerRadius)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun Modifier.tonezenGlassSurface(
    hazeState: HazeState,
    shape: Shape = TonezenChromeBarShape,
): Modifier =
    clip(shape)
        .hazeChild(
            state = hazeState,
            style = HazeMaterials.thin(
                containerColor = TonezenChromeBarBackground.copy(alpha = 0.62f),
            ),
        ) {
            blurRadius = 24.dp
            noiseFactor = 0.08f
        }
        .border(BorderStroke(1.dp, TonezenChromeBarBorder), shape)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun Modifier.tonezenGlassChrome(hazeState: HazeState, shape: Shape = TonezenChromeBarShape): Modifier =
    tonezenGlassSurface(hazeState, shape)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun TonezenChromeBarShell(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = TonezenChromeHorizontalMargin,
                    vertical = TonezenChromeBarOuterVerticalMargin,
                )
                .tonezenGlassChrome(hazeState),
            content = content,
        )
    }
}

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
        Text(stringResource(R.string.back), color = TonezenInk)
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
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TonezenSurface)
            .padding(padding),
    ) {
        LazyColumn(
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

@Composable
internal fun IconCircle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun TonezenTabs(selectedTab: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TonezenTab(
            label = stringResource(R.string.tab_audiobooks),
            selected = selectedTab == 0,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(0) },
        )
        TonezenTab(
            label = stringResource(R.string.tab_music),
            selected = selectedTab == 1,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(1) },
        )
        TonezenTab(
            label = stringResource(R.string.tab_downloads),
            selected = selectedTab == 2,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(2) },
        )
    }
}

@Composable
private fun TonezenTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) TonezenTeal else TonezenMuted,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) TonezenTeal else TonezenBorder.copy(alpha = 0.35f)),
        )
    }
}

@Composable
internal fun SearchRow(
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onFilterClick: () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TonezenSurfaceRaised.copy(alpha = 0.92f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchGlyph()
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = TonezenInk),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_library),
                            color = TonezenMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    inner()
                },
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onFilterClick)
                .background(TonezenSurfaceRaised.copy(alpha = 0.92f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            FilterGlyph()
        }
    }
}
