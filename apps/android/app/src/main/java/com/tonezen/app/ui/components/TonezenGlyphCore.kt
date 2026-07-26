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

internal enum class TonezenSvgAsset(val fileName: String) {
    Search("search.svg"),
    Mail("mail.svg"),
    Filter("filter.svg"),
    MoreVertical("more-vertical.svg"),
    Queue("queue.svg"),
    Library("library.svg"),
    Music("music.svg"),
    Books("books.svg"),
    Player("player.svg"),
    Download("download.svg"),
    Downloads("downloads.svg"),
    Profile("profile.svg"),
    CheckCircle("check-circle.svg"),
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
internal fun TonezenSvgGlyph(
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
