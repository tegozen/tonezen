package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.testing.TestTags
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBottomNavContentHeight
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

enum class BottomDestination(val label: String) {
    Music("Музыка"),
    Books("Книги"),
    Downloads("Загрузки"),
    Profile("Профиль"),
}

@Composable
internal fun TonezenBottomNavigation(
    selected: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TonezenBottomNavContentHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(
            destination = BottomDestination.Music,
            selected = selected,
            testTag = TestTags.NAV_MUSIC,
            onClick = { onSelect(BottomDestination.Music) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            destination = BottomDestination.Books,
            selected = selected,
            testTag = TestTags.NAV_BOOKS,
            onClick = { onSelect(BottomDestination.Books) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            destination = BottomDestination.Downloads,
            selected = selected,
            testTag = TestTags.NAV_DOWNLOADS,
            onClick = { onSelect(BottomDestination.Downloads) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            destination = BottomDestination.Profile,
            selected = selected,
            testTag = TestTags.NAV_PROFILE,
            onClick = { onSelect(BottomDestination.Profile) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomNavItem(
    destination: BottomDestination,
    selected: BottomDestination,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = destination == selected
    Box(
        modifier = modifier
            .fillMaxHeight()
            .testTag(testTag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) TonezenTeal.copy(alpha = 0.95f) else Color.Transparent)
                    .border(
                        BorderStroke(1.dp, if (active) TonezenTeal else TonezenMuted.copy(alpha = 0.7f)),
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (active) TonezenAppBg else TonezenMuted
                when (destination) {
                    BottomDestination.Music -> MusicGlyph(tint = tint)
                    BottomDestination.Books -> BooksGlyph(tint = tint)
                    BottomDestination.Downloads -> DownloadsGlyph(tint = tint)
                    BottomDestination.Profile -> ProfileGlyph(tint = tint)
                }
            }
            Text(
                text = destination.label,
                color = if (active) TonezenTeal else TonezenMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
