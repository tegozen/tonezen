package com.tonezen.app.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.tonezen.app.ui.bookwatch.BookWatchViewModel
import com.tonezen.app.ui.navigation.AccountSettingsRoute
import com.tonezen.app.ui.navigation.BookWatchRoute
import com.tonezen.app.ui.navigation.ProfileRoute
import com.tonezen.app.ui.navigation.StorageSettingsRoute
import com.tonezen.app.ui.profile.ProfilePage
import com.tonezen.app.ui.profile.ProfileScreen
import com.tonezen.app.ui.profile.ProfileSettingsAction
import com.tonezen.app.ui.profile.ProfileViewModel
import dev.chrisbanes.haze.HazeState

internal fun NavGraphBuilder.profileRoutes(
    navController: NavHostController,
    profileViewModel: ProfileViewModel,
    bookWatchViewModel: BookWatchViewModel,
    hazeState: HazeState,
    showMiniPlayer: Boolean,
) {
    composable<ProfileRoute> {
        ProfileRouteScreen(
            page = ProfilePage.Main,
            navController = navController,
            profileViewModel = profileViewModel,
            bookWatchViewModel = bookWatchViewModel,
            hazeState = hazeState,
            showMiniPlayer = showMiniPlayer,
        )
    }
    composable<AccountSettingsRoute> {
        ProfileRouteScreen(
            page = ProfilePage.Account,
            navController = navController,
            profileViewModel = profileViewModel,
            bookWatchViewModel = bookWatchViewModel,
            hazeState = hazeState,
            showMiniPlayer = showMiniPlayer,
        )
    }
    composable<StorageSettingsRoute> {
        ProfileRouteScreen(
            page = ProfilePage.Storage,
            navController = navController,
            profileViewModel = profileViewModel,
            bookWatchViewModel = bookWatchViewModel,
            hazeState = hazeState,
            showMiniPlayer = showMiniPlayer,
        )
    }
    composable<BookWatchRoute> {
        ProfileRouteScreen(
            page = ProfilePage.BookWatch,
            navController = navController,
            profileViewModel = profileViewModel,
            bookWatchViewModel = bookWatchViewModel,
            hazeState = hazeState,
            showMiniPlayer = showMiniPlayer,
        )
    }
}

@Composable
private fun ProfileRouteScreen(
    page: ProfilePage,
    navController: NavHostController,
    profileViewModel: ProfileViewModel,
    bookWatchViewModel: BookWatchViewModel,
    hazeState: HazeState,
    showMiniPlayer: Boolean,
) {
    ProfileScreen(
        padding = PaddingValues(0.dp),
        hazeState = hazeState,
        viewModel = profileViewModel,
        bookWatchViewModel = bookWatchViewModel,
        showMiniPlayer = showMiniPlayer,
        page = page,
        onNavigate = { action ->
            val route = when (action) {
                ProfileSettingsAction.Account -> AccountSettingsRoute
                ProfileSettingsAction.Storage -> StorageSettingsRoute
                ProfileSettingsAction.BookWatch -> BookWatchRoute
            }
            navController.navigate(route) { launchSingleTop = true }
        },
        onBack = { navController.popBackStack() },
    )
}
