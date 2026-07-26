package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.CatalogSyncRepository
import com.tonezen.app.data.remote.ProfileSyncRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class LibraryCatalogLoader(
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    private val catalogSyncRepository: CatalogSyncRepository,
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val profileSyncRepository: ProfileSyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val cycleHandler: LibraryCycleHandler,
) {
    fun refreshSessionState(sessionData: StoredSession?) {
        uiState.update {
            it.copy(sessionState = sessionRepository.resolveState(sessionData))
        }
    }

    fun refreshDownloads() {
        scope.launch {
            val books = uiState.value.books
            val downloaded = withContext(Dispatchers.IO) {
                catalogRepository.downloadedBookIds(books)
            }
            uiState.update { it.copy(downloadedBookIds = downloaded) }
            cycleHandler.refreshCycleCardStates(uiState.value.cycles, downloaded)
        }
    }

    suspend fun loadLibrary(sessionData: StoredSession?) {
        if (sessionData == null) {
            catalogSyncRepository.stop()
            withContext(Dispatchers.Main) {
                refreshSessionState(null)
                uiState.update {
                    it.copy(
                        isLoadingCatalog = false,
                        isBootstrapComplete = true,
                        hasShownInitialLocalCatalog = false,
                        books = emptyList(),
                        cycles = emptyList(),
                        downloadedBookIds = emptySet(),
                    )
                }
            }
            return
        }
        val refreshed = withContext(Dispatchers.IO) {
            if (networkMonitor.isOnline()) {
                sessionRepository.refreshIfNeeded(sessionData)
            } else {
                sessionData
            }
        }
        refreshSessionState(refreshed)
        if (networkMonitor.isOnline()) {
            refreshed?.accessToken?.let { token ->
                withContext(Dispatchers.IO) {
                    progressSyncRepository.pullAll(token)
                }
            }
        }
        refreshed?.let {
            progressSyncRepository.start(it)
            profileSyncRepository.start(it)
            catalogSyncRepository.start(it)
        } ?: catalogSyncRepository.stop()

        if (networkMonitor.isOnline()) {
            uiState.update { it.copy(isLoadingCatalog = true) }
            coroutineScope {
                val remote = async(Dispatchers.IO) {
                    loadCatalogFromRemoteWithLocalFallback(catalogRepository, refreshed?.accessToken)
                }
                val localCatalog = async(Dispatchers.IO) {
                    loadLocalCatalogProgressively(catalogRepository)
                }
                val (localBooks, localCycles) = localCatalog.await()
                updateCatalog(
                    books = localBooks,
                    cycles = localCycles,
                    markInitialLocalCatalogShown = localBooks.isNotEmpty() || localCycles.isNotEmpty(),
                )
                uiState.update { it.copy(isBootstrapComplete = true) }
                if (uiState.value.books.isNotEmpty() || uiState.value.cycles.isNotEmpty()) {
                    uiState.update { it.copy(isLoadingCatalog = false) }
                }
                try {
                    val (books, cycles) = remote.await()
                    updateCatalog(
                        books = books,
                        cycles = cycles,
                    )
                } finally {
                    uiState.update { it.copy(isLoadingCatalog = false) }
                }
            }
        } else {
            val (local, localCycles) = withContext(Dispatchers.IO) {
                loadLocalCatalogProgressively(catalogRepository)
            }
            updateCatalog(
                books = local,
                cycles = localCycles,
                markInitialLocalCatalogShown = local.isNotEmpty() || localCycles.isNotEmpty(),
            )
            uiState.update { it.copy(isLoadingCatalog = false, isBootstrapComplete = true) }
            scope.launch(Dispatchers.IO) {
                catalogRepository.reconcileLocalDownloadPaths()
                withContext(Dispatchers.Main) {
                    refreshDownloads()
                }
            }
        }
    }

    suspend fun reloadCatalogFromLocal() {
        val (books, cycles) = withContext(Dispatchers.IO) {
            loadLocalCatalogProgressively(catalogRepository)
        }
        updateCatalog(books, cycles)
    }

    private suspend fun updateCatalog(
        books: List<Book>,
        cycles: List<Cycle>,
        markInitialLocalCatalogShown: Boolean = false,
    ) {
        val shouldPreserveCurrentCatalog =
            uiState.value.hasShownInitialLocalCatalog &&
                uiState.value.books.isNotEmpty() &&
                uiState.value.cycles.isNotEmpty() &&
                books.isEmpty() &&
                cycles.isEmpty()
        if (shouldPreserveCurrentCatalog) return

        updateBooks(books, markInitialLocalCatalogShown)
        uiState.update {
            it.copy(
                cycles = cycles,
                hasShownInitialLocalCatalog = it.hasShownInitialLocalCatalog || markInitialLocalCatalogShown,
            )
        }
        cycleHandler.refreshCycleCardStates(cycles, uiState.value.downloadedBookIds)
    }

    private suspend fun updateBooks(
        books: List<Book>,
        markInitialLocalCatalogShown: Boolean = false,
    ) {
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(books)
        }
        uiState.update {
            it.copy(
                books = books,
                downloadedBookIds = downloaded,
                hasShownInitialLocalCatalog = it.hasShownInitialLocalCatalog || markInitialLocalCatalogShown,
            )
        }
    }
}
