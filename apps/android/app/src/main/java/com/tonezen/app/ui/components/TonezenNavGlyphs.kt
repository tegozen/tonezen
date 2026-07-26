package com.tonezen.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted

@Composable
internal fun SearchGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 18.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Search, modifier, tint, size)
}

@Composable
internal fun FilterGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Filter, modifier, tint, size)
}

@Composable
internal fun MailGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Mail, modifier, tint, size)
}

@Composable
internal fun OverflowGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 18.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.MoreVertical, modifier, tint, size)
}

@Composable
internal fun QueueGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Queue, modifier, tint, size)
}

@Composable
internal fun LibraryGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Library, modifier, tint, size)
}

@Composable
internal fun MusicGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Music, modifier, tint, size)
}

@Composable
internal fun BooksGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Books, modifier, tint, size)
}

@Composable
internal fun PlayerGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Player, modifier, tint, size)
}

@Composable
internal fun DownloadGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Download, modifier, tint, size)
}

@Composable
internal fun DownloadsGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Downloads, modifier, tint, size)
}

@Composable
internal fun ProfileGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Profile, modifier, tint, size)
}

@Composable
internal fun ChevronLeftGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.ChevronLeft, modifier, tint, size)
}

@Composable
internal fun ChevronRightGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 18.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.ChevronRight, modifier, tint, size)
}
