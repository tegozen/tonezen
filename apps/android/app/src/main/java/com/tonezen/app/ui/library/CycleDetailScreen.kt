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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.BookContinueState
import com.tonezen.app.domain.progress.canContinueBookListening
import com.tonezen.app.ui.components.ContinueResumeMeta
import com.tonezen.app.ui.components.ContinueResumeVariant
import com.tonezen.app.ui.components.DetailHeaderOverflowMenu
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
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
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
    onBack: () -> Unit,
    onBookClick: (Book) -> Unit,
    onBookResume: (Book) -> Unit,
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
                text = "Книги цикла",
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
                isDownloaded = false,
                showRemoveDownload = cycleCardState.showRemoveDownload,
                isListened = cycleCardState.isListened,
                onDownload = onDownloadCycle,
                onToggleListened = onToggleCycleListened,
                onRemoveDownloads = onRemoveCycleDownloads,
            )
        },
    ) {
        items(cycle.books) { book ->
            val continueState = canContinueBookListening(
                bookId = book.id,
                tracks = tracksByBookId[book.id].orEmpty(),
                progress = progressByBookId[book.id],
            )
            CycleBookRow(
                book = book,
                downloaded = downloadedBookIds.contains(book.id),
                continueState = continueState,
                onClick = { onBookClick(book) },
                onResumeClick = { onBookResume(book) },
            )
        }
    }
}

@Composable
private fun CycleBookRow(
    book: Book,
    downloaded: Boolean,
    continueState: BookContinueState?,
    onClick: () -> Unit,
    onResumeClick: () -> Unit,
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
                text = bookAuthorLabel(book).ifBlank { "Автор не указан" },
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (continueState != null || downloaded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    continueState?.let { state ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ContinueResumeMeta(
                                state = state,
                                variant = ContinueResumeVariant.Inline,
                            )
                            TextButton(onClick = onResumeClick) {
                                Text("Продолжить", color = TonezenTeal)
                            }
                        }
                    }
                    if (downloaded) {
                        StatusChip(label = "Офлайн", tone = TonezenTeal)
                    }
                }
            }
        }
    }
}
