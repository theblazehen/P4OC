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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
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

    Scaffold(
        topBar = { ServerAsciiTopBar(theme = theme, onSettings = onSettings) },
        containerColor = theme.background
    ) { padding ->
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { delay(30); started = true }
        val smoothSpringDp  = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        val smoothSpringFlt = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        val pageAlpha by animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            label = "page_alpha"
        )
        val pageOffsetY by animateDpAsState(
            targetValue = if (started) Spacing.none else 20.dp,
            animationSpec = smoothSpringDp,
            label = "page_offsetY"
        )
        val pageScale by animateFloatAsState(
            targetValue = if (started) 1f else 0.97f,
            animationSpec = smoothSpringFlt,
            label = "page_scale"
        )

        // Per-section stagger states
        var showDiscover by remember { mutableStateOf(false) }
        var showRecent   by remember { mutableStateOf(false) }
        var showRemote   by remember { mutableStateOf(false) }
        var showHelp     by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(60);  showDiscover = true
            delay(80);  showRecent   = true
            delay(80);  showRemote   = true
            delay(100); showHelp     = true
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.sm, vertical = Spacing.md)
                .alpha(pageAlpha)
                .offset(y = pageOffsetY)
                .graphicsLayer { scaleX = pageScale; scaleY = pageScale },
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {

            if (uiState.discoveredServers.isNotEmpty() || uiState.discoveryState == DiscoveryState.SCANNING) {
                val dAlpha  by animateFloatAsState(if (showDiscover) 1f else 0f,
                    tween(260, easing = FastOutSlowInEasing), label = "dA")
                val dOffset by animateDpAsState(if (showDiscover) Spacing.none else 16.dp,
                    smoothSpringDp, label = "dO")
                val dScale  by animateFloatAsState(if (showDiscover) 1f else 0.96f,
                    smoothSpringFlt, label = "dS")
                Column(modifier = Modifier.alpha(dAlpha).offset(y = dOffset)
                    .graphicsLayer { scaleX = dScale; scaleY = dScale }) {
                    DiscoveredServersSection(
                        servers = uiState.discoveredServers,
                        discoveryState = uiState.discoveryState,
                        isConnecting = uiState.isConnecting,
                        onServerClick = viewModel::connectToDiscoveredServer,
                        onStopClick = viewModel::stopDiscovery
                    )
                }
            }

            if (uiState.recentServers.isNotEmpty()) {
                val rAlpha  by animateFloatAsState(if (showRecent) 1f else 0f,
                    tween(260, easing = FastOutSlowInEasing), label = "rA")
                val rOffset by animateDpAsState(if (showRecent) Spacing.none else 16.dp,
                    smoothSpringDp, label = "rO")
                val rScale  by animateFloatAsState(if (showRecent) 1f else 0.96f,
                    smoothSpringFlt, label = "rS")
                Column(modifier = Modifier.alpha(rAlpha).offset(y = rOffset)
                    .graphicsLayer { scaleX = rScale; scaleY = rScale }) {
                    RecentServersSection(
                        servers = uiState.recentServers,
                        isConnecting = uiState.isConnecting,
                        onServerClick = viewModel::connectToRecentServer,
                        onRemoveServer = viewModel::removeRecentServer
                    )
                }
            }

            val fAlpha  by animateFloatAsState(if (showRemote) 1f else 0f,
                tween(280, easing = FastOutSlowInEasing), label = "fA")
            val fOffset by animateDpAsState(if (showRemote) Spacing.none else 16.dp,
                smoothSpringDp, label = "fO")
            val fScale  by animateFloatAsState(if (showRemote) 1f else 0.96f,
                smoothSpringFlt, label = "fS")
            Column(modifier = Modifier.alpha(fAlpha).offset(y = fOffset)
                .graphicsLayer { scaleX = fScale; scaleY = fScale }) {
                RemoteServerSection(
                    url = uiState.remoteUrl,
                    username = uiState.username,
                    password = uiState.password,
                    isConnecting = uiState.isConnecting,
                    onUrlChange = viewModel::setRemoteUrl,
                    onUsernameChange = viewModel::setUsername,
                    onPasswordChange = viewModel::setPassword,
                    onConnect = viewModel::connectToRemote
                )
            }

            uiState.error?.let { error -> ErrorBanner(error = error) }

            val hAlpha  by animateFloatAsState(if (showHelp) 1f else 0f,
                tween(260, easing = FastOutSlowInEasing), label = "hA")
            val hOffset by animateDpAsState(if (showHelp) Spacing.none else 16.dp,
                smoothSpringDp, label = "hO")
            val hScale  by animateFloatAsState(if (showHelp) 1f else 0.96f,
                smoothSpringFlt, label = "hS")
            Column(modifier = Modifier.alpha(hAlpha).offset(y = hOffset)
                .graphicsLayer { scaleX = hScale; scaleY = hScale }) {
                ServerSetupHelpSection()
            }
        }
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

/** Top frame row:  ⌜─────────────────────────⌝  */
@Composable
private fun TuiTopFrame(accentAlpha: Float = 0.7f) {
    val theme = LocalOpenCodeTheme.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "⌜", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent.copy(alpha = accentAlpha))
        Box(
            modifier = Modifier.weight(1f).height(Sizing.dividerThickness)
                .background(Brush.horizontalGradient(listOf(
                    theme.accent.copy(alpha = accentAlpha * 0.8f),
                    theme.border.copy(alpha = 0.1f),
                    theme.accent.copy(alpha = accentAlpha * 0.8f)
                )))
        )
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

/** Section label row:  ├─[ LABEL ]──────────── (trailing slot optional)  */
@Composable
private fun TuiSectionLabel(label: String, trailing: @Composable (() -> Unit)? = null) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(text = "├─[", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent.copy(alpha = 0.7f))
        Text(text = label, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = theme.accent)
        Text(text = "]", fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = theme.accent.copy(alpha = 0.7f))
        Box(
            modifier = Modifier.weight(1f).height(Sizing.dividerThickness)
                .background(Brush.horizontalGradient(listOf(
                    theme.accent.copy(alpha = 0.4f),
                    theme.border.copy(alpha = 0.05f)
                )))
        )
        trailing?.invoke()
    }
}

// ── Error Banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(error: String) {
    val theme = LocalOpenCodeTheme.current
    Column {
        TuiTopFrame(accentAlpha = 0.0f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.error.copy(alpha = 0.06f))
                .border(Sizing.strokeThin, theme.error.copy(alpha = 0.35f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "✗", color = theme.error, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium)
            Text(text = error, color = theme.error, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        TuiBottomFrame()
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

    Column {
        TuiTopFrame()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundElement.copy(alpha = 0.5f))
                .border(Sizing.strokeThin, theme.border.copy(alpha = 0.3f))
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

            if (servers.isNotEmpty()) {
                servers.forEachIndexed { index, server ->
                    DiscoveredServerItem(server = server, isConnecting = isConnecting,
                        onClick = { onServerClick(server) })
                    if (index < servers.size - 1) TuiGradientLine()
                }
            }
        }
        TuiBottomFrame()
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
    Column {
        TuiTopFrame()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalOpenCodeTheme.current.backgroundElement.copy(alpha = 0.5f))
                .border(Sizing.strokeThin, LocalOpenCodeTheme.current.border.copy(alpha = 0.3f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TuiSectionLabel(label = stringResource(R.string.server_recent_servers))
            servers.forEachIndexed { index, server ->
                RecentServerItem(server = server, isConnecting = isConnecting,
                    onClick = { onServerClick(server) }, onRemove = { onRemoveServer(server) })
                if (index < servers.size - 1) TuiGradientLine()
            }
        }
        TuiBottomFrame()
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

    Column {
        TuiTopFrame()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundElement.copy(alpha = 0.5f))
                .border(Sizing.strokeThin, theme.border.copy(alpha = 0.3f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TuiSectionLabel(label = stringResource(R.string.server_remote_title))

            // Comment line — terminal style
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "#", fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted.copy(alpha = 0.45f))
                Text(text = stringResource(R.string.server_remote_description),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = theme.textMuted)
            }

            TuiGradientLine()

            ModernTextField(
                value = url, onValueChange = onUrlChange,
                label = stringResource(R.string.field_server_url),
                placeholder = stringResource(R.string.field_server_url_placeholder),
                modifier = Modifier.fillMaxWidth().testTag("server_url_input")
            )
            ModernTextField(
                value = username, onValueChange = onUsernameChange,
                label = stringResource(R.string.field_username),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password, onValueChange = onPasswordChange,
                label = {
                    Text(stringResource(R.string.field_password),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(Sizing.radiusNone),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                colors = modernTextFieldColors(theme),
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "◉" else "○",
                        color = theme.textMuted,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .clickable(role = Role.Button) { passwordVisible = !passwordVisible }
                            .padding(Spacing.sm)
                    )
                }
            )

            TuiGradientLine()

            val canConnect = url.isNotBlank() && !isConnecting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizing.buttonHeightLg)
                    .background(if (canConnect) theme.accent else theme.accent.copy(alpha = 0.18f))
                    .then(if (canConnect) Modifier.clickable(role = Role.Button) { onConnect() } else Modifier)
                    .testTag("server_connect_button")
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isConnecting) {
                    TuiLoadingIndicator()
                    Spacer(Modifier.width(Spacing.mdLg))
                    Text(stringResource(R.string.button_connecting),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium, color = theme.background)
                } else {
                    Text("▶  ${stringResource(R.string.button_connect)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canConnect) theme.background else theme.textMuted.copy(alpha = 0.5f))
                }
            }
        }
        TuiBottomFrame()
    }
}

// ── Setup Help Section ────────────────────────────────────────────────────────

@Composable
private fun ServerSetupHelpSection() {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }

    Column {
        TuiTopFrame(accentAlpha = 0.3f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundElement.copy(alpha = 0.35f))
                .border(Sizing.strokeThin, theme.border.copy(alpha = 0.2f))
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
        TuiBottomFrame(dimAlpha = 0.15f)
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

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall)
        },
        placeholder = if (placeholder.isNotEmpty()) ({
            Text(placeholder, fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted.copy(alpha = 0.6f))
        }) else null,
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(Sizing.radiusNone),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        colors = modernTextFieldColors(theme)
    )
}

@Composable
private fun modernTextFieldColors(theme: dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = theme.accent,
        unfocusedBorderColor = theme.border,
        focusedLabelColor = theme.accent,
        unfocusedLabelColor = theme.textMuted,
        cursorColor = theme.accent,
        focusedTextColor = theme.text,
        unfocusedTextColor = theme.text,
        focusedContainerColor = theme.background.copy(alpha = 0.5f),
        unfocusedContainerColor = theme.background.copy(alpha = 0.3f)
    )

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

// ── Unified ASCII Top Bar ──────────────────────────────────────────────────────

/**
 * Fully custom top bar for ServerScreen.
 * One cohesive block: status bar insets → top frame → title row → bottom frame.
 * No Material3 TopAppBar wrapper — pure ASCII art surface.
 *
 * Layout (monospace sketch):
 *   ┌─ surface (backgroundElement) ──────────────────────────────┐
 *   │  [status bar inset spacer]                                  │
 *   │  ⌜────────────────────────────────────────────────────────⌝ │
 *   │  ◈ srv ── connect :: server ──────────────── ── ── ─ ⚙   │
 *   │  ⌞────── ·  ·  ·  ──────────────────────────────────────⌟  │
 *   └─────────────────────────────────────────────────────────────┘
 */
@Composable
private fun ServerAsciiTopBar(
    theme: OpenCodeTheme,
    onSettings: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "topBarAnim")

    // Symbol pulse: ◈ breathes between dim and bright
    val symbolGlow by inf.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "symGlow"
    )

    // Dashes slide: subtle scanning left→right offset for the decorative line
    val dashOffset by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing), RepeatMode.Restart
        ), label = "dashOffset"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = theme.backgroundElement,
        tonalElevation = 0.dp
    ) {
        Column {
            // Status bar inset
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))

            // ── Top frame line ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⌜",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.accent.copy(alpha = symbolGlow * 0.8f)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizing.dividerThickness)
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    dashOffset * 0.0f to theme.accent.copy(alpha = 0.0f),
                                    (dashOffset * 0.5f).coerceIn(0f, 1f) to theme.accent.copy(alpha = symbolGlow * 0.7f),
                                    1f to theme.accent.copy(alpha = 0.0f)
                                )
                            )
                        )
                )
                Text(
                    text = "⌝",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.accent.copy(alpha = symbolGlow * 0.8f)
                )
            }

            // ── Main title row ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ◈ animated icon
                Text(
                    text = "◈",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.accent.copy(alpha = symbolGlow)
                )
                Spacer(Modifier.width(Spacing.xs))
                // Label prefix
                Text(
                    text = "srv",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = theme.accent
                )
                // Dashed separator
                Text(
                    text = " ─",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(Spacing.xs))
                // Title
                Text(
                    text = "connect",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text
                )
                Text(
                    text = " :: ",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted.copy(alpha = 0.45f)
                )
                Text(
                    text = "server",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textMuted.copy(alpha = 0.7f)
                )
                // Spacer with animated scanning dash trail
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.xs)
                        .height(Sizing.dividerThickness)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    theme.border.copy(alpha = 0.0f),
                                    theme.border.copy(alpha = 0.3f),
                                    theme.border.copy(alpha = 0.0f)
                                )
                            )
                        )
                )
                // Settings gear
                Text(
                    text = "⚙",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textMuted.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clickable(role = Role.Button) { onSettings() }
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        .testTag("server_settings_button")
                )
            }

            // ── Bottom frame line ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⌞",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.35f)
                )
                // Scanning dot trail — animated brightness sweep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizing.dividerThickness)
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to theme.border.copy(alpha = 0.1f),
                                    dashOffset.coerceIn(0f, 1f) to theme.accent.copy(alpha = 0.5f),
                                    1f to theme.border.copy(alpha = 0.05f)
                                )
                            )
                        )
                )
                Text(
                    text = "⌟",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.border.copy(alpha = 0.35f)
                )
            }
        }
    }
}
