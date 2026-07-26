package com.tonezen.app.ui.player

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.ui.components.TonezenGlassModalBottomSheet
import com.tonezen.app.ui.shell.AppShellUiState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun NowPlayingSheet(
    visible: Boolean,
    hazeState: HazeState,
    shellState: AppShellUiState,
    musicDownload: MusicDownloadState,
    onDismiss: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    LaunchedEffect(visible) {
        if (visible) {
            viewModel.refreshCatalogContext()
        }
    }

    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismiss,
    ) {
        NowPlayingContent(
            shellState = shellState,
            musicDownload = musicDownload,
            viewModel = viewModel,
        )
    }
}
