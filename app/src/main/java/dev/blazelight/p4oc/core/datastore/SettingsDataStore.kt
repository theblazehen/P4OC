package dev.blazelight.p4oc.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.data.remote.dto.ModelInput
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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(removeDeadWorkspacePrefsMigration()) },
)

private const val TAG = "SettingsDataStore"

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
        
        const val DEFAULT_THEME_NAME = "catppuccin"
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_RECENT_SERVERS = stringPreferencesKey("recent_servers")
        private val KEY_TAB_STATE = stringPreferencesKey("tab_state_v1")
        
        // Visual settings keys
        private val KEY_FONT_SIZE = intPreferencesKey("font_size")
        private val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_CODE_BLOCK_FONT_SIZE = intPreferencesKey("code_block_font_size")
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
        private const val MAX_RECENT_MODELS = 10
        
        // Notification settings keys
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFY_PERMISSIONS = booleanPreferencesKey("notify_permissions")
        private val KEY_NOTIFY_QUESTIONS = booleanPreferencesKey("notify_questions")
        private val KEY_NOTIFY_VIBRATE_ON_COMPLETION = booleanPreferencesKey("notify_vibrate_on_completion")
        private val KEY_NOTIFY_VIBRATION_PATTERN = stringPreferencesKey("notify_vibration_pattern")
        private val KEY_NOTIFY_ON_COMPLETION = booleanPreferencesKey("notify_on_completion")

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
                AppLog.e(TAG, "Error during init", e)
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
    suspend fun setServerConfig(url: String, name: String, isLocal: Boolean, username: String? = null, password: String? = null) {
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
            AppLog.e(TAG, "Error parsing recent servers", e)
            emptyList()
        }
    }

    /**
     * Add a recent server. Password is stored in CredentialStore, not in the JSON.
     */
    suspend fun addRecentServer(url: String, name: String, username: String? = null, password: String? = null, allowInsecure: Boolean = false) {
        // Store password in encrypted storage (keyed by URL)
        if (password != null) {
            credentialStore.setServerPassword(url, password)
        }

        context.dataStore.edit { prefs ->
            val stored = prefs[KEY_RECENT_SERVERS] ?: ""
            val existingServers = if (stored.isBlank()) {
                mutableListOf()
            } else {
                try {
                    parseRecentServersLenient(stored).toMutableList()
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error parsing recent servers in addRecentServer", e)
                    mutableListOf()
                }
            }
            
            existingServers.removeAll { it.url == url }
            existingServers.add(0, RecentServer(url, name, username, allowInsecure))
            val trimmed = existingServers.take(MAX_RECENT_SERVERS)
            
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
                AppLog.e(TAG, "Error parsing recent servers in removeRecentServer", e)
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

    // ── Notification settings ──

    val notificationSettings: Flow<NotificationSettings> = context.dataStore.data.map { prefs ->
        NotificationSettings(
            enabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            permissionRequests = prefs[KEY_NOTIFY_PERMISSIONS] ?: true,
            questions = prefs[KEY_NOTIFY_QUESTIONS] ?: true,
            vibrationPattern = prefs[KEY_NOTIFY_VIBRATION_PATTERN]?.toVibrationPattern()
                ?: if (prefs[KEY_NOTIFY_VIBRATE_ON_COMPLETION] == true) VibrationPattern.Tick else VibrationPattern.None,
            notifyOnCompletion = prefs[KEY_NOTIFY_ON_COMPLETION] ?: false,
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

    private fun parsePersistedTabState(stored: String): PersistedTabState? = try {
        json.decodeFromString<PersistedTabState>(stored)
    } catch (e: Exception) {
        AppLog.w(TAG, "Ignoring invalid persisted tab state: ${e.message}")
        null
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
    } else null
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
data class PersistedTabState(
    val version: Int = CURRENT_VERSION,
    val serverEndpointKey: String,
    val activeTabId: String?,
    val tabs: List<PersistedTab>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class PersistedTab(
    val id: String,
    val startRoute: String,
    val sessionId: String? = null,
    val sessionTitle: String? = null,
    val workspaceDirectory: String? = null,
)

data class VisualSettings(
    val fontSize: Int = 14,
    val lineSpacing: Float = 1.5f,
    val fontFamily: String = "System",
    val codeBlockFontSize: Int = 12,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val compactMode: Boolean = false,
    val messageSpacing: Int = 8,
    val highContrastMode: Boolean = false,
    val reasoningExpandedByDefault: Boolean = false,
    val toolWidgetDefaultState: String = "compact", // "oneline", "compact", or "expanded"
    val openSubAgentInNewTab: Boolean = true
)

data class NotificationSettings(
    val enabled: Boolean = true,
    val permissionRequests: Boolean = true,
    val questions: Boolean = true,
    val vibrationPattern: VibrationPattern = VibrationPattern.None,
    val notifyOnCompletion: Boolean = false,
)

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
