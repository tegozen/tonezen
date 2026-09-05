package com.tonezen.app.ui.bookwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.remote.BookWatchRepository
import com.tonezen.app.domain.model.BookWatch
import com.tonezen.app.domain.model.BookWatchEvent
import com.tonezen.app.domain.model.BookWatchQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BookWatchViewModel @Inject constructor(private val repository: BookWatchRepository) : ViewModel() {
    val events: StateFlow<List<BookWatchEvent>> = repository.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val watches: StateFlow<List<BookWatch>> = repository.watches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun checkOnLaunch() = viewModelScope.launch { repository.checkOnLaunch() }
    fun settingsFor(cycleId: String, cycleTitle: String): BookWatch =
        watches.value.firstOrNull { it.cycleId == cycleId } ?: BookWatch(
            id = "", cycleId = cycleId, displayTitle = cycleTitle,
            enabled = true, lastSuccessAt = null,
            queries = listOf(
                BookWatchQuery("baza_knig", cycleTitle, true),
                BookWatchQuery("allbookerka", cycleTitle, true),
            ),
        )
    fun markAllRead() = viewModelScope.launch { repository.markRead(events.value.filter { it.readAt == null }.map { it.id }) }
    suspend fun update(watch: BookWatch, title: String, queries: List<BookWatchQuery>) {
        repository.updateWatch(watch, title, queries)
    }
}
