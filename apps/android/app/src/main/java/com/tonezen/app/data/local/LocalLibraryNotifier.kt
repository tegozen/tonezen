package com.tonezen.app.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryNotifier @Inject constructor() {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    fun notifyLocalLibraryChanged() {
        _changes.tryEmit(Unit)
    }
}
