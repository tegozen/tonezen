package com.tonezen.app.ui.bookwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.BookWatchEntity
import com.tonezen.app.data.local.BookWatchEventEntity
import com.tonezen.app.data.remote.BookWatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BookWatchViewModel @Inject constructor(private val repository: BookWatchRepository) : ViewModel() {
    val events: StateFlow<List<BookWatchEventEntity>> = repository.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val watches: StateFlow<List<BookWatchEntity>> = repository.watches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun checkOnLaunch() = viewModelScope.launch { repository.checkOnLaunch() }
    fun markAllRead() = viewModelScope.launch { repository.markRead(events.value.filter { it.readAt == null }.map { it.id }) }
    fun update(watch: BookWatchEntity, title: String, queriesJson: String) = viewModelScope.launch { repository.updateWatch(watch, title, queriesJson) }
}
