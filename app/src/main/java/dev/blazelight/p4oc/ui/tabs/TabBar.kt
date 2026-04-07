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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
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
    
    // Tab bar: background color, tabs sit on top with rounded corners
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.background)
            .testTag("tab_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xs, end = Spacing.xs, top = 5.dp, bottom = 0.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
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

            // Add tab button — aligned bottom
            Box(
                modifier = Modifier
                    .padding(start = 4.dp, bottom = 2.dp)
                    .height(28.dp)
                    .width(28.dp)
                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                    .background(theme.backgroundPanel.copy(alpha = 0.5f))
                    .border(1.dp, theme.border.copy(alpha = 0.25f), RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                    .clickable(role = Role.Button, onClick = onAddClick)
                    .testTag("tab_bar_add_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    color = theme.textMuted
                )
            }
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
    
    // Pulse animation for BUSY and AWAITING_INPUT states
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val shouldPulse = connectionState?.shouldPulse == true
    val indicatorColor = SessionStateColors.forStateOrNull(connectionState)
    val needsAttention = connectionState?.showsAttentionBadge == true
    
    val tabShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
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
        isActive -> theme.accent.copy(alpha = 0.45f)
        else -> theme.border.copy(alpha = 0.18f)
    }

    Box(
        modifier = modifier
            .height(30.dp)
            .clip(tabShape)
            .background(tabBg)
            .border(width = 1.dp, color = borderColor, shape = tabShape)
            .clickable(onClick = onClick, role = Role.Tab)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // State dot or icon
            if (indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .size(Sizing.indicatorDotActive)
                        .alpha(if (shouldPulse) pulseAlpha else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = connectionState?.name,
                        modifier = Modifier.size(Sizing.indicatorDotActive),
                        tint = indicatorColor
                    )
                    if (connectionState?.showsAttentionBadge == true) {
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "Needs attention",
                            modifier = Modifier.size(5.dp).offset(x = 3.dp, y = (-3).dp),
                            tint = theme.warning
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(12.dp),
                    tint = textColor
                )
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = Sizing.panelWidthSm)
            )

            // Close ×
            if (isActive) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    color = theme.textMuted,
                    modifier = Modifier.clickable(onClick = onClose, role = Role.Button)
                )
            }
        }
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
