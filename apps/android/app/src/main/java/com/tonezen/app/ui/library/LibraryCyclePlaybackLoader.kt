package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.CycleResumeTarget
import com.tonezen.app.domain.progress.resolveBookContinuePlayHead
import com.tonezen.app.domain.progress.resolveCycleResumeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CyclePlaybackSource(
    val bookIds: List<String>,
    val tracksByBookId: Map<String, List<Track>>,
    val progressByBookId: Map<String, AudiobookProgress?>,
    val resume: CycleResumeTarget?,
)

internal class LibraryCyclePlaybackLoader(
    private val catalogRepository: CatalogRepository,
) {
    suspend fun load(cycle: Cycle): CyclePlaybackSource {
        val bookIds = cycle.books.map { it.id }
        val (tracks, progress) = withContext(Dispatchers.IO) {
            catalogRepository.getTracksByBookIds(bookIds) to
                catalogRepository.getProgressByBookIds(bookIds)
        }
        return CyclePlaybackSource(
            bookIds = bookIds,
            tracksByBookId = tracks,
            progressByBookId = progress,
            resume = resolveCycleResumeTarget(cycle, tracks, progress),
        )
    }

    suspend fun refreshResume(cycle: Cycle, source: CyclePlaybackSource): CycleResumeTarget? {
        val progress = withContext(Dispatchers.IO) {
            catalogRepository.getProgressByBookIds(source.bookIds)
        }
        val resume = resolveCycleResumeTarget(cycle, source.tracksByBookId, progress) ?: source.resume
            ?: return null
        val head = resolveBookContinuePlayHead(
            source.tracksByBookId[resume.book.id].orEmpty(),
            progress[resume.book.id],
        ) ?: return resume
        return CycleResumeTarget(resume.book, head.track, head.positionMs)
    }
}
