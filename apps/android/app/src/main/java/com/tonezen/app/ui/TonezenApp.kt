package com.tonezen.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.auth.AuthScreen
import com.tonezen.app.ui.auth.AuthViewModel
import com.tonezen.app.ui.library.LibraryViewModel
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

    TonezenTheme {
        when {
            !libraryState.isBootstrapComplete -> SplashScreen()
            libraryState.sessionState == SessionState.UNAUTHENTICATED -> AuthScreen(
                padding = PaddingValues(0.dp),
                onLogin = authViewModel::login,
                onVerifyInviteCode = authViewModel::verifyInviteCode,
                onSignup = authViewModel::registerWithInvite,
                onPasswordRecovery = authViewModel::requestPasswordRecovery,
                inviteCodeVerified = authState.inviteCodeVerified,
                passwordRecoverySent = authState.passwordRecoverySent,
                error = authState.error,
            )

            else -> AppShell(libraryViewModel = libraryViewModel)
        }
    }
}
