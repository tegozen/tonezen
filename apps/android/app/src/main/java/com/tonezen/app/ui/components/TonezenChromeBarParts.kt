package com.tonezen.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenInk

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
