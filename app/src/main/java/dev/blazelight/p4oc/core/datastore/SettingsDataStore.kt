package dev.blazelight.p4oc.core.datastore

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ServerUrl
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.domain.server.ServerIdentity
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.workspace.Workspace
import dev.blazelight.p4oc.terminal.TerminalFontSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val settingsCorruptionHandler: ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { emptyPreferences() }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = settingsCorruptionHandler,
    produceMigrations = { listOf(removeDeadWorkspacePrefsMigration()) },
)

private const val TAG = "SettingsDataStore"
internal const val MAX_SESSION_AGENT_SELECTIONS = 100
internal const val MAX_SESSION_COMPOSER_SELECTIONS = 100

@Serializable
data class SessionComposerSelection(
    val model: ModelInput,
    val variant: String? = null,
    val pendingServerSync: Boolean = false,
)

private fun parseSessionAgentSelections(stored: String?): LinkedHashMap<String, String> =
    stored?.let {
        runCatching {
            Json.decodeFromString<LinkedHashMap<String, String>>(it)
        }.getOrNull()
    } ?: linkedMapOf()

internal fun selectedAgentForSession(stored: String?, sessionId: String): String? =
    parseSessionAgentSelections(stored)[sessionId]

internal fun updatedSessionAgentSelections(
    stored: String?,
    sessionId: String,
    agentName: String,
): String {
    val selections = parseSessionAgentSelections(stored)
    selections.remove(sessionId)
    selections[sessionId] = agentName
    while (selections.size > MAX_SESSION_AGENT_SELECTIONS) {
        selections.remove(selections.keys.first())
    }
    return Json.encodeToString(selections)
}

private fun parseSessionComposerSelections(stored: String?): LinkedHashMap<String, SessionComposerSelection> =
    stored?.let {
        runCatching {
            Json.decodeFromString<LinkedHashMap<String, SessionComposerSelection>>(it)
        }.getOrNull()
    } ?: linkedMapOf()

internal fun composerSelectionForSession(
    stored: String?,
    selectionKey: String,
): SessionComposerSelection? = parseSessionComposerSelections(stored)[selectionKey]

internal fun updatedSessionComposerSelections(
    stored: String?,
    selectionKey: String,
    selection: SessionComposerSelection,
): String {
    val selections = parseSessionComposerSelections(stored)
    selections.remove(selectionKey)
    selections[selectionKey] = selection
    while (selections.size > MAX_SESSION_COMPOSER_SELECTIONS) {
        selections.remove(selections.keys.first())
    }
    return Json.encodeToString(selections)
}

internal fun sessionComposerSelectionKey(workspace: Workspace, sessionId: String): String = Json.encodeToString(
    listOf(
        workspace.server.endpointKey,
        when (val key = workspace.key) {
            WorkspaceKey.Global -> "global"
            is WorkspaceKey.Directory -> "directory:${key.value}"
            is WorkspaceKey.SessionScoped -> "session:${key.sessionId.value}"
        },
        sessionId,
    )
)

internal const val MAX_LAST_UPLOAD_DIRECTORIES = 50

internal fun decodeLastUploadDirectories(encoded: String?): Map<String, String> {
    if (encoded.isNullOrBlank()) return emptyMap()
    return runCatching {
        Json.decodeFromString<LinkedHashMap<String, String>>(encoded).apply {
            while (size > MAX_LAST_UPLOAD_DIRECTORIES) {
                remove(keys.first())
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun updateLastUploadDirectories(
    current: Map<String, String>,
    workspaceKey: String,
    path: String?,
): Map<String, String> {
    if (workspaceKey.isBlank()) return current

    val updated = LinkedHashMap(current)
    updated.remove(workspaceKey)
    if (!path.isNullOrBlank()) updated[workspaceKey] = path
    while (updated.size > MAX_LAST_UPLOAD_DIRECTORIES) {
        updated.remove(updated.keys.first())
    }
    return updated
}

private val notificationRoutingJson = Json { ignoreUnknownKeys = true }

internal fun decodeServerRouting(stored: String?): Map<String, NotificationRoutingMode> {
    if (stored.isNullOrBlank()) return emptyMap()
    return runCatching {
        notificationRoutingJson.decodeFromString<Map<String, String>>(stored)
            .mapValues { NotificationRoutingMode.fromStorage(it.value) }
    }.getOrDefault(emptyMap())
}

internal fun encodeServerRouting(routing: Map<String, NotificationRoutingMode>): String? {
    val nonDefault = routing.filterValues { it != NotificationRoutingMode.All }
    return nonDefault.takeIf { it.isNotEmpty() }?.let { values ->
        notificationRoutingJson.encodeToString(values.mapValues { it.value.storageValue })
    }
}

internal fun pruneServerRouting(stored: String?, endpointKey: String): String? =
    encodeServerRouting(decodeServerRouting(stored) - endpointKey)

class SettingsDataStore constructor(
    private val context: Context,
    private val credentialStore: CredentialStore
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_SERVER_NAME = stringPreferencesKey("server_name")
        private val KEY_IS_LOCAL_SERVER = booleanPreferencesKey("is_local_server")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_ALLOW_INSECURE = booleanPreferencesKey("allow_insecure_tls")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_THEME_NAME = stringPreferencesKey("theme_name")
        private val KEY_OLED_BLACK = booleanPreferencesKey("oled_black")

        const val DEFAULT_THEME_NAME = "opencode"
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_RECENT_SERVERS = stringPreferencesKey("recent_servers")
        private val KEY_SAVED_SERVERS = stringPreferencesKey("saved_servers_v1")
        private val KEY_TAB_STATE = stringPreferencesKey("tab_state_v1")

        // Visual settings keys
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_CODE_BLOCK_FONT_SIZE = intPreferencesKey("code_block_font_size")
        private val KEY_TERMINAL_FONT_SIZE = intPreferencesKey("terminal_font_size")
        private val KEY_SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        private val KEY_WORD_WRAP = booleanPreferencesKey("word_wrap")
        private val KEY_COMPACT_MODE = booleanPreferencesKey("compact_mode")
        private val KEY_MESSAGE_SPACING = intPreferencesKey("message_spacing")
        private val KEY_HIGH_CONTRAST_MODE = booleanPreferencesKey("high_contrast_mode")
        private val KEY_REASONING_EXPANDED = booleanPreferencesKey("reasoning_expanded_by_default")

        private val KEY_TOOL_WIDGET_DEFAULT_STATE = stringPreferencesKey("tool_widget_default_state")
        private val KEY_OPEN_SUB_AGENT_NEW_TAB = booleanPreferencesKey("open_sub_agent_new_tab")

        // Model favorites and recents
        private val KEY_FAVORITE_MODELS = stringSetPreferencesKey("favorite_models")
        private val KEY_RECENT_MODELS = stringPreferencesKey("recent_models")
        private val KEY_SESSION_AGENTS = stringPreferencesKey("session_agents")
        private val KEY_SESSION_COMPOSER_SELECTIONS = stringPreferencesKey("session_composer_selections_v1")
        private const val MAX_RECENT_MODELS = 10

        // Notification settings keys
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFY_PERMISSIONS = booleanPreferencesKey("notify_permissions")
        private val KEY_NOTIFY_QUESTIONS = booleanPreferencesKey("notify_questions")
        private val KEY_NOTIFY_VIBRATE_ON_COMPLETION = booleanPreferencesKey("notify_vibrate_on_completion")
        private val KEY_NOTIFY_VIBRATION_PATTERN = stringPreferencesKey("notify_vibration_pattern")
        private val KEY_NOTIFY_ON_COMPLETION = booleanPreferencesKey("notify_on_completion")
        private val KEY_NOTIFY_SERVER_ROUTING = stringPreferencesKey("notify_server_routing")

        // Chat settings keys
        private val KEY_CHAT_ENTER_TO_SEND = booleanPreferencesKey("chat_enter_to_send")
        private val KEY_CHAT_LAST_UPLOAD_DIR_BY_WORKSPACE = stringPreferencesKey("chat_last_upload_dir_by_workspace")

        // Connection settings keys
        private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val KEY_RECONNECT_TIMEOUT_SECONDS = intPreferencesKey("reconnect_timeout_seconds")

        const val DEFAULT_LOCAL_URL = "http://localhost:4096"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val MAX_RECENT_SERVERS = 5
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cachedServerUrl: String = DEFAULT_LOCAL_URL

    @Volatile
    private var cachedUsername: String? = null

    init {
        scope.launch {
            try {
                val prefs = context.dataStore.data.first()
                cachedServerUrl = prefs[KEY_SERVER_URL] ?: DEFAULT_LOCAL_URL
                cachedUsername = prefs[KEY_USERNAME]
            } catch (e: Exception) {
                AppLog.e(TAG, "Error during init (${e::class.simpleName})")
            }
        }
    }

    fun getCachedServerUrl(): String = cachedServerUrl
    fun getCachedUsername(): String? = cachedUsername

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        (prefs[KEY_SERVER_URL] ?: DEFAULT_LOCAL_URL).also { cachedServerUrl = it }
    }

    val serverName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVER_NAME] ?: "Local (Termux)"
    }

    val isLocalServer: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_LOCAL_SERVER] ?: true
    }

    val username: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USERNAME].also { cachedUsername = it }
    }

    // password Flow REMOVED — use credentialStore.getActivePassword() instead

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: THEME_SYSTEM
    }

    val themeName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_NAME] ?: DEFAULT_THEME_NAME
    }

    /** AMOLED/OLED mode: forces pure-black backgrounds on dark themes to save power. */
    val oledBlack: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_OLED_BLACK] ?: false
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
        }
    }

    suspend fun setServerName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_NAME] = name
        }
    }

    suspend fun setIsLocalServer(isLocal: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOCAL_SERVER] = isLocal
        }
    }

    /**
     * Set credentials. Username goes to DataStore, password goes to CredentialStore.
     */
    suspend fun setCredentials(username: String?, password: String?) {
        context.dataStore.edit { prefs ->
            if (username != null) {
                prefs[KEY_USERNAME] = username
            } else {
                prefs.remove(KEY_USERNAME)
            }
        }
        credentialStore.setActivePassword(password)
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setThemeName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_NAME] = name
        }
    }

    suspend fun setOledBlack(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OLED_BLACK] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    val persistedTabState: Flow<PersistedTabState?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TAB_STATE]?.let(::parsePersistedTabState)
    }

    suspend fun getPersistedTabState(): PersistedTabState? =
        context.dataStore.data.first()[KEY_TAB_STATE]?.let(::parsePersistedTabState)

    suspend fun setPersistedTabState(state: PersistedTabState?) {
        context.dataStore.edit { prefs ->
            if (state == null) {
                prefs.remove(KEY_TAB_STATE)
            } else {
                prefs[KEY_TAB_STATE] = json.encodeToString(state)
            }
        }
    }

    /**
     * Save server config. Password is stored separately in CredentialStore.
     */
    suspend fun setServerConfig(
        url: String,
        name: String,
        isLocal: Boolean,
        username: String? = null,
        password: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
            prefs[KEY_SERVER_NAME] = name
            prefs[KEY_IS_LOCAL_SERVER] = isLocal
            if (username != null) prefs[KEY_USERNAME] = username else prefs.remove(KEY_USERNAME)
        }
        // Password goes to encrypted storage
        credentialStore.setActivePassword(password)
        if (password != null) {
            credentialStore.setServerPassword(url, password)
        }
        // Update cache AFTER successful write
        cachedServerUrl = url
        cachedUsername = username
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        credentialStore.clearAll()
    }

    /**
     * Save last connection config. Password stored in CredentialStore.
     */
    suspend fun saveLastConnection(config: dev.blazelight.p4oc.core.network.ServerConfig, password: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = config.url
            prefs[KEY_SERVER_NAME] = config.name
            prefs[KEY_IS_LOCAL_SERVER] = config.isLocal
            if (config.username != null) {
                prefs[KEY_USERNAME] = config.username
            } else {
                prefs.remove(KEY_USERNAME)
            }
            prefs[KEY_ALLOW_INSECURE] = config.allowInsecure
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
        // Store password encrypted
        credentialStore.setActivePassword(password)
        if (password != null) {
            credentialStore.setServerPassword(config.url, password)
        }
        // Update cache after successful write
        cachedServerUrl = config.url
        cachedUsername = config.username
    }

    /**
     * Get last connection config. Password comes from CredentialStore.
     * Returns a Pair of (ServerConfig, password?) so the caller can use the password
     * without it being embedded in ServerConfig.
     */
    suspend fun getLastConnection(): Pair<dev.blazelight.p4oc.core.network.ServerConfig, String?>? {
        val prefs = context.dataStore.data.first()
        val url = prefs[KEY_SERVER_URL] ?: return null
        val config = dev.blazelight.p4oc.core.network.ServerConfig(
            url = url,
            name = prefs[KEY_SERVER_NAME] ?: "",
            isLocal = prefs[KEY_IS_LOCAL_SERVER] ?: false,
            username = prefs[KEY_USERNAME],
            allowInsecure = prefs[KEY_ALLOW_INSECURE] ?: false
        )
        val password = credentialStore.getActivePassword()
        return Pair(config, password)
    }

    suspend fun clearLastConnection() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SERVER_URL)
            prefs.remove(KEY_SERVER_NAME)
            prefs.remove(KEY_IS_LOCAL_SERVER)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_ALLOW_INSECURE)
        }
        credentialStore.clearActivePassword()
    }

    val recentServers: Flow<List<RecentServer>> = context.dataStore.data.map { prefs ->
        val stored = prefs[KEY_RECENT_SERVERS] ?: return@map emptyList()
        try {
            parseRecentServersLenient(stored)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error parsing recent servers (${e::class.simpleName})")
            emptyList()
        }
    }

    val savedServers: Flow<List<SavedServer>> = context.dataStore.data.map { prefs ->
        savedServersFromPreferences(prefs)
    }

    suspend fun getSavedServers(): List<SavedServer> = savedServersFromPreferences(context.dataStore.data.first())

    suspend fun findSavedServer(id: String): SavedServer? = getSavedServers().firstOrNull { it.id == id }

    suspend fun findSavedServerByEndpointKey(endpointKey: String): SavedServer? =
        getSavedServers().firstOrNull { it.endpointKey == endpointKey }

    suspend fun upsertSavedServer(server: SavedServer) {
        val normalized = SavedServerRegistry.normalize(server)
        context.dataStore.edit { prefs ->
            val current = savedServersFromPreferences(prefs)
            val updated = SavedServerRegistry.upsert(current, normalized)
            prefs[KEY_SAVED_SERVERS] = json.encodeToString(updated)
        }
    }

    suspend fun addSavedServer(
        url: String,
        name: String,
        username: String? = null,
        password: String? = null,
        allowInsecure: Boolean = false,
        pinned: Boolean = false,
        defaultWorkspace: String? = null,
        lastConnectedAt: Long? = null,
    ): SavedServer {
        val server = SavedServerRegistry.fromConnection(
            url = url,
            name = name,
            username = username,
            allowInsecure = allowInsecure,
            pinned = pinned,
            defaultWorkspace = defaultWorkspace,
            lastConnectedAt = lastConnectedAt,
        )
        upsertSavedServer(server)
        if (password != null) {
            credentialStore.setServerPassword(server.id, password)
            credentialStore.setServerPassword(server.endpoint, password)
        }
        return server
    }

    suspend fun updateSavedServer(server: SavedServer) = upsertSavedServer(server)

    suspend fun removeSavedServer(id: String, removeCredentials: Boolean = true) {
        var removed: SavedServer? = null
        context.dataStore.edit { prefs ->
            val current = savedServersFromPreferences(prefs)
            removed = current.firstOrNull { it.id == id }
            val updated = current.filterNot { it.id == id }
            if (updated.isEmpty()) {
                prefs.remove(KEY_SAVED_SERVERS)
            } else {
                prefs[KEY_SAVED_SERVERS] = json.encodeToString(updated)
            }
            removed?.endpointKey?.let { endpointKey ->
                val updatedRouting = pruneServerRouting(prefs[KEY_NOTIFY_SERVER_ROUTING], endpointKey)
                if (updatedRouting == null) {
                    prefs.remove(KEY_NOTIFY_SERVER_ROUTING)
                } else {
                    prefs[KEY_NOTIFY_SERVER_ROUTING] = updatedRouting
                }
            }
        }
        if (removeCredentials) {
            removed?.let { server ->
                credentialStore.removeServerPassword(server.id)
                credentialStore.removeServerPassword(server.endpoint)
            }
        }
    }

    suspend fun getSavedServerPassword(server: SavedServer): String? =
        credentialStore.getServerPassword(server.id) ?: credentialStore.getServerPassword(server.endpoint)

    /**
     * Add a recent server. Password is stored in CredentialStore, not in the JSON.
     */
    suspend fun addRecentServer(
        url: String,
        name: String,
        username: String? = null,
        password: String? = null,
        allowInsecure: Boolean = false
    ) {
        // Store password in encrypted storage (keyed by URL)
        if (password != null) {
            credentialStore.setServerPassword(url, password)
        }

        context.dataStore.edit { prefs ->
            val stored = prefs[KEY_RECENT_SERVERS] ?: ""
            val servers = if (stored == null) {
                mutableListOf()
            } else {
                try {
                    parseRecentServersLenient(stored).toMutableList()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error parsing recent servers in addRecentServer (${e::class.simpleName})")
                    mutableListOf()
                }
            }

            servers.removeAll { it.url == url }
            servers.add(0, RecentServer(url, name, username, allowInsecure))
            val trimmed = servers.take(MAX_RECENT_SERVERS)

            prefs[KEY_RECENT_SERVERS] = json.encodeToString(trimmed)
        }
    }

    suspend fun removeRecentServer(url: String) {
        // Remove the associated password from encrypted storage
        credentialStore.removeServerPassword(url)

        context.dataStore.edit { prefs ->
            val stored = prefs[KEY_RECENT_SERVERS] ?: return@edit
            val servers = try {
                parseRecentServersLenient(stored).filter { it.url != url }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error parsing recent servers in removeRecentServer (${e::class.simpleName})")
                return@edit
            }
            prefs[KEY_RECENT_SERVERS] = json.encodeToString(servers)
        }
    }

    val visualSettings: Flow<VisualSettings> = context.dataStore.data.map { prefs ->
        VisualSettings(
            fontSize = prefs[KEY_FONT_SIZE] ?: 14,
            lineSpacing = prefs[KEY_LINE_SPACING] ?: 1.5f,
            fontFamily = prefs[KEY_FONT_FAMILY] ?: "System",
            codeBlockFontSize = prefs[KEY_CODE_BLOCK_FONT_SIZE] ?: 12,
            terminalFontSize = TerminalFontSize.clamp(prefs[KEY_TERMINAL_FONT_SIZE] ?: TerminalFontSize.DEFAULT_SP),
            showLineNumbers = prefs[KEY_SHOW_LINE_NUMBERS] ?: true,
            wordWrap = prefs[KEY_WORD_WRAP] ?: false,
            compactMode = prefs[KEY_COMPACT_MODE] ?: false,
            messageSpacing = prefs[KEY_MESSAGE_SPACING] ?: 8,
            highContrastMode = prefs[KEY_HIGH_CONTRAST_MODE] ?: false,
            reasoningExpandedByDefault = prefs[KEY_REASONING_EXPANDED] ?: false,
            toolWidgetDefaultState = prefs[KEY_TOOL_WIDGET_DEFAULT_STATE] ?: "compact",
            openSubAgentInNewTab = prefs[KEY_OPEN_SUB_AGENT_NEW_TAB] ?: true
        )
    }

    suspend fun updateVisualSettings(settings: VisualSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FONT_SIZE] = settings.fontSize
            prefs[KEY_LINE_SPACING] = settings.lineSpacing
            prefs[KEY_FONT_FAMILY] = settings.fontFamily
            prefs[KEY_CODE_BLOCK_FONT_SIZE] = settings.codeBlockFontSize
            prefs[KEY_TERMINAL_FONT_SIZE] = TerminalFontSize.clamp(settings.terminalFontSize)
            prefs[KEY_SHOW_LINE_NUMBERS] = settings.showLineNumbers
            prefs[KEY_WORD_WRAP] = settings.wordWrap
            prefs[KEY_COMPACT_MODE] = settings.compactMode
            prefs[KEY_MESSAGE_SPACING] = settings.messageSpacing
            prefs[KEY_HIGH_CONTRAST_MODE] = settings.highContrastMode
            prefs[KEY_REASONING_EXPANDED] = settings.reasoningExpandedByDefault
            prefs[KEY_TOOL_WIDGET_DEFAULT_STATE] = settings.toolWidgetDefaultState
            prefs[KEY_OPEN_SUB_AGENT_NEW_TAB] = settings.openSubAgentInNewTab
        }
    }

    /** Writes only the terminal size, so pinch-to-zoom never overwrites other visual settings. */
    suspend fun setTerminalFontSize(sizeSp: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_TERMINAL_FONT_SIZE] = TerminalFontSize.clamp(sizeSp) }
    }

    // ── Notification settings ──

    val notificationSettings: Flow<NotificationSettings> = context.dataStore.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            permissionRequests = prefs[KEY_NOTIFY_PERMISSIONS] ?: true,
            questions = prefs[KEY_NOTIFY_QUESTIONS] ?: true,
            vibrationPattern = prefs[KEY_NOTIFY_VIBRATION_PATTERN]?.toVibrationPattern()
                ?: if (prefs[KEY_NOTIFY_VIBRATE_ON_COMPLETION] == true) VibrationPattern.Tick else VibrationPattern.None,
            notifyOnCompletion = prefs[KEY_NOTIFY_ON_COMPLETION] ?: false,
            serverRouting = decodeServerRouting(prefs[KEY_NOTIFY_SERVER_ROUTING]),
        )
    }

    suspend fun updateNotificationSettings(settings: NotificationSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = settings.enabled
            prefs[KEY_NOTIFY_PERMISSIONS] = settings.permissionRequests
            prefs[KEY_NOTIFY_QUESTIONS] = settings.questions
            prefs[KEY_NOTIFY_VIBRATION_PATTERN] = settings.vibrationPattern.storageValue
            prefs[KEY_NOTIFY_ON_COMPLETION] = settings.notifyOnCompletion
            prefs.remove(KEY_NOTIFY_VIBRATE_ON_COMPLETION)
            val encodedRouting = encodeServerRouting(settings.serverRouting)
            if (encodedRouting == null) {
                prefs.remove(KEY_NOTIFY_SERVER_ROUTING)
            } else {
                prefs[KEY_NOTIFY_SERVER_ROUTING] = encodedRouting
            }
        }
    }

    // ── Chat settings ──

    val chatSettings: Flow<ChatSettings> = context.dataStore.data.map { prefs ->
        ChatSettings(
            enterToSend = prefs[KEY_CHAT_ENTER_TO_SEND] ?: false,
        )
    }

    suspend fun updateChatSettings(settings: ChatSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CHAT_ENTER_TO_SEND] = settings.enterToSend
        }
    }

    val lastUploadDirectoriesByWorkspace: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        decodeLastUploadDirectories(prefs[KEY_CHAT_LAST_UPLOAD_DIR_BY_WORKSPACE])
    }

    suspend fun setLastUploadDirectory(workspaceKey: String, path: String?) {
        if (workspaceKey.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = decodeLastUploadDirectories(prefs[KEY_CHAT_LAST_UPLOAD_DIR_BY_WORKSPACE])
            val updated = updateLastUploadDirectories(current, workspaceKey, path)
            if (updated.isEmpty()) {
                prefs.remove(KEY_CHAT_LAST_UPLOAD_DIR_BY_WORKSPACE)
            } else {
                prefs[KEY_CHAT_LAST_UPLOAD_DIR_BY_WORKSPACE] = json.encodeToString(updated)
            }
        }
    }

    // ── Connection settings ──

    val connectionSettings: Flow<ConnectionSettings> = context.dataStore.data.map { prefs ->
        ConnectionSettings(
            autoReconnect = prefs[KEY_AUTO_RECONNECT] ?: true,
            reconnectTimeoutSeconds = prefs[KEY_RECONNECT_TIMEOUT_SECONDS] ?: 45
        )
    }

    suspend fun updateConnectionSettings(settings: ConnectionSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_RECONNECT] = settings.autoReconnect
            prefs[KEY_RECONNECT_TIMEOUT_SECONDS] = settings.reconnectTimeoutSeconds
        }
    }

    val favoriteModels: Flow<Set<ModelInput>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_FAVORITE_MODELS] ?: emptySet()).mapNotNull { it.toModelInput() }.toSet()
    }

    val recentModels: Flow<List<ModelInput>> = context.dataStore.data.map { prefs ->
        val stored = prefs[KEY_RECENT_MODELS] ?: return@map emptyList()
        try {
            if (stored.startsWith("[")) {
                stored.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.toModelInput() }
            } else {
                stored.split("|||").filter { it.isNotBlank() }.mapNotNull { it.toModelInput() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun toggleFavoriteModel(model: ModelInput) {
        val key = model.toStorageKey()
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_MODELS] ?: emptySet()
            prefs[KEY_FAVORITE_MODELS] = if (key in current) {
                current - key
            } else {
                current + key
            }
        }
    }

    suspend fun addRecentModel(model: ModelInput) {
        val key = model.toStorageKey()
        context.dataStore.edit { prefs ->
            val stored = prefs[KEY_RECENT_MODELS] ?: ""
            val existing = if (stored.isBlank()) {
                mutableListOf()
            } else if (stored.startsWith("[")) {
                stored.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
                    .toMutableList()
            } else {
                stored.split("|||").filter { it.isNotBlank() }.toMutableList()
            }
            existing.remove(key)
            existing.add(0, key)
            prefs[KEY_RECENT_MODELS] = "[" + existing.take(MAX_RECENT_MODELS).joinToString(",") { "\"$it\"" } + "]"
        }
    }

    suspend fun getSelectedAgentForSession(sessionId: String): String? {
        val stored = context.dataStore.data.first()[KEY_SESSION_AGENTS] ?: return null
        return selectedAgentForSession(stored, sessionId)
    }

    suspend fun setSelectedAgentForSession(sessionId: String, agentName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_AGENTS] = updatedSessionAgentSelections(
                stored = prefs[KEY_SESSION_AGENTS],
                sessionId = sessionId,
                agentName = agentName,
            )
        }
    }

    suspend fun getComposerSelectionForSession(
        workspace: Workspace,
        sessionId: String,
    ): SessionComposerSelection? {
        val stored = context.dataStore.data.first()[KEY_SESSION_COMPOSER_SELECTIONS] ?: return null
        return composerSelectionForSession(stored, sessionComposerSelectionKey(workspace, sessionId))
    }

    suspend fun setComposerSelectionForSession(
        workspace: Workspace,
        sessionId: String,
        selection: SessionComposerSelection,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_COMPOSER_SELECTIONS] = updatedSessionComposerSelections(
                stored = prefs[KEY_SESSION_COMPOSER_SELECTIONS],
                selectionKey = sessionComposerSelectionKey(workspace, sessionId),
                selection = selection,
            )
        }
    }

    /**
     * Parse recent servers from stored format (kotlinx.serialization JSON).
     */
    private fun parseRecentServersLenient(stored: String): List<RecentServer> {
        if (stored.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<RecentServer>>(stored)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSavedServersLenient(stored: String): List<SavedServer> {
        if (stored.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SavedServer>>(stored) }.getOrDefault(emptyList())
    }

    private fun savedServersFromPreferences(prefs: Preferences): List<SavedServer> {
        val stored = prefs[KEY_SAVED_SERVERS].orEmpty()
        val saved = parseSavedServersLenient(stored)

        val lastConnection = prefs[KEY_SERVER_URL]?.let { url ->
            SavedServerRegistry.fromConnection(
                url = url,
                name = prefs[KEY_SERVER_NAME].orEmpty(),
                username = prefs[KEY_USERNAME],
                allowInsecure = prefs[KEY_ALLOW_INSECURE] ?: false,
                lastConnectedAt = null,
            )
        }

        val recent = prefs[KEY_RECENT_SERVERS]
            ?.let(::parseRecentServersLenient)
            .orEmpty()
            .mapNotNull { recentServer ->
                runCatching {
                    SavedServerRegistry.fromConnection(
                        url = recentServer.url,
                        name = recentServer.name,
                        username = recentServer.username,
                        allowInsecure = recentServer.allowInsecure,
                        lastConnectedAt = null,
                    )
                }.getOrNull()
            }

        // The active connection is the newest explicit configuration. Keep it first so
        // revocable settings (notably allowInsecure) cannot be resurrected by stale
        // saved/recent representations of the same endpoint.
        return SavedServerRegistry.merge(listOfNotNull(lastConnection) + recent + saved)
    }

    private fun parsePersistedTabState(stored: String): PersistedTabState? = try {
        migrateLegacyPersistedTabState(stored) ?: json.decodeFromString<PersistedTabState>(stored)
    } catch (e: Exception) {
        AppLog.w(TAG, "Ignoring invalid persisted tab state (${e::class.simpleName})")
        null
    }

    private fun migrateLegacyPersistedTabState(stored: String): PersistedTabState? {
        val legacy = json.decodeFromString<LegacyPersistedTabState>(stored)
        if (legacy.version >= PersistedTabState.CURRENT_VERSION) return null
        val migratedTabs = legacy.tabs.mapNotNull { tab ->
            val workspaceKey = tab.resolvedWorkspaceKey() ?: return@mapNotNull null
            PersistedTab(
                id = tab.id,
                startRoute = tab.startRoute,
                sessionId = tab.sessionId,
                sessionTitle = tab.sessionTitle,
                workspaceKey = workspaceKey,
                serverEndpointKey = legacy.serverEndpointKey,
            )
        }
        return PersistedTabState(
            version = PersistedTabState.CURRENT_VERSION,
            serverEndpointKey = legacy.serverEndpointKey,
            activeTabId = legacy.activeTabId?.takeIf { activeId -> migratedTabs.any { it.id == activeId } },
            tabs = migratedTabs,
        )
    }
}

private fun removeDeadWorkspacePrefsMigration(): DataMigration<Preferences> = object : DataMigration<Preferences> {
    private val projectWorktree = stringPreferencesKey("project_worktree")
    private val lastSessionId = stringPreferencesKey("last_session_id")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        projectWorktree in currentData || lastSessionId in currentData

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        remove(projectWorktree)
        remove(lastSessionId)
    }.toPreferences()

    override suspend fun cleanUp() = Unit
}

private fun ModelInput.toStorageKey(): String = "$providerID/$modelID"

private fun String.toModelInput(): ModelInput? {
    val parts = split("/", limit = 2)
    return if (parts.size >= 2) {
        ModelInput(providerID = parts[0], modelID = parts.drop(1).joinToString("/"))
    } else {
        null
    }
}

/**
 * A recent server entry for the server picker.
 * Password is NOT stored here — it lives in [CredentialStore].
 */
@Serializable
data class RecentServer(
    val url: String,
    val name: String,
    val username: String? = null,
    val allowInsecure: Boolean = false
)

@Serializable
data class SavedServer(
    val id: String,
    val endpoint: String,
    val endpointKey: String,
    val displayName: String,
    val username: String? = null,
    val allowInsecure: Boolean = false,
    val pinned: Boolean = false,
    val defaultWorkspace: String? = null,
    val lastConnectedAt: Long? = null,
) {
    val badgeLabel: String
        get() = ServerIdentity.derive(endpointKey, displayName).badgeLabel
}

internal object SavedServerRegistry {
    fun fromConnection(
        url: String,
        name: String,
        username: String? = null,
        allowInsecure: Boolean = false,
        pinned: Boolean = false,
        defaultWorkspace: String? = null,
        lastConnectedAt: Long? = null,
    ): SavedServer {
        val endpoint = ServerUrl.normalizeConnectUrl(url)
            ?: throw IllegalArgumentException("Invalid server endpoint: $url")
        val endpointKey = ServerUrl.endpointKey(endpoint)
            ?: throw IllegalArgumentException("Invalid server endpoint: $url")
        val identity = ServerIdentity.derive(endpointKey, name)
        return SavedServer(
            id = endpointKey,
            endpoint = endpoint,
            endpointKey = endpointKey,
            displayName = identity.displayName,
            username = username,
            allowInsecure = allowInsecure,
            pinned = pinned,
            defaultWorkspace = defaultWorkspace?.takeIf { it.isNotBlank() },
            lastConnectedAt = lastConnectedAt,
        )
    }

    fun normalize(server: SavedServer): SavedServer {
        val endpoint = ServerUrl.normalizeConnectUrl(server.endpoint)
            ?: throw IllegalArgumentException("Invalid server endpoint: ${server.endpoint}")
        val endpointKey = ServerUrl.endpointKey(endpoint)
            ?: throw IllegalArgumentException("Invalid server endpoint: ${server.endpoint}")
        val identity = ServerIdentity.derive(endpointKey, server.displayName)
        return server.copy(
            id = server.id.ifBlank { endpointKey },
            endpoint = endpoint,
            endpointKey = endpointKey,
            displayName = identity.displayName,
            defaultWorkspace = server.defaultWorkspace?.takeIf { it.isNotBlank() },
        )
    }

    fun upsert(current: List<SavedServer>, server: SavedServer): List<SavedServer> {
        val normalized = normalize(server)
        val withoutSameIdentity = current.filterNot {
            it.id == normalized.id || it.endpointKey == normalized.endpointKey
        }
        return merge(listOf(normalized) + withoutSameIdentity)
    }

    fun merge(servers: List<SavedServer>): List<SavedServer> {
        val byIdentity = linkedMapOf<String, SavedServer>()
        servers.map(::normalize).forEach { server ->
            val existingKey = byIdentity.entries.firstOrNull { (_, existing) ->
                existing.id == server.id || existing.endpointKey == server.endpointKey
            }?.key
            if (existingKey == null) {
                byIdentity[server.id] = server
            } else {
                byIdentity[existingKey] = mergeServer(byIdentity.getValue(existingKey), server)
            }
        }
        return byIdentity.values.toList()
    }

    private fun mergeServer(primary: SavedServer, fallback: SavedServer): SavedServer = primary.copy(
        displayName = primary.displayName.takeIf { it.isNotBlank() } ?: fallback.displayName,
        username = primary.username ?: fallback.username,
        allowInsecure = primary.allowInsecure,
        pinned = primary.pinned || fallback.pinned,
        defaultWorkspace = primary.defaultWorkspace ?: fallback.defaultWorkspace,
        lastConnectedAt = listOfNotNull(primary.lastConnectedAt, fallback.lastConnectedAt).maxOrNull(),
    )
}

@Serializable
data class PersistedTabState(
    val version: Int = CURRENT_VERSION,
    val serverEndpointKey: String,
    val activeTabId: String?,
    val tabs: List<PersistedTab>,
) {
    companion object {
        const val CURRENT_VERSION = 3
    }
}

@Serializable
private data class LegacyPersistedTabState(
    val version: Int = 1,
    val serverEndpointKey: String,
    val activeTabId: String?,
    val tabs: List<LegacyPersistedTab>,
)

@Serializable
private data class LegacyPersistedTab(
    val id: String,
    val startRoute: String,
    val sessionId: String? = null,
    val sessionTitle: String? = null,
    val workspaceKey: PersistedWorkspaceKey? = null,
    val workspaceDirectory: String? = null,
) {
    fun resolvedWorkspaceKey(): PersistedWorkspaceKey? = workspaceKey
        ?: workspaceDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { PersistedWorkspaceKey(PersistedWorkspaceKey.Type.DIRECTORY, it) }
        ?: if (startRoute == "sessions") PersistedWorkspaceKey(PersistedWorkspaceKey.Type.GLOBAL) else null
}

@Serializable
data class PersistedTab(
    val id: String,
    val startRoute: String,
    val sessionId: String? = null,
    val sessionTitle: String? = null,
    val workspaceKey: PersistedWorkspaceKey? = null,
    val serverEndpointKey: String? = null,
) {
    fun resolvedWorkspaceKey(): WorkspaceKey? = workspaceKey?.toWorkspaceKey()
    fun resolvedServerEndpointKey(fallback: String? = null): String? = serverEndpointKey ?: fallback
}

@Serializable
data class PersistedWorkspaceKey(
    val type: Type,
    val value: String? = null,
) {
    enum class Type { GLOBAL, DIRECTORY, SESSION_SCOPED }

    fun toWorkspaceKey(): WorkspaceKey = when (type) {
        Type.GLOBAL -> WorkspaceKey.Global
        Type.DIRECTORY -> WorkspaceKey.Directory(
            requireNotNull(value) { "Directory workspace key requires a value" }
        )
        Type.SESSION_SCOPED -> WorkspaceKey.SessionScoped(
            SessionId(
                requireNotNull(value) { "Session-scoped workspace key requires a value" }
            )
        )
    }

    companion object {
        fun fromWorkspaceKey(workspaceKey: WorkspaceKey): PersistedWorkspaceKey = when (workspaceKey) {
            WorkspaceKey.Global -> PersistedWorkspaceKey(Type.GLOBAL)
            is WorkspaceKey.Directory -> PersistedWorkspaceKey(Type.DIRECTORY, workspaceKey.value)
            is WorkspaceKey.SessionScoped -> PersistedWorkspaceKey(Type.SESSION_SCOPED, workspaceKey.sessionId.value)
        }
    }
}

data class VisualSettings(
    val fontSize: Int = 14,
    val lineSpacing: Float = 1.5f,
    val fontFamily: String = "System",
    val codeBlockFontSize: Int = 12,
    val terminalFontSize: Int = TerminalFontSize.DEFAULT_SP,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val compactMode: Boolean = false,
    val messageSpacing: Int = 8,
    val highContrastMode: Boolean = false,
    val reasoningExpandedByDefault: Boolean = false,
    val toolWidgetDefaultState: String = "compact", // "oneline", "compact", or "expanded"
    val openSubAgentInNewTab: Boolean = true
)

data class ChatSettings(
    val enterToSend: Boolean = false,
)

data class NotificationSettings(
    val enabled: Boolean = true,
    val permissionRequests: Boolean = true,
    val questions: Boolean = true,
    val vibrationPattern: VibrationPattern = VibrationPattern.None,
    val notifyOnCompletion: Boolean = false,
    /** Per-server notification routing, keyed by endpointKey. Absent = All (design 18). */
    val serverRouting: Map<String, NotificationRoutingMode> = emptyMap(),
)

/**
 * Per-server notification routing (design 18).
 * - [All]: deliver every enabled notification type.
 * - [Mentions]: only agent-awaiting-input (permission / question); suppress turn-complete.
 * - [Off]: suppress all notifications for the server.
 */
enum class NotificationRoutingMode(val storageValue: String) {
    All("all"),
    Mentions("mentions"),
    Off("off");

    companion object {
        fun fromStorage(value: String?): NotificationRoutingMode =
            entries.firstOrNull { it.storageValue == value } ?: All
    }
}

enum class VibrationPattern(val storageValue: String) {
    None("none"),
    Tick("tick"),
    Click("click"),
    HeavyClick("heavy_click"),
    DoubleClick("double_click"),
    LongPulse("long_pulse"),
    DoubleLongPulse("double_long_pulse"),
}

fun String.toVibrationPattern(): VibrationPattern = VibrationPattern.entries
    .firstOrNull { it.storageValue == this }
    ?: VibrationPattern.None

data class ConnectionSettings(
    val autoReconnect: Boolean = true,
    val reconnectTimeoutSeconds: Int = 45
)
