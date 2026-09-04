package com.tonezen.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.auth.AuthScreen
import com.tonezen.app.ui.auth.AuthViewModel
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.navigation.AppRoute
import com.tonezen.app.ui.navigation.AuthRoute
import com.tonezen.app.ui.navigation.SplashRoute
import com.tonezen.app.ui.shell.AppShell
import com.tonezen.app.ui.splash.SplashScreen
import com.tonezen.app.ui.theme.TonezenTheme

@Composable
fun TonezenApp(
    authViewModel: AuthViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val target = when {
        !libraryState.isBootstrapComplete -> RootDestination.Splash
        libraryState.sessionState == SessionState.UNAUTHENTICATED -> RootDestination.Auth
        else -> RootDestination.App
    }

    LaunchedEffect(target, navBackStackEntry?.destination) {
        val destination = navBackStackEntry?.destination ?: return@LaunchedEffect
        val alreadyAtTarget = when (target) {
            RootDestination.Splash -> destination.hasRoute<SplashRoute>()
            RootDestination.Auth -> destination.hasRoute<AuthRoute>()
            RootDestination.App -> destination.hasRoute<AppRoute>()
        }
        if (alreadyAtTarget) return@LaunchedEffect
        val route = when (target) {
            RootDestination.Splash -> SplashRoute
            RootDestination.Auth -> AuthRoute
            RootDestination.App -> AppRoute
        }
        navController.navigate(route) {
            popUpTo(destination.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    TonezenTheme {
        NavHost(
            navController = navController,
            startDestination = SplashRoute,
        ) {
            composable<SplashRoute> { SplashScreen() }
            composable<AuthRoute> {
                AuthScreen(
                    padding = PaddingValues(0.dp),
                    onLogin = authViewModel::login,
                    onVerifyInviteCode = authViewModel::verifyInviteCode,
                    onSignup = authViewModel::registerWithInvite,
                    onPasswordRecovery = authViewModel::requestPasswordRecovery,
                    inviteCodeVerified = authState.inviteCodeVerified,
                    passwordRecoverySent = authState.passwordRecoverySent,
                    error = authState.error,
                )
            }
            composable<AppRoute> {
                AppShell(libraryViewModel = libraryViewModel)
            }
        }
    }
}

private enum class RootDestination { Splash, Auth, App }
