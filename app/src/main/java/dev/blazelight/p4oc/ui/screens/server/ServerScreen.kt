package dev.blazelight.p4oc.ui.screens.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.SavedServer
import dev.blazelight.p4oc.core.network.DiscoveryState
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.ServerUrl
import dev.blazelight.p4oc.core.network.toServerRef
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiSectionHeader
import dev.blazelight.p4oc.ui.components.TuiSwitch
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.status.serverStatusVisual
import dev.blazelight.p4oc.ui.tabs.TabManager
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
internal fun serverScreen(
    onNavigateToSessions: () -> Unit,
    onSettings: () -> Unit,
    config: ServerScreenConfig = ServerScreenConfig(),
) {
    val viewModel: ServerViewModel = koinViewModel()
    val theme = LocalOpenCodeTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serverConnectionRegistry: ServerConnectionRegistry = koinInject()
    val registryStates = uiState.savedServers.associate { saved ->
        val state by serverConnectionRegistry.connectionState(saved.toServerRef()).collectAsStateWithLifecycle()
        saved.endpointKey to state
    }
    val tabManager: TabManager = koinInject()
    val tabs by tabManager.tabs.collectAsStateWithLifecycle()
    val openTabsByEndpoint = tabs.filterNot { it.isPinnedHome }.groupBy { it.serverEndpointKey }
    val inventory = remember(uiState, registryStates) { buildServerInventory(uiState, registryStates) }
    var showManualForm by rememberSaveable {
        mutableStateOf(
            config.showManualFormInitially ||
                (uiState.savedServers.isEmpty() && uiState.discoveredServers.isEmpty()),
        )
    }
    var editingServerId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(config.autoReconnect) {
        viewModel.start(config.autoReconnect)
    }

    // The TLS toggle lives in the connect form; reveal the form when an attempt fails TLS validation.
    LaunchedEffect(uiState.showTlsOptions) {
        if (uiState.showTlsOptions) showManualForm = true
    }

    // Start/stop mDNS discovery with screen lifecycle
    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    LaunchedEffect(uiState.navigationDestination) {
        when (uiState.navigationDestination) {
            is NavigationDestination.Sessions -> {
                viewModel.clearNavigationDestination()
                onNavigateToSessions()
            }
            is NavigationDestination.Projects -> {
                viewModel.clearNavigationDestination()
                onNavigateToSessions()
            }
            null -> { /* waiting for connection */ }
        }
    }

    serverScaffold(
        presentation = ServerPresentation(
            uiState = uiState,
            inventory = inventory,
            openTabsByEndpoint = openTabsByEndpoint,
            tabManager = tabManager,
            viewModel = viewModel,
            showManualForm = showManualForm,
            onShowManualForm = { showManualForm = true },
            editingServerId = editingServerId,
            onEditServer = { saved ->
                viewModel.prepareSavedServer(saved)
                editingServerId = saved.id
            },
            onDismissEdit = { editingServerId = null },
            onNavigateToSessions = onNavigateToSessions,
            onConnectSavedServer = config.onConnectSavedServer,
        ),
        onSettings = onSettings,
        onNavigateBack = config.onNavigateBack,
    )
}

private data class ServerPresentation(
    val uiState: ServerUiState,
    val inventory: ServerInventory,
    val openTabsByEndpoint: Map<String?, List<dev.blazelight.p4oc.ui.tabs.TabInstance>>,
    val tabManager: TabManager,
    val viewModel: ServerViewModel,
    val showManualForm: Boolean,
    val onShowManualForm: () -> Unit,
    val editingServerId: String?,
    val onEditServer: (SavedServer) -> Unit,
    val onDismissEdit: () -> Unit,
    val onNavigateToSessions: () -> Unit,
    val onConnectSavedServer: ((SavedServer) -> Unit)?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun serverScaffold(
    presentation: ServerPresentation,
    onSettings: () -> Unit,
    onNavigateBack: (() -> Unit)?,
) {
    val theme = LocalOpenCodeTheme.current
    Scaffold(
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.server_connect_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    // Sized to match the home header's settings button (48dp target, 20dp glyph).
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier
                            .size(Sizing.minTouchTarget)
                            .testTag("server_settings_button"),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.server_settings_cd),
                            tint = theme.textMuted,
                            modifier = Modifier.size(Sizing.iconMd),
                        )
                    }
                },
            )
        },
        containerColor = LocalOpenCodeTheme.current.background,
    ) { padding ->
        serverContent(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            presentation,
        )
    }
}

private val serverContent: @Composable (Modifier, ServerPresentation) -> Unit = { modifier, presentation ->
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        val openTabsByEndpoint = presentation.openTabsByEndpoint
        if (presentation.inventory.saved.isNotEmpty()) {
            savedServersSection(
                state = SavedServersState(
                    presentation.inventory.saved,
                    presentation.uiState.isConnecting,
                    openTabsByEndpoint,
                ),
                actions = SavedServerActions(
                    onServerClick = presentation.onConnectSavedServer ?: presentation.viewModel::connectToSavedServer,
                    onEditServer = presentation.onEditServer,
                    onReviewTabs = { saved ->
                        openTabsByEndpoint[saved.endpointKey]?.firstOrNull()?.let {
                            presentation.tabManager.focusTab(it.id)
                        }
                        presentation.onNavigateToSessions()
                    },
                    onCloseTabsAndRemove = { saved ->
                        openTabsByEndpoint[saved.endpointKey].orEmpty().forEach {
                            presentation.tabManager.closeTab(it.id)
                        }
                        presentation.viewModel.removeSavedServer(saved)
                    },
                    onRemoveServer = presentation.viewModel::removeSavedServer,
                ),
            )
        }
        presentation.editingServerId?.let { id ->
            presentation.inventory.saved.firstOrNull { it.server.id == id }?.server?.let { server ->
                savedServerEditor(
                    SavedServerEditorPresentation(
                        server = server,
                        uiState = presentation.uiState,
                        viewModel = presentation.viewModel,
                        openTabCount = openTabsByEndpoint[server.endpointKey].orEmpty().size,
                        onReviewTabs = {
                            openTabsByEndpoint[server.endpointKey]?.firstOrNull()?.let {
                                presentation.tabManager.focusTab(it.id)
                            }
                            presentation.onNavigateToSessions()
                        },
                        onCloseTabsAndRemove = {
                            openTabsByEndpoint[server.endpointKey].orEmpty().forEach {
                                presentation.tabManager.closeTab(it.id)
                            }
                            presentation.viewModel.removeSavedServer(server)
                            presentation.onDismissEdit()
                        },
                        onDismiss = presentation.onDismissEdit,
                    ),
                )
            }
        }
        // The editor already owns a URL/credentials form bound to the same uiState, so the
        // add-a-server section stays hidden until the editor is dismissed.
        if (presentation.editingServerId == null) {
            manualServerSection(
                presentation.uiState,
                presentation.viewModel,
                presentation.showManualForm,
                presentation.onShowManualForm,
            )
        }
        val showDiscovery = presentation.inventory.nearby.isNotEmpty() ||
            presentation.uiState.discoveryState == DiscoveryState.SCANNING
        if (showDiscovery) {
            discoveredServersSection(
                presentation.inventory.nearby,
                presentation.uiState.discoveryState,
                presentation.uiState.isConnecting,
                presentation.viewModel::connectToDiscoveredServer,
            )
        }
        serverFooter(presentation.uiState.error)
    }
}

@Composable
private fun serverFooter(error: String?) {
    serverError(error)
    serverSetupHelpSection()
}

@Composable
private fun manualServerSection(
    uiState: ServerUiState,
    viewModel: ServerViewModel,
    showManualForm: Boolean,
    onShowManualForm: () -> Unit,
) {
    if (!showManualForm) {
        OutlinedButton(
            onClick = onShowManualForm,
            modifier = Modifier.fillMaxWidth().testTag("server_add_button"),
            shape = RectangleShape,
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.server_add), fontFamily = FontFamily.Monospace)
        }
    } else {
        remoteServerSection(
            state = uiState.toRemoteServerState(),
            actions = RemoteServerActions(
                viewModel::setRemoteUrl,
                viewModel::setUsername,
                viewModel::setPassword,
                viewModel::setAllowInsecure,
                viewModel::connectToRemote,
            ),
        )
    }
}

private val serverError: @Composable (String?) -> Unit = { error ->
    val theme = LocalOpenCodeTheme.current
    error?.let {
        Surface(
            color = theme.error.copy(alpha = 0.1f),
            shape = RectangleShape,
            modifier = Modifier.border(Sizing.strokeMd, theme.error.copy(alpha = 0.3f), RectangleShape),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Error, stringResource(R.string.server_status_cd_error), tint = theme.error)
                Text(it, color = theme.error, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

internal fun serverUrlTextFieldValue(url: String): TextFieldValue =
    TextFieldValue(
        text = url,
        selection = TextRange(url.length),
    )

private data class RemoteServerState(
    val url: String,
    val username: String,
    val password: String,
    val allowInsecure: Boolean,
    val isConnecting: Boolean,
    val showTlsOptions: Boolean,
)

private fun ServerUiState.toRemoteServerState() = RemoteServerState(
    url = remoteUrl,
    username = username,
    password = password,
    allowInsecure = allowInsecure,
    isConnecting = isConnecting,
    showTlsOptions = showTlsOptions,
)

private data class RemoteServerActions(
    val onUrlChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onAllowInsecureChange: (Boolean) -> Unit,
    val onConnect: () -> Unit,
)

@Composable
private fun remoteServerSection(
    state: RemoteServerState,
    actions: RemoteServerActions,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    // Keep optional credentials visible so authenticated servers remain quick to configure.
    var showCredentials by rememberSaveable { mutableStateOf(true) }
    var urlFieldValue by remember { mutableStateOf(serverUrlTextFieldValue(state.url)) }

    LaunchedEffect(state.url) {
        if (state.url != urlFieldValue.text) {
            urlFieldValue = serverUrlTextFieldValue(state.url)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        remoteUrlField(
            value = urlFieldValue,
            onValueChange = { value ->
                urlFieldValue = value
                actions.onUrlChange(value.text)
            },
        )
        credentialsSection(
            state = state,
            actions = actions,
            controls = CredentialControls(
                expanded = showCredentials,
                passwordVisible = passwordVisible,
                onToggleExpanded = { showCredentials = !showCredentials },
                onTogglePassword = { passwordVisible = !passwordVisible },
            ),
        )
        connectButton(state = state, onConnect = actions.onConnect)
    }
}

@Composable
private fun remoteUrlField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    val contentDescription = stringResource(R.string.cd_server_url_field)
    Column {
        serverFieldLabel(
            stringResource(R.string.setup_server_url_label),
            stringResource(R.string.setup_field_required),
        )
        Spacer(Modifier.height(Spacing.sm))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizing.buttonHeightLg)
                .background(theme.backgroundPanel, RectangleShape)
                .border(Sizing.strokeMd, theme.primary, RectangleShape)
                .semantics { this.contentDescription = contentDescription }
                .testTag("server_url_input"),
            textStyle = TextStyle(
                color = theme.text,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.xl,
            ),
            singleLine = true,
            cursorBrush = SolidColor(theme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "/",
                        color = theme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = TuiCodeFontSize.xl,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Box(Modifier.weight(1f)) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.field_server_url_placeholder),
                                color = theme.textMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.xl,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                }
            },
        )
    }
}

private data class CredentialControls(
    val expanded: Boolean,
    val passwordVisible: Boolean,
    val onToggleExpanded: () -> Unit,
    val onTogglePassword: () -> Unit,
)

@Composable
private fun credentialsSection(
    state: RemoteServerState,
    actions: RemoteServerActions,
    controls: CredentialControls,
) {
    val theme = LocalOpenCodeTheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizing.buttonHeightLg)
                .background(theme.backgroundPanel, RectangleShape)
                .border(Sizing.strokeMd, theme.backgroundElement, RectangleShape)
                .clickable(role = Role.Button) { controls.onToggleExpanded() }
                .padding(horizontal = Spacing.lg)
                .testTag("server_credentials_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = if (controls.expanded) "▾" else "▸",
                color = theme.secondary,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.lg,
            )
            Text(
                text = stringResource(R.string.server_credentials),
                color = theme.text,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.lg,
                modifier = Modifier.weight(1f),
            )
            if (!controls.expanded) {
                Text(
                    text = if (state.password.isBlank()) {
                        stringResource(
                            R.string.setup_credentials_hint_no_password,
                            state.username.ifBlank { ServerUrl.DEFAULT_USERNAME },
                        )
                    } else {
                        stringResource(
                            R.string.setup_credentials_hint,
                            state.username.ifBlank { ServerUrl.DEFAULT_USERNAME },
                        )
                    },
                    color = theme.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = TuiCodeFontSize.sm,
                )
            }
        }
        AnimatedVisibility(controls.expanded) {
            credentialsFields(state, actions, controls.passwordVisible, controls.onTogglePassword)
        }
    }
}

@Composable
private fun credentialsFields(
    state: RemoteServerState,
    actions: RemoteServerActions,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
) {
    Column(Modifier.padding(top = Spacing.lg)) {
        serverFieldLabel(
            stringResource(R.string.setup_username_label),
            stringResource(R.string.setup_field_default_opencode),
        )
        Spacer(Modifier.height(Spacing.sm))
        serverFieldBox(
            value = state.username,
            onValueChange = actions.onUsernameChange,
            options = ServerFieldOptions(
                testTag = "server_username_input",
                contentDescription = stringResource(R.string.cd_username_field),
            ),
        )
        Spacer(Modifier.height(Spacing.lg))
        serverFieldLabel(
            stringResource(R.string.setup_password_label),
            stringResource(R.string.setup_field_optional),
        )
        Spacer(Modifier.height(Spacing.sm))
        passwordField(state.password, actions.onPasswordChange, passwordVisible, onTogglePassword)
        if (state.showTlsOptions) {
            Spacer(Modifier.height(Spacing.xl))
            serverTlsSection(state.allowInsecure, actions.onAllowInsecureChange)
        }
    }
}

@Composable
private fun serverFieldLabel(label: String, qualifier: String) {
    val theme = LocalOpenCodeTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.md,
            color = theme.textMuted,
        )
        Text(
            text = qualifier,
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.md,
            color = theme.textMuted,
        )
    }
}

@Composable
private fun serverTlsSection(allowInsecure: Boolean, onAllowInsecureChange: (Boolean) -> Unit) {
    val theme = LocalOpenCodeTheme.current
    Text(
        text = stringResource(R.string.setup_tls_label),
        fontFamily = FontFamily.Monospace,
        fontSize = TuiCodeFontSize.sm,
        color = theme.textMuted,
        letterSpacing = 1.sp,
    )
    Spacer(Modifier.height(Spacing.md))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md)
            .testTag("server_allow_insecure_toggle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.field_allow_insecure),
                color = theme.text,
                fontSize = TuiCodeFontSize.xl,
            )
            Text(
                text = stringResource(R.string.setup_tls_skip_verification),
                color = theme.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.md,
            )
        }
        TuiSwitch(checked = allowInsecure, onCheckedChange = onAllowInsecureChange)
    }
    Row(
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("⚠", color = theme.warning, fontFamily = FontFamily.Monospace, fontSize = TuiCodeFontSize.md)
        Text(
            text = stringResource(R.string.setup_tls_warning),
            color = theme.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.md,
        )
    }
}

private data class ServerFieldOptions(
    val testTag: String,
    val contentDescription: String,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
)

@Composable
private fun serverFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    options: ServerFieldOptions,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalOpenCodeTheme.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizing.textFieldHeightSm)
            .background(theme.backgroundPanel, RectangleShape)
            .border(Sizing.strokeMd, theme.borderSubtle, RectangleShape)
            .semantics { contentDescription = options.contentDescription }
            .testTag(options.testTag),
        textStyle = TextStyle(
            color = theme.text,
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.xl,
        ),
        singleLine = true,
        cursorBrush = SolidColor(theme.primary),
        visualTransformation = options.visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = options.keyboardType),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { inner() }
                if (trailing != null) {
                    Spacer(Modifier.width(Spacing.md))
                    trailing()
                }
            }
        },
    )
}

@Composable
private fun passwordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    serverFieldBox(
        value = password,
        onValueChange = onPasswordChange,
        options = ServerFieldOptions(
            testTag = "server_password_input",
            contentDescription = stringResource(R.string.cd_password_field),
            keyboardType = KeyboardType.Password,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        ),
        trailing = {
            Text(
                text = if (passwordVisible) {
                    stringResource(R.string.setup_password_hide)
                } else {
                    stringResource(R.string.setup_password_show)
                },
                color = theme.secondary,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.lg,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onTogglePassword)
                    .testTag("server_password_visibility"),
            )
        },
    )
}

@Composable
private fun connectButton(state: RemoteServerState, onConnect: () -> Unit) {
    val theme = LocalOpenCodeTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizing.buttonHeightLg)
            .background(theme.primary, RectangleShape)
            .clickable(enabled = !state.isConnecting, role = Role.Button) { onConnect() }
            .testTag("server_connect_button"),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isConnecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Sizing.iconXs),
                    color = theme.background,
                    strokeWidth = Sizing.strokeThick,
                )
                Text(
                    text = stringResource(R.string.button_connecting),
                    color = theme.background,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = TuiCodeFontSize.xxl,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.button_connect).uppercase(),
                color = theme.background,
                fontWeight = FontWeight.SemiBold,
                fontSize = TuiCodeFontSize.xxl,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

private data class SavedServersState(
    val servers: List<ServerInventoryEntry>,
    val isConnecting: Boolean,
    val openTabsByEndpoint: Map<String?, List<dev.blazelight.p4oc.ui.tabs.TabInstance>>,
)

private data class SavedServerActions(
    val onServerClick: (SavedServer) -> Unit,
    val onEditServer: (SavedServer) -> Unit,
    val onReviewTabs: (SavedServer) -> Unit,
    val onCloseTabsAndRemove: (SavedServer) -> Unit,
    val onRemoveServer: (SavedServer) -> Unit,
)

@Composable
private fun savedServersSection(
    state: SavedServersState,
    actions: SavedServerActions,
) {
    var pendingForget by remember { mutableStateOf<Pair<SavedServer, Int>?>(null) }
    Column {
        TuiSectionHeader(text = stringResource(R.string.server_saved_servers))
        state.servers.forEach { entry ->
            key(entry.server.id) {
                savedServerRow(
                    entry = entry,
                    isConnecting = state.isConnecting,
                    openTabCount = state.openTabsByEndpoint[entry.server.endpointKey].orEmpty().size,
                    actions = actions,
                    onForget = { server, count -> pendingForget = server to count },
                )
            }
        }
    }
    pendingForget?.let { (server, count) ->
        forgetServerDialog(
            server = server,
            openTabCount = count,
            actions = actions,
            onDismiss = { pendingForget = null },
        )
    }
}

@Composable
private fun savedServerRow(
    entry: ServerInventoryEntry,
    isConnecting: Boolean,
    openTabCount: Int,
    actions: SavedServerActions,
    onForget: (SavedServer, Int) -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    val server = entry.server
    val statusVisual = serverStatusVisual(entry.status)
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.backgroundPanel, RectangleShape)
            .clickable(enabled = !isConnecting, role = Role.Button) {
                actions.onServerClick(server)
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.mdLg),
    ) {
        Box(
            modifier = Modifier
                .size(Sizing.indicatorDotActive)
                .background(statusVisual.color, RectangleShape)
                .semantics { contentDescription = statusVisual.contentDescription },
        )
        savedServerDetails(entry, openTabCount, Modifier.weight(1f))
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("saved_server_actions_${server.id}"),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    stringResource(R.string.server_actions_for, server.displayName),
                    tint = theme.textMuted,
                )
            }
            DropdownMenu(menuExpanded, { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.server_edit)) },
                    onClick = {
                        menuExpanded = false
                        actions.onEditServer(server)
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.server_forget)) },
                    onClick = {
                        menuExpanded = false
                        onForget(server, openTabCount)
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = theme.error) },
                )
            }
        }
    }
}

@Composable
private fun savedServerDetails(
    entry: ServerInventoryEntry,
    openTabCount: Int,
    modifier: Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    val server = entry.server
    Column(modifier) {
        Text(
            server.displayName,
            color = theme.text,
            fontSize = TuiCodeFontSize.xl,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            server.endpoint,
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.md,
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val metaParts = buildList {
            add(
                if (server.username.isNullOrBlank()) {
                    stringResource(R.string.server_auth_default)
                } else {
                    stringResource(R.string.server_auth_configured)
                },
            )
            add(
                if (server.allowInsecure) {
                    stringResource(R.string.server_tls_checks_off)
                } else {
                    stringResource(R.string.server_tls_checks_on)
                },
            )
            if (openTabCount > 0) add(stringResource(R.string.server_open_tabs_count, openTabCount))
        }
        Text(
            metaParts.joinToString(" · "),
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.md,
            color = if (server.allowInsecure) theme.warning else theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun savedServerEditor(presentation: SavedServerEditorPresentation) {
    val server = presentation.server
    val uiState = presentation.uiState
    val viewModel = presentation.viewModel
    val theme = LocalOpenCodeTheme.current
    var confirmRemoval by remember { mutableStateOf(false) }
    Surface(color = theme.backgroundElement, shape = RectangleShape) {
        savedServerEditorForm(presentation) { confirmRemoval = true }
    }
    if (confirmRemoval) {
        savedServerRemovalDialog(presentation) { confirmRemoval = false }
    }
}

@Composable
private fun savedServerEditorForm(presentation: SavedServerEditorPresentation, onRemove: () -> Unit) {
    val server = presentation.server
    val uiState = presentation.uiState
    val viewModel = presentation.viewModel
    val theme = LocalOpenCodeTheme.current
    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                server.displayName,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = theme.text,
            )
            IconButton(onClick = presentation.onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.server_close_details))
            }
        }
        remoteServerSection(
            uiState.toRemoteServerState(),
            RemoteServerActions(
                viewModel::setRemoteUrl,
                viewModel::setUsername,
                viewModel::setPassword,
                viewModel::setAllowInsecure,
                viewModel::connectToRemote,
            ),
        )
        OutlinedButton(
            onClick = { viewModel.saveSavedServer(server) },
            enabled = uiState.remoteUrl.isNotBlank() && !uiState.isConnecting,
            modifier = Modifier.fillMaxWidth().testTag("saved_server_save"),
            shape = RectangleShape,
        ) {
            Icon(Icons.Default.Save, null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.file_editor_save), fontFamily = FontFamily.Monospace)
        }
        TextButton(onRemove, Modifier.fillMaxWidth().testTag("saved_server_detail_forget")) {
            Text(stringResource(R.string.server_forget), color = theme.error)
        }
    }
}

@Composable
private fun savedServerRemovalDialog(presentation: SavedServerEditorPresentation, onDismiss: () -> Unit) {
    val server = presentation.server
    val viewModel = presentation.viewModel
    forgetServerDialog(
        server = server,
        openTabCount = presentation.openTabCount,
        actions = SavedServerActions(
            onServerClick = {},
            onEditServer = {},
            onReviewTabs = { presentation.onReviewTabs() },
            onCloseTabsAndRemove = { presentation.onCloseTabsAndRemove() },
            onRemoveServer = {
                viewModel.removeSavedServer(server)
                presentation.onDismiss()
            },
        ),
        onDismiss = onDismiss,
    )
}

private data class SavedServerEditorPresentation(
    val server: SavedServer,
    val uiState: ServerUiState,
    val viewModel: ServerViewModel,
    val openTabCount: Int,
    val onReviewTabs: () -> Unit,
    val onCloseTabsAndRemove: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
private fun forgetServerDialog(
    server: SavedServer,
    openTabCount: Int,
    actions: SavedServerActions,
    onDismiss: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    if (openTabCount == 0) {
        TuiConfirmDialog(
            onDismissRequest = onDismiss,
            onConfirm = {
                onDismiss()
                actions.onRemoveServer(server)
            },
            title = stringResource(R.string.server_forget_title, server.displayName),
            message = stringResource(R.string.server_forget_message_no_tabs),
            confirmText = stringResource(R.string.server_forget),
            dismissText = stringResource(R.string.button_cancel),
            isDestructive = true,
            modifier = Modifier.testTag("saved_server_forget_dialog"),
        )
    } else {
        TuiAlertDialog(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.server_forget_title, server.displayName),
            modifier = Modifier.testTag("saved_server_forget_dialog"),
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        actions.onReviewTabs(server)
                    },
                    modifier = Modifier.testTag("server_review_tabs"),
                ) { Text(stringResource(R.string.server_review_tabs)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        actions.onCloseTabsAndRemove(server)
                    },
                    modifier = Modifier.testTag("server_close_tabs_forget"),
                ) { Text(stringResource(R.string.server_close_tabs_forget), color = theme.error) }
            },
        ) {
            Text(stringResource(R.string.server_forget_message, openTabCount))
        }
    }
}
