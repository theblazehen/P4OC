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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
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
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.background,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = tabs,
                    key = { it.id }
                ) { tab ->
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
            
            // Add button
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(Sizing.iconLg)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New tab",
                    modifier = Modifier.size(Sizing.iconSm),
                    tint = theme.textMuted
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
    
    val backgroundColor = when {
        needsAttention && !isActive -> theme.warning.copy(alpha = if (shouldPulse) pulseAlpha * 0.15f else 0.15f)
        isActive -> theme.backgroundElement
        else -> theme.background
    }
    
    Surface(
        modifier = modifier
            .height(22.dp)
            .clickable(onClick = onClick),
        shape = RectangleShape,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            // State indicator dot (only for chat tabs with connection state)
            if (indicatorColor != null) {
                Box(
                    modifier = Modifier
                        .size(if (isActive) 8.dp else 6.dp)
                        .alpha(if (shouldPulse) pulseAlpha else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Circle,
                        contentDescription = connectionState?.name,
                        modifier = Modifier.size(if (isActive) 8.dp else 6.dp),
                        tint = indicatorColor
                    )
                    
                    // Badge for AWAITING_INPUT
                    if (connectionState?.showsAttentionBadge == true) {
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "Needs attention",
                            modifier = Modifier
                                .size(5.dp) // Tiny badge dot, no token
                                .offset(x = 3.dp, y = (-3).dp),
                            tint = theme.warning
                        )
                    }
                }
            } else {
                // Icon for non-chat tabs
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (isActive) Sizing.iconXs else Sizing.iconXs),
                    tint = if (isActive) theme.text else theme.textMuted
                )
            }
            
            // Truncated title
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    needsAttention -> theme.warning
                    isActive -> theme.text
                    else -> theme.textMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 80.dp)
            )
            
            // Close button
            // Close button - only show on active tab
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier
                        .size(Sizing.iconXs)
                        .clickable(onClick = onClose),
                    tint = theme.textMuted
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
