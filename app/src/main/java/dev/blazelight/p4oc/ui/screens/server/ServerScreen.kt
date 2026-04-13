package dev.blazelight.p4oc.ui.screens.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.RecentServer
import dev.blazelight.p4oc.core.network.DiscoveredServer
import dev.blazelight.p4oc.core.network.DiscoveryState
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme
import dev.blazelight.p4oc.ui.theme.opencode.OptimizedThemeLoader
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServerScreen(
    viewModel: ServerViewModel = koinViewModel(),
    onNavigateToSessions: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onSettings: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // NO iniciar conexión automática inmediatamente
    // La conexión se iniciará solo después de que el tema esté cargado

    LaunchedEffect(uiState.navigationDestination) {
        when (uiState.navigationDestination) {
            is NavigationDestination.Sessions -> {
                viewModel.clearNavigationDestination()
                onNavigateToSessions()
            }
            is NavigationDestination.Projects -> {
                viewModel.clearNavigationDestination()
                onNavigateToProjects()
            }
            null -> {}
        }
    }

    // Only start connection after theme is ready
    LaunchedEffect(Unit) {
        // Wait for theme to be ready AND user theme is loaded before starting discovery
        while (!OptimizedThemeLoader.isThemeReady()) {
            delay(50) // Check every 50ms
        }
        
        // Additional delay to ensure user theme is completely loaded and entrance animation can complete
        delay(260)
        
        // Now start discovery after theme is ready
        viewModel.startDiscovery()
    }

    val smoothSpringDp  = spring<Dp>(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium)
    val smoothSpringFlt = spring<Float>(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium)

    var started      by remember { mutableStateOf(false) }
    var showHeader   by remember { mutableStateOf(false) }
    var showDiscover by remember { mutableStateOf(false) }
    var showRecent   by remember { mutableStateOf(false) }
    var showRemote   by remember { mutableStateOf(false) }
    var showHelp     by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        started    = true
        delay(30);  showHeader   = true
        delay(60);  showDiscover = true
        delay(55);  showRecent   = true
        delay(55);  showRemote   = true
        delay(70);  showHelp     = true
    }

    val pageAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing), label = "pA"
    )
    val pageOffset by animateDpAsState(
        targetValue = if (started) Spacing.none else 24.dp,
        animationSpec = smoothSpringDp, label = "pO"
    )
    val pageScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.96f,
        animationSpec = smoothSpringFlt, label = "pS"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .alpha(pageAlpha)
                .offset(y = pageOffset)
                .graphicsLayer { scaleX = pageScale; scaleY = pageScale }
                .padding(horizontal = Spacing.sm)
        ) {
            // Hero + panel share the same horizontal padding, flowing as one
            ServerHeroHeader(
                theme = theme,
                visible = showHeader,
                springDp = smoothSpringDp,
                onSettings = onSettings
            )
            UnifiedServerPanel(
                uiState = uiState,
                showDiscover = showDiscover,
                showRecent   = showRecent,
                showRemote   = showRemote,
                showHelp     = showHelp,
                springDp     = smoothSpringDp,
                springFlt    = smoothSpringFlt,
                onDiscoveredClick  = viewModel::connectToDiscoveredServer,
                onStopDiscovery    = viewModel::stopDiscovery,
                onRecentClick      = viewModel::connectToRecentServer,
                onRemoveRecent     = viewModel::removeRecentServer,
                onUrlChange        = viewModel::setRemoteUrl,
                onUsernameChange   = viewModel::setUsername,
                onPasswordChange   = viewModel::setPassword,
                onConnect          = viewModel::connectToRemote
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

// ── Unified Panel ────────────────────────────────────────────────────────────

/**
 * One continuous ASCII art terminal panel that contains every section.
 * Structure:
 *
 *  ⌜─────────────────────────────────────────────────────────⌝
 *  ├─[ DISCOVERED SERVERS ]─────────────────── ● scan
 *  │ ...rows...
 *  ├─[ RECENT SERVERS ]───────────────────────
 *  │ ...rows...
 *  ├─[ REMOTE SERVER ]────────────────────────
 *  │ ...fields + button...
 *  ├─[ SERVER SETUP ]─────────────────────────
 *  │ ...collapsible...
 *  ⌞─────────────────────────────────────────────────────────⌟
 */
@Composable
private fun UnifiedServerPanel(
    uiState: ServerUiState,
    showDiscover: Boolean,
    showRecent: Boolean,
    showRemote: Boolean,
    showHelp: Boolean,
    springDp: androidx.compose.animation.core.SpringSpec<Dp>,
    springFlt: androidx.compose.animation.core.SpringSpec<Float>,
    onDiscoveredClick: (DiscoveredServer) -> Unit,
    onStopDiscovery: () -> Unit,
    onRecentClick: (RecentServer) -> Unit,
    onRemoveRecent: (RecentServer) -> Unit,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current

    // Stagger alphas for each section row inside the panel
    val dA by animateFloatAsState(if (showDiscover) 1f else 0f,
        tween(280, easing = FastOutSlowInEasing), label = "dA")
    val dO by animateDpAsState(if (showDiscover) Spacing.none else 14.dp,
        springDp, label = "dO")
    val rA by animateFloatAsState(if (showRecent) 1f else 0f,
        tween(300, easing = FastOutSlowInEasing), label = "rA")
    val rO by animateDpAsState(if (showRecent) Spacing.none else 14.dp,
        springDp, label = "rO")
    val fA by animateFloatAsState(if (showRemote) 1f else 0f,
        tween(320, easing = FastOutSlowInEasing), label = "fA")
    val fO by animateDpAsState(if (showRemote) Spacing.none else 14.dp,
        springDp, label = "fO")
    val hA by animateFloatAsState(if (showHelp) 1f else 0f,
        tween(340, easing = FastOutSlowInEasing), label = "hA")
    val hO by animateDpAsState(if (showHelp) Spacing.none else 14.dp,
        springDp, label = "hO")

    Column(modifier = Modifier.fillMaxWidth()) {
        // ⌜ top border — animated scan
        TuiTopFrame(animated = true)

        // Panel body — left + right border drawn by single-pixel Box columns
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left border bar
            Box(modifier = Modifier.width(Sizing.strokeThin)
                .fillMaxHeight()
                .background(theme.border.copy(alpha = 0.35f)))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(theme.backgroundElement.copy(alpha = 0.4f))
            ) {
            // ── DISCOVERED SERVERS ──
            if (uiState.discoveredServers.isNotEmpty() || uiState.discoveryState == DiscoveryState.SCANNING) {
                Column(modifier = Modifier.alpha(dA).offset(y = dO)) {
                    DiscoveredServersSection(
                        servers = uiState.discoveredServers,
                        discoveryState = uiState.discoveryState,
                        isConnecting = uiState.isConnecting,
                        onServerClick = onDiscoveredClick,
                        onStopClick = onStopDiscovery
                    )
                }
                PanelDivider()
            }

            // ── RECENT SERVERS ──
            if (uiState.recentServers.isNotEmpty()) {
                Column(modifier = Modifier.alpha(rA).offset(y = rO)) {
                    RecentServersSection(
                        servers = uiState.recentServers,
                        isConnecting = uiState.isConnecting,
                        onServerClick = onRecentClick,
                        onRemoveServer = onRemoveRecent
                    )
                }
                PanelDivider()
            }

            // ── REMOTE SERVER ──
            Column(modifier = Modifier.alpha(fA).offset(y = fO)) {
                RemoteServerSection(
                    url = uiState.remoteUrl,
                    username = uiState.username,
                    password = uiState.password,
                    isConnecting = uiState.isConnecting,
                    onUrlChange = onUrlChange,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onConnect = onConnect
                )
            }

            // ── ERROR ──
            uiState.error?.let { err ->
                PanelDivider()
                ErrorBanner(error = err)
            }

            // ── SETUP HELP ──
            PanelDivider()
            Column(modifier = Modifier.alpha(hA).offset(y = hO)) {
                ServerSetupHelpSection()
            }
            }
            // Right border bar
            Box(modifier = Modifier.width(Sizing.strokeThin)
                .fillMaxHeight()
                .background(theme.border.copy(alpha = 0.35f)))
        }

        // ⌞ bottom border
        TuiBottomFrame()
    }
}

/** Internal horizontal divider between panel sections — ├──── */
@Composable
private fun PanelDivider() {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "├", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = theme.border.copy(alpha = 0.5f))
        Box(
            modifier = Modifier.weight(1f).height(Sizing.dividerThickness)
                .background(Brush.horizontalGradient(listOf(
                    theme.border.copy(alpha = 0.5f),
                    theme.border.copy(alpha = 0.1f),
                    theme.border.copy(alpha = 0.05f)
                )))
        )
        Text(text = "┤", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = theme.border.copy(alpha = 0.3f))
    }
}

// ── ASCII Art helpers ─────────────────────────────────────────────────────────

/** Gradient separator:  ───────────────────  */
@Composable
private fun TuiGradientLine() {
    val theme = LocalOpenCodeTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizing.dividerThickness)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        theme.border.copy(alpha = 0.05f),
                        theme.border.copy(alpha = 0.4f),
                        theme.border.copy(alpha = 0.05f)
                    )
                )
            )
    )
}

/** Top frame row:  ⌜─────────────────────────⌝  with optional scan pulse */
@Composable
private fun TuiTopFrame(accentAlpha: Float = 0.8f, animated: Boolean = false) {
    val theme = LocalOpenCodeTheme.current
    val scanX = if (animated) {
        val tr = rememberInfiniteTransition(label = "topScan")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing),
            RepeatMode.Restart), label = "sX").value
    } else -1f
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "⌜", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent.copy(alpha = accentAlpha))
        Box(modifier = Modifier.weight(1f).height(Sizing.dividerThickness).clipToBounds()) {
            Box(modifier = Modifier.fillMaxSize()
                .background(Brush.horizontalGradient(listOf(
                    theme.accent.copy(alpha = accentAlpha * 0.9f),
                    theme.border.copy(alpha = 0.15f),
                    theme.accent.copy(alpha = accentAlpha * 0.9f)
                ))))
            if (animated && scanX >= 0f) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.15f)
                    .offset(x = ((scanX - 0.15f) * 2000).dp)
                    .background(Brush.horizontalGradient(listOf(
                        theme.accent.copy(alpha = 0f),
                        theme.accent.copy(alpha = 0.9f),
                        theme.accent.copy(alpha = 0f)
                    ))))
            }
        }
        Text(text = "⌝", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent.copy(alpha = accentAlpha))
    }
}

/** Bottom frame row:  ⌞─────────────────────────⌟  */
@Composable
private fun TuiBottomFrame(dimAlpha: Float = 0.3f) {
    val theme = LocalOpenCodeTheme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "⌞", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.border.copy(alpha = dimAlpha))
        Box(
            modifier = Modifier.weight(1f).height(Sizing.dividerThickness)
                .background(Brush.horizontalGradient(listOf(
                    theme.border.copy(alpha = dimAlpha),
                    theme.border.copy(alpha = 0.05f),
                    theme.border.copy(alpha = dimAlpha)
                )))
        )
        Text(text = "⌟", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.border.copy(alpha = dimAlpha))
    }
}

/** Section label row:  ├─[ LABEL ]──────── (trailing slot optional)  */
@Composable
private fun TuiSectionLabel(label: String, trailing: @Composable (() -> Unit)? = null) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(text = "├─", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent)
        Text(text = "[", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent)
        Text(text = " $label ", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = theme.accent)
        Text(text = "]", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent)
        Box(
            modifier = Modifier.weight(1f).height(Sizing.dividerThickness)
                .background(Brush.horizontalGradient(listOf(
                    theme.accent.copy(alpha = 0.6f),
                    theme.border.copy(alpha = 0.08f)
                )))
        )
        trailing?.invoke()
    }
}

// ── Error Banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(error: String) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.error.copy(alpha = 0.08f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "✗", color = theme.error, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium)
        Text(text = error, color = theme.error, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

// ── Discovered Servers ───────────────────────────────────────────────────────

@Composable
private fun DiscoveredServersSection(
    servers: List<DiscoveredServer>,
    discoveryState: DiscoveryState,
    isConnecting: Boolean,
    onServerClick: (DiscoveredServer) -> Unit,
    onStopClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val scanning = discoveryState == DiscoveryState.SCANNING

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.none)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TuiSectionLabel(
                label = stringResource(R.string.discovery_section_title),
                trailing = if (scanning) ({
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        ScanPulse()
                        Text(text = "scan", fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall, color = theme.textMuted)
                        Text(text = "·", color = theme.border, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "[stop]",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.accent,
                            modifier = Modifier
                                .clickable(role = Role.Button) { onStopClick() }
                                .padding(horizontal = Spacing.xxs)
                        )
                    }
                }) else null
            )

            if (servers.isEmpty() && scanning) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = Spacing.xs)
                ) {
                    Text(text = "~\$", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted.copy(alpha = 0.5f))
                    Text(text = stringResource(R.string.discovery_scanning_hint),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, color = theme.textMuted)
                }
            }
        }

        if (servers.isNotEmpty()) {
            servers.forEachIndexed { index, server ->
                DiscoveredServerItem(server = server, isConnecting = isConnecting,
                    onClick = { onServerClick(server) })
                if (index < servers.size - 1) TuiGradientLine()
            }
        }
    }
}

@Composable
private fun DiscoveredServerItem(
    server: DiscoveredServer,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val isTailscale = server.host.startsWith("100.") || server.serviceName.contains(".ts.net")
    val networkLabel = if (isTailscale) "VPN" else "LAN"
    val networkColor = if (isTailscale) theme.info else theme.success

    // Left status bar + content — same pattern as SessionCard
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(enabled = !isConnecting, role = Role.Button) { onClick() }
            .testTag("discovered_server_${server.serviceName}")
    ) {
        Box(modifier = Modifier.width(Sizing.strokeMd).fillMaxHeight().background(networkColor))
        Row(
            modifier = Modifier
                .weight(1f)
                .background(theme.background.copy(alpha = 0.35f))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(text = "●", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, color = networkColor)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.serviceName.removePrefix("opencode-").ifEmpty { server.serviceName },
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium, color = theme.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(text = "${server.host}:${server.port}",
                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    color = theme.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .background(networkColor.copy(alpha = 0.12f))
                    .border(Sizing.strokeMd, networkColor.copy(alpha = 0.3f))
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            ) {
                Text(text = networkLabel, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = networkColor, fontWeight = FontWeight.Medium)
            }
            Text(text = "→", color = theme.textMuted, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Recent Servers ────────────────────────────────────────────────────────────

@Composable
private fun RecentServersSection(
    servers: List<RecentServer>,
    isConnecting: Boolean,
    onServerClick: (RecentServer) -> Unit,
    onRemoveServer: (RecentServer) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.none)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TuiSectionLabel(label = stringResource(R.string.server_recent_servers))
        }
        servers.forEachIndexed { index, server ->
            RecentServerItem(server = server, isConnecting = isConnecting,
                onClick = { onServerClick(server) }, onRemove = { onRemoveServer(server) })
            if (index < servers.size - 1) TuiGradientLine()
        }
    }
}

@Composable
private fun RecentServerItem(
    server: RecentServer,
    isConnecting: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(enabled = !isConnecting, role = Role.Button) { onClick() }
    ) {
        Box(modifier = Modifier.width(Sizing.strokeMd).fillMaxHeight()
            .background(theme.textMuted.copy(alpha = 0.4f)))
        Row(
            modifier = Modifier
                .weight(1f)
                .background(theme.background.copy(alpha = 0.35f))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(text = "○", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted.copy(alpha = 0.6f))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = server.name, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                    color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = server.url, style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace, color = theme.textMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = "×",
                color = theme.textMuted,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .clickable(role = Role.Button) { onRemove() }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            )
        }
    }
}

// ── Remote Server Form ────────────────────────────────────────────────────────

@Composable
private fun RemoteServerSection(
    url: String,
    username: String,
    password: String,
    isConnecting: Boolean,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    var passwordVisible by remember { mutableStateOf(false) }
    val canConnect = url.isNotBlank() && !isConnecting

    Column(modifier = Modifier.fillMaxWidth()) {

        // Section header
        Column(modifier = Modifier.fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            TuiSectionLabel(label = stringResource(R.string.server_remote_title))
            Spacer(Modifier.height(Spacing.xxs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically) {
                Text(text = "#", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent.copy(alpha = 0.3f))
                Text(text = stringResource(R.string.server_remote_description),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted.copy(alpha = 0.7f))
            }
        }

        TuiGradientLine()

        // ── Terminal input fields ──────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            TerminalInput(
                label = stringResource(R.string.field_server_url),
                value = url,
                onValueChange = onUrlChange,
                placeholder = stringResource(R.string.field_server_url_placeholder),
                prefix = "url",
                modifier = Modifier.testTag("server_url_input")
            )
            TerminalInput(
                label = stringResource(R.string.field_username),
                value = username,
                onValueChange = onUsernameChange,
                prefix = "usr"
            )
            TerminalInput(
                label = stringResource(R.string.field_password),
                value = password,
                onValueChange = onPasswordChange,
                prefix = "pwd",
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible }
            )
        }

        TuiGradientLine()

        // ── Connect button — full width terminal-style ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizing.buttonHeightLg)
                .background(
                    if (canConnect)
                        Brush.horizontalGradient(listOf(
                            theme.accent.copy(alpha = 0.85f),
                            theme.accent,
                            theme.accent.copy(alpha = 0.85f)
                        ))
                    else
                        Brush.horizontalGradient(listOf(
                            theme.accent.copy(alpha = 0.1f),
                            theme.accent.copy(alpha = 0.15f)
                        ))
                )
                .then(if (canConnect) Modifier.clickable(role = Role.Button) { onConnect() } else Modifier)
                .testTag("server_connect_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically) {
                    TuiLoadingIndicator()
                    Text(stringResource(R.string.button_connecting),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium, color = theme.background)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "▶", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (canConnect) theme.background else theme.textMuted.copy(alpha = 0.35f))
                    Text(text = stringResource(R.string.button_connect),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (canConnect) theme.background else theme.textMuted.copy(alpha = 0.35f))
                }
            }
        }
    }
}

// ── Setup Help Section ────────────────────────────────────────────────────────

@Composable
private fun ServerSetupHelpSection() {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.none)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Collapsible header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = Spacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(text = if (expanded) "▾" else "▸", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
                    Text(text = stringResource(R.string.server_setup_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace, color = theme.textMuted)
                }
                Text(text = "man setup", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted.copy(alpha = 0.35f))
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TuiGradientLine()
                    Text(text = stringResource(R.string.server_setup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, color = theme.textMuted)

                    SetupStep("1", stringResource(R.string.server_setup_step1_title), stringResource(R.string.server_setup_step1_cmd))
                    SetupStep("2", stringResource(R.string.server_setup_step2_title), stringResource(R.string.server_setup_step2_cmd))
                    SetupStep("3", stringResource(R.string.server_setup_step3_title), stringResource(R.string.server_setup_step3_cmd))

                    TuiGradientLine()

                    // Tip block — accent ASCII bordered
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.accent.copy(alpha = 0.05f))
                            .border(Sizing.strokeMd, theme.accent.copy(alpha = 0.25f))
                            .padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Text(text = "▸", fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall, color = theme.accent)
                            Text(text = stringResource(R.string.server_setup_tip_label),
                                fontFamily = FontFamily.Monospace, color = theme.accent,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Text(text = stringResource(R.string.server_setup_tip_text),
                            fontFamily = FontFamily.Monospace, color = theme.textMuted,
                            style = MaterialTheme.typography.bodySmall)
                        TerminalCodeBlock(stringResource(R.string.server_setup_find_ip))
                        Text(text = stringResource(R.string.server_setup_test_hint),
                            fontFamily = FontFamily.Monospace, color = theme.textMuted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStep(number: String, title: String, command: String) {
    val theme = LocalOpenCodeTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Text(text = "[$number]", fontFamily = FontFamily.Monospace,
                color = theme.accent, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold)
            Text(text = title, fontFamily = FontFamily.Monospace,
                color = theme.text, style = MaterialTheme.typography.bodySmall)
        }
        TerminalCodeBlock(command = command)
    }
}

@Composable
private fun TerminalCodeBlock(command: String) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.background)
            .border(Sizing.strokeThin, theme.border.copy(alpha = 0.5f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$", fontFamily = FontFamily.Monospace,
            color = theme.textMuted, style = MaterialTheme.typography.bodySmall)
        Text(text = command, modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace, color = theme.accent,
            style = MaterialTheme.typography.bodySmall)
    }
}

// ── ASCII Terminal Input ───────────────────────────────────────────────────────

/**
 * Fully custom ASCII-art input field. No Material OutlinedTextField.
 *
 *  ┌─ pwd ───────────────────────────────────────────┐
 *  │ Password (optional)                        ○ │
 *  │ _                                            │
 *  └─────────────────────────────────────────────┘
 */
@Composable
private fun TerminalInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String = "",
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) theme.accent else theme.border.copy(alpha = 0.45f)
    val cornerColor = if (isFocused) theme.accent else theme.border.copy(alpha = 0.6f)
    val labelColor  = if (isFocused) theme.accent else theme.textMuted.copy(alpha = 0.7f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Top border: ┌─ prefix ───────────────┐
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text = "┌─", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, color = cornerColor)
            if (prefix.isNotEmpty()) {
                Text(text = prefix, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold)
                Text(text = " ─", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall, color = cornerColor)
            }
            Box(modifier = Modifier.weight(1f).height(Sizing.strokeThin)
                .background(Brush.horizontalGradient(listOf(
                    borderColor.copy(alpha = 0.8f), borderColor.copy(alpha = 0.2f)
                ))))
            Text(text = "┐", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, color = cornerColor)
        }

        // Body row: │ label ··· [toggle] │
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(theme.backgroundElement.copy(alpha = if (isFocused) 0.7f else 0.45f))
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "│", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, color = borderColor)
            Spacer(Modifier.width(Spacing.xs))
            Column(modifier = Modifier.weight(1f)
                .padding(vertical = Spacing.xs)) {
                // Label line
                Text(text = label, fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall, color = labelColor)
                // Input line
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, color = theme.text
                    ),
                    cursorBrush = SolidColor(theme.accent),
                    visualTransformation = if (isPassword && !passwordVisible)
                        PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = if (isPassword)
                        KeyboardOptions(keyboardType = KeyboardType.Password)
                    else KeyboardOptions.Default,
                    interactionSource = interactionSource,
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(placeholder, fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.textMuted.copy(alpha = 0.35f))
                            }
                            inner()
                        }
                    }
                )
            }
            if (isPassword && onTogglePassword != null) {
                Text(
                    text = if (passwordVisible) "◉" else "○",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted.copy(alpha = 0.6f),
                    modifier = Modifier.clickable(role = Role.Button) { onTogglePassword() }
                        .padding(horizontal = Spacing.xs)
                )
            }
            Text(text = "│", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, color = borderColor)
        }

        // Bottom border: └───────────────┘
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text = "└", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, color = cornerColor)
            Box(modifier = Modifier.weight(1f).height(Sizing.strokeThin)
                .background(Brush.horizontalGradient(listOf(
                    borderColor.copy(alpha = 0.8f), borderColor.copy(alpha = 0.1f)
                ))))
            Text(text = "┘", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall, color = cornerColor)
        }
    }
}

/** Pulsing ● dot — pure text, matches SessionCard / SessionListScreen style */
@Composable
private fun ScanPulse() {
    val theme = LocalOpenCodeTheme.current
    val transition = rememberInfiniteTransition(label = "scanPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Text(text = "●", fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.labelSmall, color = theme.accent.copy(alpha = alpha))
}

// ── Unified Hero Header ───────────────────────────────────────────────────

/**
 * Compact hero: top line shared with panel, no dead space.
 *
 *   ◈ P4OC ───────────────────────────── ⚙
 *   └─ connect :: server
 *   (panel starts immediately, ⌜ is the panel's own top frame)
 */
@Composable
private fun ServerHeroHeader(
    theme: OpenCodeTheme,
    visible: Boolean,
    springDp: androidx.compose.animation.core.SpringSpec<Dp>,
    onSettings: () -> Unit
) {
    val hA by animateFloatAsState(if (visible) 1f else 0f,
        tween(260, easing = FastOutSlowInEasing), label = "hdrA")
    val hO by animateDpAsState(if (visible) Spacing.none else (-10).dp,
        springDp, label = "hdrO")

    val infiniteTransition = rememberInfiniteTransition(label = "heroGlow")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse), label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(hA)
            .offset(y = hO)
            .background(Brush.verticalGradient(listOf(
                theme.backgroundElement.copy(alpha = 0.9f),
                theme.backgroundElement.copy(alpha = 0.5f)
            )))
            .padding(horizontal = Spacing.xs)
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        // Title row: ◈ P4OC ──────────── ⚙
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = "◈", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.accent.copy(alpha = glow), fontWeight = FontWeight.Bold)
                Text(text = "P4OC", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold, color = theme.text)
            }
            Text(text = "⚙", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textMuted.copy(alpha = 0.65f),
                modifier = Modifier
                    .clickable(role = Role.Button) { onSettings() }
                    .padding(Spacing.xs)
                    .testTag("server_settings_button"))
        }
        // Breadcrumb row: └─ connect :: server
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(text = "└─", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.accent.copy(alpha = 0.5f))
            Text(text = "connect", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, color = theme.textMuted)
            Text(text = " :: ", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted.copy(alpha = 0.35f))
            Text(text = "server", fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                color = theme.accent.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}
