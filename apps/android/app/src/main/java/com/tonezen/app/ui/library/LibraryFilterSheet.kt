package com.tonezen.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.library.LibraryContentFilter
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.library.LibrarySortOrder
import com.tonezen.app.ui.components.TonezenGlassModalBottomSheet
import com.tonezen.app.ui.components.TonezenSheetPrimaryButton
import com.tonezen.app.ui.components.TonezenSheetSecondaryButton
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterSheet(
    visible: Boolean,
    hazeState: HazeState,
    filter: LibraryFilterState,
    onDismiss: () -> Unit,
    onApply: (LibraryFilterState) -> Unit,
    onReset: () -> Unit,
    onContentFilterChange: (LibraryContentFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
) {
    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Поиск и фильтр", color = TonezenInk, fontWeight = FontWeight.SemiBold)
            FilterChipRow(
                label = "Все",
                selected = filter.contentFilter == LibraryContentFilter.ALL,
                onClick = { onContentFilterChange(LibraryContentFilter.ALL) },
            )
            FilterChipRow(
                label = "Загруженные",
                selected = filter.contentFilter == LibraryContentFilter.DOWNLOADED,
                onClick = { onContentFilterChange(LibraryContentFilter.DOWNLOADED) },
            )
            Text("Сортировка", color = TonezenMuted)
            FilterChipRow(
                label = "Недавно слушали",
                selected = filter.sortOrder == LibrarySortOrder.RECENTLY_PLAYED,
                onClick = { onSortOrderChange(LibrarySortOrder.RECENTLY_PLAYED) },
            )
            FilterChipRow(
                label = "Название",
                selected = filter.sortOrder == LibrarySortOrder.TITLE,
                onClick = { onSortOrderChange(LibrarySortOrder.TITLE) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TonezenSheetSecondaryButton(
                    label = "Сбросить",
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                )
                TonezenSheetPrimaryButton(
                    label = "Применить",
                    onClick = { onApply(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) TonezenTeal.copy(alpha = 0.15f) else TonezenSurfaceRaised.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp),
            )
            .border(
                BorderStroke(1.dp, if (selected) TonezenTeal else TonezenBorder),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (selected) TonezenTeal else TonezenInk,
    )
}
