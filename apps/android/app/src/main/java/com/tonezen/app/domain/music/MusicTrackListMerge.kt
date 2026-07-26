package com.tonezen.app.domain.music

/**
 * Pure merge algorithm for keeping a previously shown (possibly shuffled) music
 * track list stable across catalog refreshes, only reshuffling tracks that are
 * newly discovered.
 */
object MusicTrackListMerge {
    fun <T> merge(
        existing: List<T>,
        built: List<T>,
        musicStartedInSession: Boolean,
        idOf: (T) -> String,
        metadataEquals: (T, T) -> Boolean,
        shuffleInitial: (List<T>) -> List<T>,
        shuffleAppended: (List<T>) -> List<T>,
        refreshExisting: (List<T>) -> List<T>,
    ): List<T> {
        if (built.isEmpty()) return emptyList()
        if (existing.isEmpty()) {
            return if (musicStartedInSession) built else shuffleInitial(built)
        }

        val existingIds = existing.map(idOf).toSet()
        val builtIds = built.map(idOf).toSet()
        val freshById = built.associateBy(idOf)
        val catalogChanged = built.size != existing.size ||
            built.any { idOf(it) !in existingIds } ||
            existing.any { idOf(it) !in builtIds } ||
            existing.any { item ->
                val fresh = freshById[idOf(item)] ?: return@any false
                !metadataEquals(item, fresh)
            }

        if (!catalogChanged) return refreshExisting(existing)

        val kept = existing.mapNotNull { freshById[idOf(it)] }
        val keptIds = kept.map(idOf).toSet()
        val appended = built.filter { idOf(it) !in keptIds }
        return kept + shuffleAppended(appended)
    }
}
