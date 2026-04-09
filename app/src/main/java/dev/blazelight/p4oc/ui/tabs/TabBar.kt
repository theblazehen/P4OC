package dev.blazelight.p4oc.ui.tabs

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.domain.model.SessionStateColors
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.core.performance.rememberOptimizedPulse

/**
 * Tab bar showing all open tabs with indicators and close buttons.
 * Reuses visual language from SessionStatusBar.
 */
@Composable
fun TabBar(
    tabs: List<TabInstance>,
    activeTabId: String?,
    tabTitles: Map<String, String>,
    tabIcons: Map<String, ImageVector>,
    tabConnectionStates: Map<String, SessionConnectionState>,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val listState = rememberLazyListState()
    
    // Auto-scroll to active tab when it changes
    LaunchedEffect(activeTabId) {
        val activeIndex = tabs.indexOfFirst { it.id == activeTabId }
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex)
        }
    }
    
    // Terminal-style tab bar - connected to SessionsTopBar
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background)
            .testTag("tab_bar")
    ) {
        // Top connector line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
            Text(
                text = "┬",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.border.copy(alpha = 0.3f)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
        }

        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                items(items = tabs, key = { it.id }) { tab ->
                    val isActive = tab.id == activeTabId
                    val title = tabTitles[tab.id] ?: "Tab"
                    val icon = tabIcons[tab.id] ?: Icons.Default.Tab
                    val connectionState = tabConnectionStates[tab.id]

                    TabIndicator(
                        title = title,
                        icon = icon,
                        connectionState = connectionState,
                        isActive = isActive,
                        onClick = { onTabClick(tab.id) },
                        onClose = { onTabClose(tab.id) }
                    )
                }
            }

            // Add tab button - terminal style
            Text(
                text = "[+new]",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = theme.success,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable(role = Role.Button, onClick = onAddClick)
                    .testTag("tab_bar_add_button")
            )
        }

        // Bottom connector line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "├",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.border.copy(alpha = 0.3f)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(theme.border.copy(alpha = 0.3f))
            )
            Text(
                text = "┤",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.border.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Individual tab indicator showing icon, title, state, and close button.
 */
@Composable
private fun TabIndicator(
    title: String,
    icon: ImageVector,
    connectionState: SessionConnectionState?,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    // Optimized pulse animation for BUSY and AWAITING_INPUT states
    val pulseAlpha by rememberOptimizedPulse(
        key = "tab_pulse_${connectionState?.name}",
        fast = false
    )
    
    val shouldPulse = connectionState?.shouldPulse == true
    val indicatorColor = SessionStateColors.forStateOrNull(connectionState)
    val needsAttention = connectionState?.showsAttentionBadge == true
    
    val activeTabBg = theme.backgroundElement
    val inactiveTabBg = theme.background
    val attentionBg = theme.warning.copy(alpha = if (shouldPulse) pulseAlpha * 0.12f else 0.12f)

    val tabBg = when {
        needsAttention && !isActive -> attentionBg
        isActive -> activeTabBg
        else -> inactiveTabBg
    }
    val textColor = when {
        needsAttention -> theme.warning
        isActive -> theme.text
        else -> theme.textMuted
    }
    val borderColor = when {
        isActive -> theme.accent.copy(alpha = 0.6f)
        else -> theme.border.copy(alpha = 0.3f)
    }

    // Terminal-style tab with box drawing
    Row(
        modifier = modifier
            .height(28.dp)
            .background(tabBg)
            .border(width = 1.dp, color = borderColor)
            .clickable(onClick = onClick, role = Role.Tab)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Left bracket
        Text(
            text = "[",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = theme.border.copy(alpha = 0.5f)
        )

        // State indicator (symbol instead of icon)
        val stateSymbol = when {
            connectionState?.shouldPulse == true -> "▶"
            indicatorColor != null -> "●"
            else -> "○"
        }
        Text(
            text = stateSymbol,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = indicatorColor ?: textColor,
            modifier = Modifier.alpha(if (shouldPulse) pulseAlpha else 1f)
        )

        // Title
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = Sizing.panelWidthSm)
        )

        // Close ×
        Text(
            text = "×",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = if (isActive) theme.error.copy(alpha = 0.8f) else theme.textMuted,
            modifier = Modifier.clickable(onClick = onClose, role = Role.Button)
        )

        // Right bracket
        Text(
            text = "]",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = theme.border.copy(alpha = 0.5f)
        )
    }
}

/**
 * Helper to get appropriate icon for a screen route.
 */
fun getIconForRoute(route: String?): ImageVector {
    return when {
        route == null -> Icons.Default.Tab
        route == "sessions" -> Icons.AutoMirrored.Filled.List
        route.startsWith("chat/") -> Icons.AutoMirrored.Filled.Chat
        route == "files" || route.startsWith("files/") -> Icons.Default.Folder
        route.startsWith("terminal/") -> Icons.Default.Terminal
        route.startsWith("settings") -> Icons.Default.Settings
        route == "projects" -> Icons.Default.FolderOpen
        else -> Icons.Default.Tab
    }
}

/**
 * Helper to get appropriate title for a screen route.
 */
fun getTitleForRoute(route: String?, sessionTitle: String? = null): String {
    return when {
        route == null -> "Tab"
        route == "sessions" -> "Sessions"
        route.startsWith("sessions?") -> "Sessions"
        route.startsWith("chat/") -> sessionTitle ?: "Chat"
        route == "files" -> "Files"
        route.startsWith("files/") -> "File"
        route.startsWith("terminal/") -> sessionTitle ?: "Terminal"
        route == "settings" -> "Settings"
        route.startsWith("settings/") -> "Settings"
        route == "projects" -> "Projects"
        else -> "Tab"
    }
}
