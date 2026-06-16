package com.tonezen.app.ui

import androidx.compose.runtime.Composable
import com.tonezen.app.ui.theme.TonezenTheme

@Composable
internal fun TonezenComposeTestContent(content: @Composable () -> Unit) {
    TonezenTheme(content = content)
}
