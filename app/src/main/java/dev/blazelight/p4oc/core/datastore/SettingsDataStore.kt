package dev.blazelight.p4oc.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.security.CredentialStore
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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
        // KEY_PASSWORD intentionally removed — migrated to CredentialStore
        @Deprecated("Only used for migration detection and removal")
        private val KEY_PASSWORD_LEGACY = stringPreferencesKey("password")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_THEME_NAME = stringPreferencesKey("theme_name")
        
        const val DEFAULT_THEME_NAME = "catppuccin"
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_LAST_SESSION_ID = stringPreferencesKey("last_session_id")
        private val KEY_RECENT_SERVERS = stringPreferencesKey("recent_servers")
        
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
        
        // Model favorites and recents
        private val KEY_FAVORITE_MODELS = stringSetPreferencesKey("favorite_models")
        private val KEY_RECENT_MODELS = stringPreferencesKey("recent_models")
        private const val MAX_RECENT_MODELS = 10
        
        // Project directory persistence
        private val KEY_PROJECT_WORKTREE = stringPreferencesKey("project_worktree")

        const val DEFAULT_LOCAL_URL = "http://localhost:4096"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val MAX_RECENT_SERVERS = 5

        // Migration flag
        private val KEY_CREDENTIALS_MIGRATED = booleanPreferencesKey("credentials_migrated_v1")
    }

    @Volatile
    private var cachedServerUrl: String = DEFAULT_LOCAL_URL
    @Volatile
    private var cachedUsername: String? = null

    init {
        // Preload cache from DataStore + run one-time credential migration
        runBlocking {
            val prefs = context.dataStore.data.first()
            cachedServerUrl = prefs[KEY_SERVER_URL] ?: DEFAULT_LOCAL_URL
            cachedUsername = prefs[KEY_USERNAME]

            // One-time migration from plaintext DataStore to CredentialStore
            @Suppress("DEPRECATION")
            if (prefs[KEY_CREDENTIALS_MIGRATED] != true) {
                migrateCredentials(prefs)
            }
        }
    }

    /**
     * Migrate plaintext passwords from DataStore to CredentialStore.
     * Idempotent: checks KEY_CREDENTIALS_MIGRATED flag before acting.
     */
    @Suppress("DEPRECATION")
    private suspend fun migrateCredentials(prefs: Preferences) {
        AppLog.d(TAG, "Starting credential migration to encrypted storage")

        // 1. Migrate the active/last-connection password
        val legacyPassword = prefs[KEY_PASSWORD_LEGACY]
        val serverUrl = prefs[KEY_SERVER_URL]
        credentialStore.migrateFromPlaintext(legacyPassword, serverUrl)

        // 2. Migrate recent server passwords
        val storedServers = prefs[KEY_RECENT_SERVERS] ?: ""
        if (storedServers.startsWith("[")) {
            try {
                val jsonItems = storedServers.removeSurrounding("[", "]")
                    .split("},")
                    .map { it.trim().removeSuffix("}") + "}" }
                jsonItems.forEach { json ->
                    val server = RecentServer.fromJson(json)
                    if (server != null && !server.password.isNullOrBlank()) {
                        credentialStore.migrateRecentServerPassword(server.url, server.password)
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Error parsing recent servers during migration", e)
            }
        }

        // 3. Strip passwords from recent servers JSON and remove legacy password key
        context.dataStore.edit { mutablePrefs ->
            // Remove plaintext password
            mutablePrefs.remove(KEY_PASSWORD_LEGACY)

            // Rewrite recent servers without passwords
            val stored = mutablePrefs[KEY_RECENT_SERVERS] ?: ""
            if (stored.startsWith("[")) {
                try {
                    val jsonItems = stored.removeSurrounding("[", "]")
                        .split("},")
                        .map { it.trim().removeSuffix("}") + "}" }
                    val cleaned = jsonItems.mapNotNull { RecentServer.fromJson(it) }
                        .map { it.copy(password = null) }
                    mutablePrefs[KEY_RECENT_SERVERS] = "[" + cleaned.joinToString(",") { it.toJson() } + "]"
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error cleaning recent servers during migration", e)
                }
            }

            // Mark migration as done
            mutablePrefs[KEY_CREDENTIALS_MIGRATED] = true
        }

        AppLog.d(TAG, "Credential migration complete")
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

    val lastSessionId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SESSION_ID]
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

    suspend fun setLastSessionId(sessionId: String?) {
        context.dataStore.edit { prefs ->
            if (sessionId != null) {
                prefs[KEY_LAST_SESSION_ID] = sessionId
            } else {
                prefs.remove(KEY_LAST_SESSION_ID)
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
            username = prefs[KEY_USERNAME]
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
        }
        credentialStore.clearActivePassword()
    }

    val recentServers: Flow<List<RecentServer>> = context.dataStore.data.map { prefs ->
        val stored = prefs[KEY_RECENT_SERVERS] ?: return@map emptyList()
        try {
            if (stored.startsWith("[")) {
                val jsonItems = stored.removeSurrounding("[", "]")
                    .split("},")
                    .map { it.trim().removeSuffix("}") + "}" }
                jsonItems.mapNotNull { RecentServer.fromJson(it) }
            } else {
                // Fallback to legacy delimiter format for migration
                stored.split("|||").mapNotNull { entry ->
                    val parts = entry.split(":::")
                    if (parts.size >= 2) {
                        RecentServer(url = parts[0], name = parts[1])
                    } else null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add a recent server. Password is stored in CredentialStore, not in the JSON.
     */
    suspend fun addRecentServer(url: String, name: String, username: String? = null, password: String? = null) {
        // Store password in encrypted storage (keyed by URL)
        if (password != null) {
            credentialStore.setServerPassword(url, password)
        }

        context.dataStore.edit { prefs ->
            val stored = prefs[KEY_RECENT_SERVERS] ?: ""
            val existingServers = if (stored.isBlank()) {
                mutableListOf()
            } else if (stored.startsWith("[")) {
                val jsonItems = stored.removeSurrounding("[", "]")
                    .split("},")
                    .map { it.trim().removeSuffix("}") + "}" }
                jsonItems.mapNotNull { RecentServer.fromJson(it) }.toMutableList()
            } else {
                // Legacy format migration
                stored.split("|||").mapNotNull { entry ->
                    val parts = entry.split(":::")
                    if (parts.size >= 2) RecentServer(parts[0], parts[1]) else null
                }.toMutableList()
            }
            
            existingServers.removeAll { it.url == url }
            // Note: password=null in RecentServer — it's in CredentialStore now
            existingServers.add(0, RecentServer(url, name, username, password = null))
            val trimmed = existingServers.take(MAX_RECENT_SERVERS)
            
            // Save as JSON array (no passwords in JSON)
            prefs[KEY_RECENT_SERVERS] = "[" + trimmed.joinToString(",") { it.toJson() } + "]"
        }
    }

    suspend fun removeRecentServer(url: String) {
        // Remove the associated password from encrypted storage
        credentialStore.removeServerPassword(url)

        context.dataStore.edit { prefs ->
            val stored = prefs[KEY_RECENT_SERVERS] ?: return@edit
            val servers = if (stored.startsWith("[")) {
                val jsonItems = stored.removeSurrounding("[", "]")
                    .split("},")
                    .map { it.trim().removeSuffix("}") + "}" }
                jsonItems.mapNotNull { RecentServer.fromJson(it) }.filter { it.url != url }
            } else {
                stored.split("|||").mapNotNull { entry ->
                    val parts = entry.split(":::")
                    if (parts.size >= 2 && parts[0] != url) RecentServer(parts[0], parts[1]) else null
                }
            }
            prefs[KEY_RECENT_SERVERS] = "[" + servers.joinToString(",") { it.toJson() } + "]"
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
            toolWidgetDefaultState = prefs[KEY_TOOL_WIDGET_DEFAULT_STATE] ?: "compact"
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

    // Project directory persistence
    val projectWorktree: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROJECT_WORKTREE]
    }

    suspend fun getProjectWorktree(): String? {
        return context.dataStore.data.first()[KEY_PROJECT_WORKTREE]
    }

    suspend fun setProjectWorktree(worktree: String?) {
        context.dataStore.edit { prefs ->
            if (worktree != null) {
                prefs[KEY_PROJECT_WORKTREE] = worktree
            } else {
                prefs.remove(KEY_PROJECT_WORKTREE)
            }
        }
    }
}

private fun ModelInput.toStorageKey(): String = "$providerID/$modelID"

private fun String.toModelInput(): ModelInput? {
    val parts = split("/", limit = 2)
    return if (parts.size >= 2) {
        ModelInput(providerID = parts[0], modelID = parts.drop(1).joinToString("/"))
    } else null
}

data class RecentServer(
    val url: String,
    val name: String,
    val username: String? = null,
    val password: String? = null  // DEPRECATED: kept for migration parsing only, never written
) {
    fun toJson(): String {
        val parts = mutableListOf(
            """"url":"${url.replace("\"", "\\\"")}"""",
            """"name":"${name.replace("\"", "\\\"")}""""
        )
        username?.let { parts.add(""""username":"${it.replace("\"", "\\\"")}"""") }
        // password intentionally NOT serialized to JSON
        return "{${parts.joinToString(",")}}"
    }
    
    companion object {
        fun fromJson(json: String): RecentServer? {
            return try {
                val urlMatch = """"url"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(json)
                val nameMatch = """"name"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(json)
                val usernameMatch = """"username"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(json)
                // Still parse password for migration purposes
                val passwordMatch = """"password"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(json)
                if (urlMatch != null && nameMatch != null) {
                    RecentServer(
                        url = urlMatch.groupValues[1].replace("\\\"", "\""),
                        name = nameMatch.groupValues[1].replace("\\\"", "\""),
                        username = usernameMatch?.groupValues?.get(1)?.replace("\\\"", "\""),
                        password = passwordMatch?.groupValues?.get(1)?.replace("\\\"", "\"")
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

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
    val toolWidgetDefaultState: String = "compact" // "oneline", "compact", or "expanded"
)
