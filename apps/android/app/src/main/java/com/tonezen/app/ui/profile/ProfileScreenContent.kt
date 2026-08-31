package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.tonezen.app.ui.theme.TonezenFaint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.StorageGlyph
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.components.TonezenTitleChromeBar
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenProfileBottomExtraScrollPadding
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import com.tonezen.app.ui.theme.tonezenScreenContentPadding
import com.tonezen.app.ui.theme.TonezenGreen
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
internal fun ProfileScreenContent(
    padding: PaddingValues,
    hazeState: HazeState,
    state: ProfileUiState,
    showMiniPlayer: Boolean = false,
    onSignOutClick: () -> Unit,
    onAccountClick: () -> Unit,
    onSettingsClick: (ProfileSettingsAction) -> Unit,
    onPeerAcceptClick: () -> Unit,
    onPeerSendClick: () -> Unit,
    bookWatchUnreadCount: Int,
) {
    val online = state.sessionState == SessionState.AUTHENTICATED_ONLINE
    val settingsItems = listOf(
        SettingsItem(
            action = ProfileSettingsAction.Storage,
            title = "Хранилище",
            subtitle = "Офлайн-файлы на устройстве",
            icon = { StorageGlyph(tint = TonezenInk) },
        ),
        SettingsItem(
            action = ProfileSettingsAction.BookWatch,
            title = "Новые книги",
            subtitle = "Отслеживание продолжений циклов",
            icon = { Text("●", color = TonezenGreen) },
            badge = bookWatchUnreadCount,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TonezenSurface)
            .padding(padding),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState),
            contentPadding = tonezenScreenContentPadding(
                top = TonezenPageChromeScrollPadding,
                bottom = tonezenBottomChromeScrollPadding(
                    showMiniPlayer = showMiniPlayer,
                    showBottomNav = true,
                    extraBottom = TonezenProfileBottomExtraScrollPadding,
                ),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ProfileUserCard(
                    displayName = state.displayName.orEmpty(),
                    email = state.email,
                    memberSinceLabel = state.memberSinceLabel,
                    avatarUrl = state.avatarUrl,
                    onClick = onAccountClick,
                )
            }
            item {
                Column {
                    ProfileSectionLabel("Статус синхронизации")
                    SyncStatusCard(
                        lastSyncTime = state.lastSyncTime,
                        pendingSyncCount = state.pendingSyncCount,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileSectionLabel("Настройки")
                    SettingsGroup(
                        items = settingsItems,
                        onItemClick = onSettingsClick,
                    )
                    SignOutCard(onClick = onSignOutClick)
                }
            }
            item {
                Column {
                    ProfileSectionLabel("Синхронизация по блютус")
                    PeerBluetoothSettingsGroup(
                        enabled = true,
                        onAcceptClick = onPeerAcceptClick,
                        onSendClick = onPeerSendClick,
                    )
                }
            }
        }
        TonezenTitleChromeBar(
            modifier = Modifier.align(Alignment.TopCenter),
            hazeState = hazeState,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Профиль",
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(
                    label = if (online) "Онлайн" else "Офлайн",
                    tone = if (online) TonezenGreen else TonezenAmber,
                )
            }
        }
    }
}

@Composable
private fun ProfileUserCard(
    displayName: String,
    email: String?,
    memberSinceLabel: String?,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(avatarUrl = avatarUrl, size = 58.dp, iconSize = 28.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                displayName,
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            email?.takeIf { it.isNotBlank() }?.let { value ->
                Text(value, color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            }
            memberSinceLabel?.let { value ->
                Text(
                    "Участник с ${value}",
                    color = TonezenFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ProfileSectionLabel(label: String) {
    Text(
        label.uppercase(),
        color = TonezenMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}
