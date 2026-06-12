package com.tonezen.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.ui.components.BookCover
import com.tonezen.app.ui.components.DetailHeaderOverflowMenu
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.bookAuthorLabel
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState

@Composable
internal fun CycleDetailScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    cycle: Cycle,
    cycleCardState: CycleCardState,
    downloadedBookIds: Set<String>,
    onBack: () -> Unit,
    onBookClick: (Book) -> Unit,
    onDownloadCycle: () -> Unit,
    onToggleCycleListened: () -> Unit,
    onRemoveCycleDownloads: () -> Unit,
    bottomScrollPadding: Dp,
) {
    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        bottomScrollPadding = bottomScrollPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        title = {
            Text(
                text = stringResource(R.string.cycle_books_section),
                color = TonezenInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailing = {
            DetailHeaderOverflowMenu(
                showDownload = cycleCardState.showDownload,
                showRemoveDownload = cycleCardState.showRemoveDownload,
                isListened = cycleCardState.isListened,
                onDownload = onDownloadCycle,
                onToggleListened = onToggleCycleListened,
                onRemoveDownloads = onRemoveCycleDownloads,
            )
        },
    ) {
        items(cycle.books) { book ->
            CycleBookRow(
                book = book,
                downloaded = downloadedBookIds.contains(book.id),
                onClick = { onBookClick(book) },
            )
        }
    }
}

@Composable
private fun CycleBookRow(
    book: Book,
    downloaded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            book = book,
            modifier = Modifier.width(72.dp).aspectRatio(0.78f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = book.title,
                color = TonezenInk,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bookAuthorLabel(book),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (downloaded) {
                StatusChip(label = stringResource(R.string.offline), tone = TonezenTeal)
            }
        }
    }
}
