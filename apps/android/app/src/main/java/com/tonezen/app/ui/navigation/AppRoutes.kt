package com.tonezen.app.ui.navigation

import com.tonezen.app.ui.components.BottomDestination
import kotlinx.serialization.Serializable

@Serializable
internal data object SplashRoute

@Serializable
internal data object AuthRoute

@Serializable
internal data object AppRoute

@Serializable
internal data object MusicRoute

@Serializable
internal data object BooksRoute

@Serializable
internal data object DownloadsRoute

@Serializable
internal data object ProfileRoute

@Serializable
internal data class CycleRoute(val cycleId: String)

@Serializable
internal data class BookRoute(val bookId: String, val autoResume: Boolean = false)

@Serializable
internal data object AccountSettingsRoute

@Serializable
internal data object StorageSettingsRoute

@Serializable
internal data object BookWatchRoute

internal fun BottomDestination.route(): Any = when (this) {
    BottomDestination.Music -> MusicRoute
    BottomDestination.Books -> BooksRoute
    BottomDestination.Downloads -> DownloadsRoute
    BottomDestination.Profile -> ProfileRoute
}
