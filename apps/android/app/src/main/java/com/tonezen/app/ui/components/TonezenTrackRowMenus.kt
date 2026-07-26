package com.tonezen.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun TrackRowOverflowMenu(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    deleteLabel: String = "Удалить трек",
    showDelete: Boolean = true,
    onToggleListened: (() -> Unit)? = null,
    isListened: Boolean = false,
) {
    val hasMenuItems = onToggleListened != null || showDelete
    if (!hasMenuItems) return

    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            OverflowGlyph(tint = TonezenMuted)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            onToggleListened?.let { toggleListened ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isListened) "Отметить не прослушанным" else "Отметить прослушанным",
                            color = TonezenInk,
                        )
                    },
                    onClick = {
                        expanded = false
                        toggleListened()
                    },
                )
            }
            if (showDelete) {
                DropdownMenuItem(
                    text = {
                        Text(
                            deleteLabel,
                            color = TonezenError,
                        )
                    },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
internal fun DetailHeaderOverflowMenu(
    showDownload: Boolean,
    isDownloaded: Boolean = false,
    showRemoveDownload: Boolean = false,
    isListened: Boolean,
    onDownload: () -> Unit,
    onToggleListened: () -> Unit,
    onRemoveDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            OverflowGlyph(tint = TonezenMuted)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (showRemoveDownload) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Удалить загрузку",
                            color = TonezenError,
                        )
                    },
                    onClick = {
                        expanded = false
                        onRemoveDownloads()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        if (isListened) "Отметить не прослушанным" else "Отметить прослушанным",
                        color = TonezenInk,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleListened()
                },
            )
            when {
                isDownloaded -> {
                    // Show checkmark instead of download button when fully downloaded
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CheckCircleGlyph(tint = TonezenTeal, size = 20.dp)
                    }
                }
                showDownload -> {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Скачать все",
                                color = TonezenInk,
                            )
                        },
                        onClick = {
                            expanded = false
                            onDownload()
                        },
                    )
                }
            }
        }
    }
}
