package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenChromeBarBackground
import com.tonezen.app.ui.theme.TonezenChromeBarBorder
import com.tonezen.app.ui.theme.TonezenChromeBarOuterVerticalMargin
import com.tonezen.app.ui.theme.TonezenChromeHorizontalMargin
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

internal val TonezenChromeCornerRadius = 16.dp
internal val TonezenChromeBarShape = RoundedCornerShape(TonezenChromeCornerRadius)

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
internal fun Modifier.tonezenGlassChrome(hazeState: HazeState, shape: Shape = TonezenChromeBarShape): Modifier =
    tonezenGlassSurface(hazeState, shape)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun TonezenChromeBarShell(
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
