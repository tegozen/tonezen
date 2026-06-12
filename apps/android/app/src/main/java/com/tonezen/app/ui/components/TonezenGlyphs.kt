package com.tonezen.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted

private enum class TonezenSvgAsset(val fileName: String) {
    Search("search.svg"),
    Mail("mail.svg"),
    Filter("filter.svg"),
    MoreVertical("more-vertical.svg"),
    Queue("queue.svg"),
    Library("library.svg"),
    Player("player.svg"),
    Download("download.svg"),
    Profile("profile.svg"),
    CheckCircle("check-circle.svg"),
    SkipPrevious("skip-previous.svg"),
    SkipNext("skip-next.svg"),
    ChevronLeft("chevron-left.svg"),
    ChevronRight("chevron-right.svg"),
    Storage("storage.svg"),
    Sync("sync.svg"),
    Lock("lock.svg"),
    Warning("warning.svg"),
    Play("play.svg"),
    Pause("pause.svg"),
    Eye("eye.svg"),
    EyeOff("eye-off.svg"),
}

@Composable
private fun TonezenSvgGlyph(
    asset: TonezenSvgAsset,
    modifier: Modifier = Modifier,
    tint: Color,
    size: Dp,
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/icons/${asset.fileName}")
            .decoderFactory(SvgDecoder.Factory())
            .build(),
    )
    Icon(
        painter = painter,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

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
internal fun PlayerGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Player, modifier, tint, size)
}

@Composable
internal fun DownloadGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Download, modifier, tint, size)
}

@Composable
internal fun ProfileGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Profile, modifier, tint, size)
}

@Composable
internal fun CheckCircleGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.CheckCircle, modifier, tint, size)
}

@Composable
internal fun SkipPreviousGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.SkipPrevious, modifier, tint, size)
}

@Composable
internal fun SkipNextGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.SkipNext, modifier, tint, size)
}

@Composable
internal fun ChevronLeftGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.ChevronLeft, modifier, tint, size)
}

@Composable
internal fun ChevronRightGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 18.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.ChevronRight, modifier, tint, size)
}

@Composable
internal fun StorageGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Storage, modifier, tint, size)
}

@Composable
internal fun SyncGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Sync, modifier, tint, size)
}

@Composable
internal fun LockGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Lock, modifier, tint, size)
}

@Composable
internal fun WarningGlyph(modifier: Modifier = Modifier, tint: Color = TonezenAmber, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Warning, modifier, tint, size)
}

@Composable
internal fun PlayGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Play, modifier, tint, size)
}

@Composable
internal fun PauseGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Pause, modifier, tint, size)
}

@Composable
internal fun EyeGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Eye, modifier, tint, size)
}

@Composable
internal fun EyeOffGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.EyeOff, modifier, tint, size)
}
