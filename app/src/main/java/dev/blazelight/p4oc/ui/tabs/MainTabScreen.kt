@file:Suppress(
    "DEPRECATION", // LocalLifecycleOwner – platform version until lifecycle-runtime-compose upgrade
    "TooManyFunctions",
)

package dev.blazelight.p4oc.ui.tabs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.SavedServer
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionState
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.core.network.toServerRef
import dev.blazelight.p4oc.core.notification.NotificationRoute
import dev.blazelight.p4oc.data.remote.dto.CreatePtyRequest
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.session.SessionRepositoryProvider
import dev.blazelight.p4oc.data.session.presence
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.SessionConnectionState
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.ui.navigation.Screen
import dev.blazelight.p4oc.ui.screens.home.HomeActions
import dev.blazelight.p4oc.ui.screens.home.HomeSummaryBuilder
import dev.blazelight.p4oc.ui.screens.home.HomeSummaryInput
import dev.blazelight.p4oc.ui.screens.home.ScopedHomeRepositoryState
import dev.blazelight.p4oc.ui.screens.home.homeScreen
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiShapes
import dev.blazelight.p4oc.ui.workspace.WorkspaceRepositoryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val TAG = "MainTabScreen"

private data class SavedServerView(
    val endpointKey: String,
    val displayName: String,
    val badgeLabel: String,
)

private data class MainTabDeps(
    val tabManager: TabManager,
    val settingsDataStore: SettingsDataStore,
    val serverConnectionRegistry: ServerConnectionRegistry,
    val sessionRepositoryProvider: SessionRepositoryProvider,
    val coroutineScope: CoroutineScope,
)

private class StartWorkUiState {
    var restoreError: String? by mutableStateOf(null)
    var startWorkContext: StartWorkContext? by mutableStateOf(null)
    var showStartWorkSheet: Boolean by mutableStateOf(false)
    var showFilesTabPrompt: Boolean by mutableStateOf(false)
    var showStartWorkPicker: Boolean by mutableStateOf(false)
    var homeDetailSelection: StartWorkSelection by mutableStateOf(StartWorkSelection.NeedsSelection)
    var pendingStartWork: Pair<StartWorkTarget, StartWorkAction>? by mutableStateOf(null)
    var pickerSelectedEndpointKey: String? by mutableStateOf(null)
    var pickerSearchQuery: String by mutableStateOf("")
}

internal enum class PendingStartDisposition {
    WaitForConnection,
    Run,
    SavedServerMissing,
    ConnectionFailed,
    ApiUnavailable,
}

internal fun pendingStartDisposition(
    savedServerExists: Boolean,
    connectionState: ConnectionState?,
    apiAvailable: Boolean,
): PendingStartDisposition = when {
    !savedServerExists -> PendingStartDisposition.SavedServerMissing
    connectionState is ConnectionState.Error -> PendingStartDisposition.ConnectionFailed
    connectionState !is ConnectionState.Connected -> PendingStartDisposition.WaitForConnection
    !apiAvailable -> PendingStartDisposition.ApiUnavailable
    else -> PendingStartDisposition.Run
}

private val startWorkPickerSearch: @Composable (StartWorkUiState) -> Unit = { uiState ->
    val theme = LocalOpenCodeTheme.current
    val searchDescription = stringResource(R.string.start_work_filter_workspaces)
    BasicTextField(
        value = uiState.pickerSearchQuery,
        onValueChange = { uiState.pickerSearchQuery = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
        cursorBrush = SolidColor(theme.accent),
        modifier = Modifier
            .fillMaxWidth()
            .border(Sizing.strokeThin, theme.border, RectangleShape)
            .semantics { contentDescription = searchDescription }
            .testTag("start_work_search_field"),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("/", style = MaterialTheme.typography.labelMedium, color = theme.textMuted)
                Spacer(Modifier.width(Spacing.xs))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    field()
                    if (uiState.pickerSearchQuery.isEmpty()) {
                        Text(
                            stringResource(R.string.start_work_filter_workspaces),
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    )
}

private val startWorkServerRail: @Composable (
    List<StartWorkPickerGroup>,
    StartWorkPickerGroup?,
    StartWorkUiState,
) -> Unit = { groups, selectedGroup, uiState ->
    val theme = LocalOpenCodeTheme.current
    val resources = LocalResources.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items(groups, key = { it.server.endpointKey }) { group ->
            val selected = group.server.endpointKey == selectedGroup?.server?.endpointKey
            Surface(
                color = theme.backgroundPanel,
                shape = RectangleShape,
                modifier = Modifier
                    .width(Sizing.serverFilterCardWidth)
                    .then(if (selected) Modifier.border(Sizing.strokeMd, theme.primary) else Modifier)
                    .clickable(role = Role.Tab) {
                        uiState.pickerSelectedEndpointKey = group.server.endpointKey
                        uiState.pickerSearchQuery = ""
                    }
                    .semantics {
                        contentDescription = resources.getString(
                            R.string.start_work_server_workspaces,
                            group.server.displayName,
                            group.targets.size - 1,
                        )
                        this.selected = selected
                    }
                    .testTag("start_work_server_${group.server.endpointKey}"),
            ) {
                Column(
                    Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                ) {
                    Text(
                        group.badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) theme.primary else theme.text,
                    )
                    Text(
                        group.server.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val startWorkPickerLedger: @Composable ColumnScope.(
    MainTabContentParams,
    List<StartWorkTarget>,
) -> Unit = { params, targets ->
    val labels = rememberTabTitleLabels()
    targets.firstOrNull { it.workspaceKey == WorkspaceKey.Global }?.let { globalTarget ->
        filesWorkspaceOption(
            title = stringResource(R.string.sessions_global),
            subtitle = workspaceSubtitle(globalTarget.workspaceKey),
            marker = "◆",
            onClick = { selectStartWorkPickerTarget(params, globalTarget) },
            modifier = Modifier.testTag("start_work_target_global"),
        )
    }
    val directories = targets.filter { it.workspaceKey != WorkspaceKey.Global }
    if (directories.isEmpty()) {
        Text(
            stringResource(R.string.start_work_no_matching_workspaces),
            style = MaterialTheme.typography.labelMedium,
            color = LocalOpenCodeTheme.current.textMuted,
            modifier = Modifier.padding(vertical = Spacing.md),
        )
    } else {
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Spacing.hairline),
        ) {
            items(
                items = directories,
                key = { target -> "target:${target.serverRef.endpointKey}:${target.workspaceKey}" },
            ) { target ->
                filesWorkspaceOption(
                    title = workspaceLabel(target.workspaceKey, labels) ?: workspaceSubtitle(target.workspaceKey),
                    subtitle = workspaceSubtitle(target.workspaceKey),
                    marker = "◇",
                    onClick = { selectStartWorkPickerTarget(params, target) },
                    modifier = Modifier.testTag("start_work_target_${target.workspaceKey}"),
                )
            }
            item(key = "picker_navigation_bar") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

internal data class StartWorkPickerGroup(
    val server: ServerRef,
    val badgeLabel: String,
    val targets: List<StartWorkTarget>,
)

internal fun buildStartWorkPickerGroups(
    servers: List<Triple<String, String, String>>,
    openTargets: List<StartWorkTarget>,
    knownHomeTargets: List<StartWorkTarget>,
): List<StartWorkPickerGroup> = servers.map { (endpointKey, displayName, badgeLabel) ->
    val server = ServerRef.fromEndpointKey(endpointKey, displayName)
    val directoryTargets = (knownHomeTargets + openTargets)
        .filter { it.serverRef.endpointKey == endpointKey && it.workspaceKey != WorkspaceKey.Global }
        .distinctBy { it.workspaceKey }
    StartWorkPickerGroup(
        server = server,
        badgeLabel = badgeLabel,
        targets = listOf(StartWorkTarget(server, WorkspaceKey.Global)) + directoryTargets,
    )
}

internal val startWorkScopedActionOrder = listOf(
    StartWorkAction.NewChat,
    StartWorkAction.Files,
    StartWorkAction.Terminal,
)

private class TabStateMaps(
    val connectionStates: SnapshotStateMap<String, SessionConnectionState>,
    val readTokens: SnapshotStateMap<String, Long>,
    val routes: SnapshotStateMap<String, String>,
    val ptyIds: SnapshotStateMap<String, String>,
    val workspaceOwners: SnapshotStateMap<String, WorkspaceRepositoryOwner>,
)

private data class MainTabContentParams(
    val deps: MainTabDeps,
    val uiState: StartWorkUiState,
    val tabMaps: TabStateMaps,
    val tabs: List<TabInstance>,
    val activeTabId: String?,
    val savedServers: List<SavedServer>,
    val savedServerViews: List<SavedServerView>,
    val scopedConnectionStates: Map<String, ConnectionState>,
    val homeRepositoryStates: List<ScopedHomeRepositoryState>,
    val closeTab: (String) -> Unit,
    val savedServerExists: (String) -> Boolean,
    val connectSavedServer: (String) -> Unit,
    val onDisconnect: () -> Unit,
)

@Composable
private fun rememberMainTabDeps(): MainTabDeps {
    val tabManager: TabManager = koinInject()
    val settingsDataStore: SettingsDataStore = koinInject()
    val serverConnectionRegistry: ServerConnectionRegistry = koinInject()
    val sessionRepositoryProvider: SessionRepositoryProvider = koinInject()
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) {
        MainTabDeps(
            tabManager = tabManager,
            settingsDataStore = settingsDataStore,
            serverConnectionRegistry = serverConnectionRegistry,
            sessionRepositoryProvider = sessionRepositoryProvider,
            coroutineScope = coroutineScope,
        )
    }
}

private val rememberStartWorkUiState: @Composable () -> StartWorkUiState = {
    remember { StartWorkUiState() }
}

private val rememberTabStateMaps: @Composable () -> TabStateMaps = {
    remember {
        TabStateMaps(
            connectionStates = mutableStateMapOf(),
            readTokens = mutableStateMapOf(),
            routes = mutableStateMapOf(),
            ptyIds = mutableStateMapOf(),
            workspaceOwners = mutableStateMapOf(),
        )
    }
}

private val rememberScopedConnectionStates: @Composable (
    List<SavedServer>,
    ServerConnectionRegistry,
) -> Map<String, ConnectionState> = { savedServers, registry ->
    savedServers.associate { saved ->
        val serverRef = ServerRef.fromEndpointKey(saved.endpointKey, saved.displayName)
        val state by registry.connectionState(serverRef).collectAsStateWithLifecycle()
        saved.endpointKey to state
    }
}

private val rememberSavedServerExists: @Composable (List<SavedServer>) -> (String) -> Boolean =
    { savedServers ->
        remember(savedServers) {
            fun(endpointKey: String): Boolean {
                return savedServers.any { it.endpointKey == endpointKey }
            }
        }
    }

private val rememberConnectSavedServer: @Composable (
    ServerConnectionRegistry,
    List<SavedServer>,
) -> (String) -> Unit = { registry, savedServers ->
    remember(registry, savedServers) {
        fun(endpointKey: String) {
            savedServers.firstOrNull { it.endpointKey == endpointKey }?.let(registry::connect)
        }
    }
}

private val mainTabForegroundEffect: @Composable (ServerConnectionRegistry, LifecycleOwner) -> Unit =
    { serverConnectionRegistry, lifecycleOwner ->
        DisposableEffect(serverConnectionRegistry, lifecycleOwner) {
            var wasStopped = false
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> wasStopped = true
                    Lifecycle.Event.ON_START -> if (wasStopped) {
                        wasStopped = false
                        serverConnectionRegistry.onAppForegrounded()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

@Composable
private fun mainTabRestoreEffect(
    deps: MainTabDeps,
    savedServers: List<SavedServer>,
    uiState: StartWorkUiState,
) {
    LaunchedEffect(savedServers) {
        if (!deps.tabManager.shouldAttemptRestore()) return@LaunchedEffect
        val persisted = deps.settingsDataStore.getPersistedTabState()
        if (persisted != null) {
            val availableServers = savedServers.associate { saved ->
                saved.endpointKey to ServerRef.fromEndpointKey(saved.endpointKey, saved.displayName)
            }
            when (val result = deps.tabManager.restoreState(persisted, availableServers)) {
                is RestoreResult.Restored -> AppLog.d(TAG, "Restored ${result.count} tabs")
                RestoreResult.Empty -> AppLog.w(TAG, "Persisted tab state was empty")
                is RestoreResult.VersionMismatch ->
                    uiState.restoreError = "Saved tabs use unsupported version ${result.version}. Starting fresh."
                is RestoreResult.ServerMismatch ->
                    uiState.restoreError = "Saved tabs reference a different server. Starting fresh."
                is RestoreResult.MissingServer ->
                    uiState.restoreError = "Saved tabs reference unavailable server ${result.endpointKey}."
            }
        }
        if (!deps.tabManager.hasTabs()) deps.tabManager.ensureHomeTab(focus = true)
    }
}

@Composable
private fun mainTabPersistEffect(
    deps: MainTabDeps,
    tabs: List<TabInstance>,
    activeTabId: String?,
) {
    LaunchedEffect(tabs, activeTabId) {
        deps.settingsDataStore.setPersistedTabState(deps.tabManager.saveState())
    }
}

private val savedServerConnectionEffect: @Composable (ServerConnectionRegistry, List<SavedServer>) -> Unit =
    { registry, savedServers ->
        val endpointKeys = savedServers.map(SavedServer::endpointKey)
        LaunchedEffect(endpointKeys) {
            savedServers.forEach { server ->
                if (registry.connectionState(server.toServerRef()).value is ConnectionState.Disconnected) {
                    registry.connect(server)
                }
            }
        }
    }

@Composable
private fun mainTabWorkspaceOwnersEffect(
    deps: MainTabDeps,
    tabs: List<TabInstance>,
    savedServers: List<SavedServer>,
    scopedConnectionStates: Map<String, ConnectionState>,
    workspaceOwners: SnapshotStateMap<String, WorkspaceRepositoryOwner>,
) {
    DisposableEffect(Unit) {
        onDispose {
            workspaceOwners.values.forEach { it.close() }
            workspaceOwners.clear()
        }
    }
    val tabOwnerInputs = tabs.mapNotNull { tab ->
        val serverRef = tab.serverRef ?: return@mapNotNull null
        val workspaceKey = tab.workspaceKey ?: return@mapNotNull null
        val connection by deps.serverConnectionRegistry.connection(serverRef).collectAsStateWithLifecycle()
        val generation = deps.serverConnectionRegistry.generation(serverRef)
        TabOwnerInput(tab.id, serverRef, workspaceKey, connection != null, generation)
    }
    val homeOwnerInputs = savedServers.mapNotNull { saved ->
        if (scopedConnectionStates[saved.endpointKey] !is ConnectionState.Connected) {
            return@mapNotNull null
        }
        val serverRef = ServerRef.fromEndpointKey(saved.endpointKey, saved.displayName)
        TabOwnerInput(
            tabId = "home:${saved.endpointKey}",
            serverRef = serverRef,
            workspaceKey = WorkspaceKey.Global,
            connected = true,
            generation = deps.serverConnectionRegistry.generation(serverRef),
        )
    }
    val ownerInputs = tabOwnerInputs + homeOwnerInputs
    LaunchedEffect(ownerInputs) {
        reconcileWorkspaceOwners(deps, ownerInputs, workspaceOwners)
    }
}

private data class TabOwnerInput(
    val tabId: String,
    val serverRef: ServerRef,
    val workspaceKey: WorkspaceKey,
    val connected: Boolean,
    val generation: dev.blazelight.p4oc.domain.server.ServerGeneration?,
)

private val reconcileWorkspaceOwners: (
    MainTabDeps,
    List<TabOwnerInput>,
    SnapshotStateMap<String, WorkspaceRepositoryOwner>,
) -> Unit = { deps, inputs, owners ->
    val liveTabIds = inputs.map { it.tabId }.toSet()
    owners.keys.filter { it !in liveTabIds }.forEach { id -> owners.remove(id)?.close() }
    inputs.forEach { input -> reconcileWorkspaceOwner(deps, input, owners) }
}

private fun reconcileWorkspaceOwner(
    deps: MainTabDeps,
    input: TabOwnerInput,
    owners: SnapshotStateMap<String, WorkspaceRepositoryOwner>,
) {
    val generation = input.generation
    if (!input.connected || generation == null) {
        owners.remove(input.tabId)?.close()
        return
    }
    val workspace = Workspace(
        server = input.serverRef,
        directory = (input.workspaceKey as? WorkspaceKey.Directory)?.value,
    )
    val current = owners[input.tabId]
    if (current?.workspace == workspace && current.generation == generation) return
    val newOwner = WorkspaceRepositoryOwner(
        tabId = input.tabId,
        workspace = workspace,
        generation = generation,
        sessionRepositoryProvider = deps.sessionRepositoryProvider,
    )
    current?.close()
    owners[input.tabId] = newOwner
}

@Composable
private fun mainTabPresenceCollection(
    tabs: List<TabInstance>,
    activeTabId: String?,
    tabMaps: TabStateMaps,
) {
    tabs.forEach { tab ->
        val sessionId = tab.sessionId
        val workspaceOwner = tabMaps.workspaceOwners[tab.id]
        if (sessionId != null && workspaceOwner != null) {
            val sessionState by workspaceOwner.sessionRepository
                .sessionUiState(SessionId(sessionId))
                .collectAsStateWithLifecycle()
            LaunchedEffect(tab.id, activeTabId, sessionState.responseCompletedToken) {
                if (tab.id == activeTabId) {
                    tabMaps.readTokens[tab.id] = sessionState.responseCompletedToken
                }
            }
            LaunchedEffect(tab.id, sessionState) {
                val readToken = tabMaps.readTokens[tab.id] ?: sessionState.responseCompletedToken
                val hasUnread = sessionState.responseCompletedToken > readToken &&
                    sessionState.status !is SessionStatus.Busy
                tabMaps.connectionStates[tab.id] = sessionState.presence(hasUnread = hasUnread)
            }
        } else {
            val tabSessionState by tab.connectionState.collectAsStateWithLifecycle()
            LaunchedEffect(tab.id, tabSessionState) {
                val currentState = tabSessionState
                if (currentState != null) {
                    tabMaps.connectionStates[tab.id] = currentState
                } else {
                    tabMaps.connectionStates.remove(tab.id)
                    tabMaps.readTokens.remove(tab.id)
                }
            }
        }
    }
}

@Composable
private fun rememberCloseTab(
    deps: MainTabDeps,
    tabMaps: TabStateMaps,
): (String) -> Unit = remember(deps) {
    fun(tabId: String) {
        deps.coroutineScope.launch {
            val route = tabMaps.routes[tabId]
            if (route != null && route.startsWith("terminal/")) {
                val ptyId = tabMaps.ptyIds[tabId]
                if (ptyId != null) {
                    val owner = tabMaps.workspaceOwners[tabId]
                    val api = owner?.let {
                        deps.serverConnectionRegistry.api(it.workspace.server, it.generation)
                    }
                    if (api != null) {
                        val result = safeApiCall {
                            api.deletePtySession(
                                id = ptyId,
                                directory = owner.workspace.directory,
                                workspace = null,
                            )
                        }
                        if (result is ApiResult.Error) {
                            AppLog.e(TAG, "Failed to delete PTY")
                        }
                    }
                }
            }
            tabMaps.routes.remove(tabId)
            tabMaps.ptyIds.remove(tabId)
            tabMaps.connectionStates.remove(tabId)
            deps.tabManager.closeTab(tabId)
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList")
private fun mainTabPendingStartWorkEffect(
    deps: MainTabDeps,
    uiState: StartWorkUiState,
    scopedConnectionStates: Map<String, ConnectionState>,
    savedServerExists: (String) -> Boolean,
    connectSavedServer: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(uiState.pendingStartWork, scopedConnectionStates) {
        val pending = uiState.pendingStartWork ?: return@LaunchedEffect
        val target = pending.first
        val action = pending.second
        val endpointKey = target.serverRef.endpointKey
        val connectionState = scopedConnectionStates[endpointKey]
        val api = if (connectionState is ConnectionState.Connected) {
            deps.serverConnectionRegistry.api(target.serverRef)
        } else {
            null
        }
        when (pendingStartDisposition(savedServerExists(endpointKey), connectionState, api != null)) {
            PendingStartDisposition.WaitForConnection -> return@LaunchedEffect
            PendingStartDisposition.Run -> Unit
            PendingStartDisposition.SavedServerMissing -> {
                uiState.pendingStartWork = null
                deps.coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "This saved server is no longer available. Choose a server and workspace again.",
                        duration = SnackbarDuration.Long,
                        withDismissAction = true,
                    )
                }
                return@LaunchedEffect
            }
            PendingStartDisposition.ConnectionFailed,
            PendingStartDisposition.ApiUnavailable,
            -> {
                uiState.pendingStartWork = null
                deps.coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Could not connect to this server. Check its settings and try again.",
                        actionLabel = "Retry",
                        duration = SnackbarDuration.Indefinite,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed && savedServerExists(endpointKey)) {
                        uiState.pendingStartWork = target to action
                        connectSavedServer(endpointKey)
                    }
                }
                return@LaunchedEffect
            }
        }
        checkNotNull(api)
        when (action) {
            StartWorkAction.NewChat -> {
                val result = safeApiCall {
                    api.createSession(
                        directory = (target.workspaceKey as? WorkspaceKey.Directory)?.value,
                        workspace = null,
                        request = CreateSessionRequest(),
                    )
                }
                when (result) {
                    is ApiResult.Success -> {
                        uiState.pendingStartWork = null
                        deps.tabManager.createTab(
                            startRoute = Screen.Chat.createRoute(result.data.id),
                            workspaceKey = target.workspaceKey,
                            serverRef = target.serverRef,
                            focus = true,
                        )
                    }
                    is ApiResult.Error -> {
                        uiState.pendingStartWork = null
                        deps.coroutineScope.launch {
                            val retry = snackbarHostState.showSnackbar(
                                message = "Could not create the session. Check the connection and try again.",
                                actionLabel = "Retry",
                                duration = SnackbarDuration.Indefinite,
                                withDismissAction = true,
                            )
                            if (retry == SnackbarResult.ActionPerformed && savedServerExists(endpointKey)) {
                                uiState.pendingStartWork = target to action
                            }
                        }
                    }
                }
            }
            StartWorkAction.Terminal -> {
                val result = safeApiCall {
                    api.createPtySession(
                        directory = (target.workspaceKey as? WorkspaceKey.Directory)?.value,
                        workspace = null,
                        request = createPtyRequestForWorkspace(target.workspaceKey),
                    )
                }
                when (result) {
                    is ApiResult.Success -> {
                        uiState.pendingStartWork = null
                        deps.tabManager.createTab(
                            startRoute = Screen.Terminal.createRoute(result.data.id),
                            workspaceKey = target.workspaceKey,
                            serverRef = target.serverRef,
                            focus = true,
                        )
                    }
                    is ApiResult.Error -> {
                        uiState.pendingStartWork = null
                        deps.coroutineScope.launch {
                            val retry = snackbarHostState.showSnackbar(
                                message = "Could not start the terminal. Check the connection and try again.",
                                actionLabel = "Retry",
                                duration = SnackbarDuration.Indefinite,
                                withDismissAction = true,
                            )
                            if (retry == SnackbarResult.ActionPerformed && savedServerExists(endpointKey)) {
                                uiState.pendingStartWork = target to action
                            }
                        }
                    }
                }
            }
            else -> uiState.pendingStartWork = null
        }
    }
}

@Composable
private fun mainTabSnackbarEffects(
    deps: MainTabDeps,
    showTabWarning: Boolean,
    uiState: StartWorkUiState,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(showTabWarning) {
        if (showTabWarning) {
            snackbarHostState.showSnackbar(
                message = resources.getString(R.string.tabs_performance_warning),
                duration = SnackbarDuration.Short,
            )
            deps.tabManager.dismissTabWarning()
        }
    }
    LaunchedEffect(uiState.restoreError) {
        uiState.restoreError?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
            uiState.restoreError = null
        }
    }
    LaunchedEffect(deps.serverConnectionRegistry) {
        deps.serverConnectionRegistry.scopedEvents.collect { scopedEvent ->
            when (val event = scopedEvent.event) {
                is OpenCodeEvent.InstallationUpdateAvailable -> snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.server_update_available, event.version),
                    duration = SnackbarDuration.Long,
                )
                is OpenCodeEvent.InstallationUpdated -> snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.server_updated, event.version),
                    duration = SnackbarDuration.Short,
                )
                else -> Unit
            }
        }
    }
}

object MainTabScreen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    @Suppress("LongMethod")
    operator fun invoke(
        pendingNotificationRoute: StateFlow<NotificationRoute?>,
        onNotificationRouteConsumed: (NotificationRoute) -> Unit,
        onDisconnect: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val deps = rememberMainTabDeps()
        val uiState = rememberStartWorkUiState()
        val tabMaps = rememberTabStateMaps()
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(Unit) { deps.tabManager.ensureHomeTab(focus = false) }
        mainTabForegroundEffect(deps.serverConnectionRegistry, lifecycleOwner)

        val tabs by deps.tabManager.tabs.collectAsStateWithLifecycle()
        val activeTabId by deps.tabManager.activeTabId.collectAsStateWithLifecycle()
        val showTabWarning by deps.tabManager.showTabWarning.collectAsStateWithLifecycle()
        val savedServers by deps.settingsDataStore.savedServers.collectAsStateWithLifecycle(
            initialValue = emptyList(),
        )
        val scopedConnectionStates = rememberScopedConnectionStates(
            savedServers,
            deps.serverConnectionRegistry,
        )
        val savedServerViews = remember(savedServers) {
            savedServers.map { SavedServerView(it.endpointKey, it.displayName, it.badgeLabel) }
        }
        val savedServerExists = rememberSavedServerExists(savedServers)
        val connectSavedServer = rememberConnectSavedServer(deps.serverConnectionRegistry, savedServers)

        val notificationRoute by pendingNotificationRoute.collectAsStateWithLifecycle()
        LaunchedEffect(notificationRoute, savedServers) {
            val route = notificationRoute ?: return@LaunchedEffect
            val ownedServer = findSavedServerForNotification(route, savedServers)
            if (ownedServer != null) {
                val existing = deps.tabManager.findTabByNotificationRoute(route)
                if (existing != null) {
                    deps.tabManager.focusTab(existing.id)
                } else {
                    deps.tabManager.createTab(
                        startRoute = Screen.Chat.createRoute(route.sessionId),
                        workspaceKey = route.workspaceKey,
                        serverRef = ServerRef.fromEndpointKey(ownedServer.endpointKey, ownedServer.displayName),
                        focus = true,
                    )
                }
            }
            // Missing/removed servers safely fall back to the current screen; never guess another owner.
            onNotificationRouteConsumed(route)
        }

        mainTabRestoreEffect(deps, savedServers, uiState)
        savedServerConnectionEffect(deps.serverConnectionRegistry, savedServers)
        mainTabPersistEffect(deps, tabs, activeTabId)
        mainTabWorkspaceOwnersEffect(
            deps,
            tabs,
            savedServers,
            scopedConnectionStates,
            tabMaps.workspaceOwners,
        )
        mainTabPresenceCollection(tabs, activeTabId, tabMaps)

        val homeRepositoryStates = tabMaps.workspaceOwners.values
            .distinctBy { it.workspace.server.endpointKey to it.workspace.key }
            .map { owner ->
                val state by owner.sessionRepository.state.collectAsStateWithLifecycle()
                ScopedHomeRepositoryState(owner.workspace.server, state)
            }

        val closeTab = rememberCloseTab(deps, tabMaps)
        val snackbarHostState = remember { SnackbarHostState() }
        mainTabPendingStartWorkEffect(
            deps,
            uiState,
            scopedConnectionStates,
            savedServerExists,
            connectSavedServer,
            snackbarHostState,
        )
        mainTabSnackbarEffects(deps, showTabWarning, uiState, snackbarHostState)

        val params = MainTabContentParams(
            deps = deps,
            uiState = uiState,
            tabMaps = tabMaps,
            tabs = tabs,
            activeTabId = activeTabId,
            savedServers = savedServers,
            savedServerViews = savedServerViews,
            scopedConnectionStates = scopedConnectionStates,
            homeRepositoryStates = homeRepositoryStates,
            closeTab = closeTab,
            savedServerExists = savedServerExists,
            connectSavedServer = connectSavedServer,
            onDisconnect = onDisconnect,
        )
        mainTabScaffold(params, snackbarHostState, modifier)
        startWorkSheets(params)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun mainTabScaffold(
    params: MainTabContentParams,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    val tabTitleLabels = rememberTabTitleLabels()
    val tabTitles = params.tabs.associate { it.id to getTitleForTab(it, tabTitleLabels) }
    val tabIcons = params.tabs.associate { it.id to getIconForTab(it) }
    val pagerState = rememberPagerState(
        initialPage = params.tabs.indexOfFirst { it.id == params.activeTabId }.coerceAtLeast(0),
        pageCount = { params.tabs.size },
    )
    LaunchedEffect(params.activeTabId, params.tabs.size) {
        val index = params.tabs.indexOfFirst { it.id == params.activeTabId }
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        params.tabs.getOrNull(pagerState.settledPage)?.let { tab ->
            if (tab.id != params.activeTabId) params.deps.tabManager.focusTab(tab.id)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = theme.background,
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding)
                .statusBarsPadding().consumeWindowInsets(WindowInsets.statusBars),
        ) {
            TabBar(
                tabs = params.tabs,
                activeTabId = params.activeTabId,
                tabTitles = tabTitles,
                tabIcons = tabIcons,
                tabConnectionStates = params.tabMaps.connectionStates,
                onTabClick = { id -> params.deps.tabManager.focusTab(id) },
                onTabClose = params.closeTab,
                onAddClick = {
                    if (params.deps.tabManager.activeTab?.isPinnedHome == true) {
                        params.uiState.showStartWorkPicker = true
                    } else {
                        params.uiState.startWorkContext = startWorkContextFor(params.deps.tabManager.activeTab)
                        params.uiState.showStartWorkSheet = true
                    }
                },
            )
            mainTabPager(params, pagerState)
        }
    }
}

@Composable
private fun ColumnScope.mainTabPager(
    params: MainTabContentParams,
    pagerState: PagerState,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
        key = { params.tabs.getOrNull(it)?.id ?: it.toString() },
        beyondViewportPageCount = 0,
    ) { pageIndex ->
        params.tabs.getOrNull(pageIndex)?.let { tab ->
            saveableStateHolder.SaveableStateProvider(tab.id) {
                mainTabPageContent(params, tab, params.homeRepositoryStates)
            }
        }
    }
}

@Composable
private fun mainTabPageContent(
    params: MainTabContentParams,
    tab: TabInstance,
    homeRepositoryStates: List<ScopedHomeRepositoryState>,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(backStackEntry) {
        val ptyId = backStackEntry?.arguments?.getString(Screen.Terminal.ARG_PTY_ID)
        if (ptyId != null) {
            params.tabMaps.ptyIds[tab.id] = ptyId
            params.tabMaps.routes[tab.id] = Screen.Terminal.createRoute(ptyId)
        }
    }
    val isActive = tab.id == params.activeTabId
    val workspaceOwner = params.tabMaps.workspaceOwners[tab.id]
    if (tab.isPinnedHome) {
        mainTabHomeContent(params, homeRepositoryStates)
    } else if (workspaceOwner != null) {
        mainTabTabNavHostContent(params, tab, navController, isActive, workspaceOwner)
    } else {
        mainTabEmptyContent(params, tab)
    }
}

@Composable
private fun mainTabHomeContent(
    params: MainTabContentParams,
    homeRepositoryStates: List<ScopedHomeRepositoryState>,
) {
    homeScreen(
        summary = HomeSummaryBuilder.build(
            HomeSummaryInput(
                savedServers = params.savedServers,
                connectionStates = params.scopedConnectionStates,
                tabs = params.tabs,
                repositories = homeRepositoryStates,
            ),
        ),
        actions = HomeActions(
            onBrowseSessions = { target ->
                requestScopedAction(params, target, StartWorkAction.BrowseSessions)
            },
            onBrowseAllSessions = { params.uiState.showFilesTabPrompt = true },
            onManageServers = params.onDisconnect,
            onFocusTab = params.deps.tabManager::focusTab,
            onResumeSession = { session ->
                val existing = params.deps.tabManager.findTabBySessionId(session.sessionId.value)
                if (existing != null) {
                    params.deps.tabManager.focusTab(existing.id)
                } else {
                    params.deps.tabManager.createTab(
                        startRoute = Screen.Chat.createRoute(session.sessionId.value),
                        workspaceKey = session.workspaceKey,
                        serverRef = session.serverRef,
                        focus = true,
                    )
                }
            },
            onStartScopedWork = { target ->
                params.uiState.homeDetailSelection = StartWorkSelection.Selected(target)
                params.uiState.startWorkContext = startWorkContextForHomeDetail(target)
                params.uiState.showStartWorkSheet = true
            },
            onOpenFiles = { target -> requestScopedAction(params, target, StartWorkAction.Files) },
            onOpenTerminal = { target ->
                requestScopedAction(params, target, StartWorkAction.Terminal)
            },
            onChooseTarget = { params.uiState.showFilesTabPrompt = true },
            onWorkspaceDetailChanged = { params.uiState.homeDetailSelection = it },
        ),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun mainTabTabNavHostContent(
    params: MainTabContentParams,
    tab: TabInstance,
    navController: NavHostController,
    isActive: Boolean,
    workspaceOwner: WorkspaceRepositoryOwner,
) {
    val serverRef = tab.serverRef ?: return
    TabNavHost(
        navController = navController,
        tabManager = params.deps.tabManager,
        tabId = tab.id,
        serverRef = serverRef,
        onDisconnect = params.onDisconnect,
        onCloseTab = { params.closeTab(tab.id) },
        startRoute = tab.startRoute,
        workspaceOwner = workspaceOwner,
        onNewFilesTab = {
            val sr = tab.serverRef
            val wk = tab.workspaceKey
            if (sr != null && wk != null) {
                requestScopedAction(params, StartWorkTarget(sr, wk), StartWorkAction.Files)
            } else {
                params.uiState.showStartWorkPicker = true
            }
        },
        onNewTerminalTab = {
            val sr = tab.serverRef
            val wk = tab.workspaceKey
            if (sr != null && wk != null) {
                requestScopedAction(params, StartWorkTarget(sr, wk), StartWorkAction.Terminal)
            } else {
                params.uiState.showStartWorkPicker = true
            }
        },
        isActiveTab = isActive,
        onConnectionStateChanged = { state -> tab.updateConnectionState(state) },
        modifier = Modifier.fillMaxSize(),
    )
}

private val mainTabEmptyContent: @Composable (MainTabContentParams, TabInstance) -> Unit = { params, tab ->
    val theme = LocalOpenCodeTheme.current
    Box(modifier = Modifier.fillMaxSize()) {
        if (tab.serverRef == null ||
            params.scopedConnectionStates[tab.serverRef?.endpointKey] !is ConnectionState.Connected
        ) {
            Text(
                text = stringResource(R.string.server_not_connected),
                color = theme.textMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun requestScopedAction(
    params: MainTabContentParams,
    target: StartWorkTarget,
    action: StartWorkAction,
) {
    val deps = params.deps
    val uiState = params.uiState
    if (!params.savedServerExists(target.serverRef.endpointKey)) {
        uiState.pendingStartWork = target to action
        uiState.startWorkContext = StartWorkContext(
            source = StartWorkSource.OtherTab,
            selection = StartWorkSelection.Selected(target),
            defaultAction = action,
        )
        return
    }
    when (action) {
        StartWorkAction.Files -> deps.tabManager.focusOrCreateFilesTab(
            serverRef = target.serverRef,
            workspaceKey = target.workspaceKey,
        )
        StartWorkAction.BrowseSessions -> deps.tabManager.createTab(
            startRoute = Screen.Sessions.route,
            workspaceKey = target.workspaceKey,
            serverRef = target.serverRef,
            focus = true,
        )
        StartWorkAction.NewChat, StartWorkAction.Terminal -> {
            uiState.pendingStartWork = target to action
            if (params.scopedConnectionStates[target.serverRef.endpointKey] !is ConnectionState.Connected) {
                params.connectSavedServer(target.serverRef.endpointKey)
            }
        }
        StartWorkAction.ChooseAnotherTarget -> uiState.showStartWorkPicker = true
    }
}

private val startWorkSheets: @Composable (MainTabContentParams) -> Unit = { params ->
    val uiState = params.uiState
    if (uiState.showStartWorkSheet) {
        startWorkSheet(params)
    }
    if (uiState.showStartWorkPicker || uiState.showFilesTabPrompt) {
        startWorkPickerSheet(params)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun startWorkSheet(params: MainTabContentParams) {
    val uiState = params.uiState
    val theme = LocalOpenCodeTheme.current
    val context = uiState.startWorkContext
        ?: if (params.deps.tabManager.activeTab?.isPinnedHome == true) {
            StartWorkContext(StartWorkSource.HomeWorkspaceDetail, uiState.homeDetailSelection)
        } else {
            startWorkContextFor(params.deps.tabManager.activeTab)
        }
    val target = context.selectedTarget
    ModalBottomSheet(
        onDismissRequest = { uiState.showStartWorkSheet = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.background,
        modifier = Modifier.testTag("start_work_sheet"),
    ) {
        startWorkSheetContent(params, target)
    }
}

@Composable
private fun startWorkSheetContent(
    params: MainTabContentParams,
    target: StartWorkTarget?,
) {
    val theme = LocalOpenCodeTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md)
            .padding(top = Spacing.md)
            .navigationBarsPadding()
            .padding(bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(R.string.start_work_title), style = MaterialTheme.typography.titleLarge)
        if (target == null) {
            Text(stringResource(R.string.start_work_choose_context), color = theme.textMuted)
            LaunchedEffect(Unit) { params.uiState.showStartWorkPicker = true }
        } else {
            startWorkSheetTargetCard(params, target)
            startWorkSheetScopedActions(params, target)
            Text(stringResource(R.string.start_work_existing_work), color = theme.textMuted)
            startWorkActionRow(
                label = stringResource(R.string.start_work_sessions),
                description = stringResource(R.string.start_work_sessions_description),
                marker = "S",
            ) {
                params.uiState.showStartWorkSheet = false
                requestScopedAction(params, target, StartWorkAction.BrowseSessions)
            }
        }
    }
}

@Composable
private fun startWorkSheetTargetCard(
    params: MainTabContentParams,
    target: StartWorkTarget,
) {
    val theme = LocalOpenCodeTheme.current
    val tabTitleLabels = rememberTabTitleLabels()
    Surface(
        color = theme.backgroundElement,
        shape = TuiShapes.small,
        modifier = Modifier.fillMaxWidth().testTag("start_work_context"),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.start_work_in), color = theme.textMuted)
                Spacer(Modifier.width(Spacing.sm))
                val connectionStatus = connectionStatusText(
                    params.scopedConnectionStates[target.serverRef.endpointKey],
                )
                Text(
                    "${target.serverRef.displayName} · $connectionStatus",
                    color = theme.textMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { params.uiState.showStartWorkPicker = true }) {
                    Text(stringResource(R.string.start_work_change))
                }
            }
            Text(
                workspaceLabel(target.workspaceKey, tabTitleLabels)
                    ?: workspaceSubtitle(target.workspaceKey),
            )
        }
    }
}

private val connectionStatusText: @Composable (ConnectionState?) -> String = { state ->
    when (state) {
        is ConnectionState.Connected -> stringResource(R.string.server_status_connected)
        is ConnectionState.Connecting -> stringResource(R.string.server_status_connecting)
        is ConnectionState.Error -> stringResource(R.string.server_status_error)
        else -> stringResource(R.string.server_status_offline)
    }
}

@Composable
private fun startWorkSheetScopedActions(
    params: MainTabContentParams,
    target: StartWorkTarget,
) {
    val labels = mapOf(
        StartWorkAction.NewChat to (R.string.start_work_new_chat to "C"),
        StartWorkAction.Files to (R.string.start_work_files to "F"),
        StartWorkAction.Terminal to (R.string.start_work_terminal to "T"),
    )
    startWorkScopedActionOrder.forEach { action ->
        val (label, marker) = checkNotNull(labels[action])
        startWorkActionRow(
            label = stringResource(label),
            description = stringResource(R.string.start_work_scoped_action),
            marker = marker,
        ) {
            params.uiState.showStartWorkSheet = false
            requestScopedAction(params, target, action)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun startWorkPickerSheet(params: MainTabContentParams) {
    val uiState = params.uiState
    val theme = LocalOpenCodeTheme.current
    val openTargets = params.tabs.mapNotNull { tab ->
        val serverRef = tab.serverRef ?: return@mapNotNull null
        val workspaceKey = tab.workspaceKey ?: return@mapNotNull null
        StartWorkTarget(serverRef, workspaceKey)
    }.distinct()
    val knownHomeTargets = remember(params.homeRepositoryStates) {
        deriveStartWorkPickerTargets(params.homeRepositoryStates)
    }
    ModalBottomSheet(
        onDismissRequest = {
            uiState.showStartWorkPicker = false
            uiState.showFilesTabPrompt = false
        },
        containerColor = theme.background,
        modifier = Modifier.testTag("start_work_context_picker"),
    ) {
        startWorkPickerContent(params, openTargets, knownHomeTargets)
    }
}

@Composable
private fun startWorkPickerContent(
    params: MainTabContentParams,
    openTargets: List<StartWorkTarget>,
    knownHomeTargets: List<StartWorkTarget>,
) {
    val theme = LocalOpenCodeTheme.current
    val tabTitleLabels = rememberTabTitleLabels()
    val uiState = params.uiState
    val groups = remember(params.savedServerViews, openTargets, knownHomeTargets) {
        buildStartWorkPickerGroups(
            params.savedServerViews.map { Triple(it.endpointKey, it.displayName, it.badgeLabel) },
            openTargets,
            knownHomeTargets,
        )
    }
    LaunchedEffect(groups, uiState.pickerSelectedEndpointKey) {
        if (groups.none { it.server.endpointKey == uiState.pickerSelectedEndpointKey }) {
            uiState.pickerSelectedEndpointKey = groups.firstOrNull()?.server?.endpointKey
        }
    }
    val pickerState = StartWorkPickerState(uiState.pickerSelectedEndpointKey, uiState.pickerSearchQuery)
    val selectedGroup = groups.firstOrNull { it.server.endpointKey == pickerState.selectedEndpointKey }
    val targets = remember(groups, pickerState) { pickerState.filteredTargets(groups) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
        Text(
            stringResource(R.string.start_work_choose_context),
            style = MaterialTheme.typography.titleMedium,
        )
        if (params.savedServerViews.isEmpty()) {
            Text(stringResource(R.string.start_work_no_servers), color = theme.textMuted)
        }
        startWorkPickerSearch(uiState)
        startWorkServerRail(groups, selectedGroup, uiState)
        startWorkPickerLedger(params, targets)
    }
}

internal val createPtyRequestForWorkspace: (WorkspaceKey) -> CreatePtyRequest = { workspaceKey ->
    CreatePtyRequest(
        cwd = (workspaceKey as? WorkspaceKey.Directory)?.value,
        title = terminalTitle(workspaceKey),
    )
}

private val selectStartWorkPickerTarget: (MainTabContentParams, StartWorkTarget) -> Unit =
    { params, pickedTarget ->
        params.uiState.startWorkContext = StartWorkContext(
            StartWorkSource.OtherTab,
            StartWorkSelection.Selected(pickedTarget),
            params.uiState.startWorkContext?.defaultAction,
        )
        params.uiState.homeDetailSelection = StartWorkSelection.Selected(pickedTarget)
        params.uiState.showStartWorkPicker = false
        params.uiState.showFilesTabPrompt = false
        params.uiState.showStartWorkSheet = true
    }

private val terminalTitle: (WorkspaceKey) -> String? = { workspaceKey ->
    when (workspaceKey) {
        is WorkspaceKey.Directory -> workspaceKey.value.trimEnd('/').substringAfterLast('/').ifBlank { "Terminal" }
        WorkspaceKey.Global -> null
        is WorkspaceKey.SessionScoped -> workspaceKey.sessionId.value
    }
}

private val workspaceSubtitle: (WorkspaceKey) -> String = { workspaceKey ->
    when (workspaceKey) {
        is WorkspaceKey.Directory -> workspaceKey.value
        WorkspaceKey.Global -> "No project context"
        is WorkspaceKey.SessionScoped -> "Session-scoped workspace"
    }
}

@Composable
private fun startWorkActionRow(
    label: String,
    description: String,
    marker: String,
    onClick: () -> Unit,
) {
    val actionDescription = stringResource(R.string.start_work_action_accessibility, label, description)
    filesWorkspaceOption(
        title = label,
        subtitle = description,
        marker = marker,
        onClick = onClick,
        modifier = Modifier
            .testTag("start_work_${marker.lowercase()}")
            .semantics { contentDescription = actionDescription },
    )
}

@Composable
private fun filesWorkspaceOption(
    title: String,
    subtitle: String,
    marker: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        color = theme.backgroundElement,
        shape = TuiShapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = marker,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted,
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted,
            )
        }
    }
}
