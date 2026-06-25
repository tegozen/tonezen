package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.library.LibraryContentFilter
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.library.LibrarySortOrder
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSheetTopShape
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch

private val TonezenSheetContentBottomPadding = 20.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
private fun TonezenGlassSheetSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tonezenGlassSurface(hazeState, TonezenSheetTopShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle(
                    color = TonezenMuted.copy(alpha = 0.65f),
                    width = 40.dp,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = TonezenSheetContentBottomPadding),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun TonezenGlassModalBottomSheet(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shouldShowSheet = visible || sheetState.isVisible

    LaunchedEffect(visible) {
        if (!visible && sheetState.isVisible) {
            sheetState.hide()
        }
    }

    if (shouldShowSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    sheetState.hide()
                }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismiss()
                    }
                }
            },
            sheetState = sheetState,
            modifier = modifier,
            shape = RectangleShape,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.35f),
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            TonezenGlassSheetSurface(
                hazeState = hazeState,
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
internal fun TonezenGlassAlertDialog(
    visible: Boolean,
    hazeState: HazeState,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                title()
                text()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterSheet(
    visible: Boolean,
    hazeState: HazeState,
    filter: LibraryFilterState,
    onDismiss: () -> Unit,
    onApply: (LibraryFilterState) -> Unit,
    onReset: () -> Unit,
    onContentFilterChange: (LibraryContentFilter) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
) {
    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Поиск и фильтр", color = TonezenInk, fontWeight = FontWeight.SemiBold)
            FilterChipRow(
                label = "Все",
                selected = filter.contentFilter == LibraryContentFilter.ALL,
                onClick = { onContentFilterChange(LibraryContentFilter.ALL) },
            )
            FilterChipRow(
                label = "Загруженные",
                selected = filter.contentFilter == LibraryContentFilter.DOWNLOADED,
                onClick = { onContentFilterChange(LibraryContentFilter.DOWNLOADED) },
            )
            Text("Сортировка", color = TonezenMuted)
            FilterChipRow(
                label = "Недавно слушали",
                selected = filter.sortOrder == LibrarySortOrder.RECENTLY_PLAYED,
                onClick = { onSortOrderChange(LibrarySortOrder.RECENTLY_PLAYED) },
            )
            FilterChipRow(
                label = "Название",
                selected = filter.sortOrder == LibrarySortOrder.TITLE,
                onClick = { onSortOrderChange(LibrarySortOrder.TITLE) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TonezenSheetSecondaryButton(
                    label = "Сбросить",
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                )
                TonezenSheetPrimaryButton(
                    label = "Применить",
                    onClick = { onApply(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FilterChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) TonezenTeal.copy(alpha = 0.15f) else TonezenSurfaceRaised.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp),
            )
            .border(
                BorderStroke(1.dp, if (selected) TonezenTeal else TonezenBorder),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (selected) TonezenTeal else TonezenInk,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadConfirmSheet(
    visible: Boolean,
    hazeState: HazeState,
    estimatedBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var includeAudio by remember { mutableStateOf(true) }
    var includeCover by remember { mutableStateOf(true) }
    TonezenGlassModalBottomSheet(
        visible = visible,
        hazeState = hazeState,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Скачать аудиокнигу?", color = TonezenInk, fontWeight = FontWeight.SemiBold)
            Text("Будет использовано примерно ${formatMegabytes(estimatedBytes)} памяти.", color = TonezenMuted)
            ToggleRow("Аудиофайлы", includeAudio) { includeAudio = it }
            ToggleRow("Обложка", includeCover) { includeCover = it }
            Button(
                onClick = onConfirm,
                enabled = includeAudio,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
            ) {
                Text("Скачать офлайн")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена", color = TonezenMuted)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TonezenInk)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = TonezenTeal),
        )
    }
}

@Composable
internal fun SignOutConfirmDialog(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Выйти из аккаунта?",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text("Офлайн-загрузки останутся на устройстве. Прогресс аудиокниг синхронизируется снова после входа онлайн.", color = TonezenMuted)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Выйти", color = TonezenError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
internal fun OfflineSyncDialog(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Синхронизация приостановлена",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text("Вы офлайн. Прогресс аудиокниг синхронизируется, когда появится сеть.", color = TonezenMuted)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Продолжить слушать", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text("Повторить")
            }
        },
    )
}

private fun formatMegabytes(bytes: Long): String = "%.0f MB".format(bytes / (1024.0 * 1024.0))
