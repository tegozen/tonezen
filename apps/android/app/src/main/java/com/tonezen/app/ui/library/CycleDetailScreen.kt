package com.tonezen.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.BookContinueState
import com.tonezen.app.domain.progress.canContinueBookListening
import com.tonezen.app.domain.progress.isBookFullyListened
import com.tonezen.app.domain.progress.resolveBookListenFraction
import com.tonezen.app.ui.components.CheckCircleGlyph
import com.tonezen.app.ui.components.ContinueResumeMeta
import com.tonezen.app.ui.components.ContinueResumeVariant
import com.tonezen.app.ui.components.DetailHeaderOverflowMenu
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
    onBookWatch: () -> Unit,
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
                onBookWatch = onBookWatch,
            )
        },
    ) {
        items(cycle.books, key = { it.id }) { book ->
            val tracks = tracksByBookId[book.id].orEmpty()
            val progress = progressByBookId[book.id]
            val continueState = remember(book.id, tracks, progress) {
                canContinueBookListening(
                    bookId = book.id,
                    tracks = tracks,
                    progress = progress,
                )
            }
            val progressFraction = remember(tracks, progress) {
                val fraction = resolveBookListenFraction(tracks, progress) ?: 0f
                if (isBookFullyListened(tracks, progress)) 1f else fraction
            }
            CycleBookRow(
                book = book,
                downloaded = downloadedBookIds.contains(book.id),
                progressFraction = progressFraction,
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
    progressFraction: Float,
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
        Box(modifier = Modifier.width(72.dp).aspectRatio(0.78f)) {
            BookCover(
                book = book,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.78f),
            )
            if (downloaded) {
                CheckCircleGlyph(
                    tint = TonezenTeal,
                    size = 18.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .zIndex(1f),
                )
            }
        }
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
            Text(
                text = "${(progressFraction * 100).toInt()}%",
                color = TonezenTeal,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            continueState?.let { state ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ContinueResumeMeta(
                        state = state,
                        variant = ContinueResumeVariant.Inline,
                    )
                    TextButton(onClick = onResumeClick) {
                        Text("Продолжить", color = TonezenTeal)
                    }
                }
            }
        }
    }
}
