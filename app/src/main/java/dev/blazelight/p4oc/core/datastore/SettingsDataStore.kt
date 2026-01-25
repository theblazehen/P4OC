package dev.blazelight.p4oc.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_SERVER_NAME = stringPreferencesKey("server_name")
        private val KEY_IS_LOCAL_SERVER = booleanPreferencesKey("is_local_server")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PASSWORD = stringPreferencesKey("password")
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
        private val KEY_TOOL_CALLS_EXPANDED = booleanPreferencesKey("tool_calls_expanded_by_default")
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
    }

    @Volatile
    private var cachedServerUrl: String = DEFAULT_LOCAL_URL
    @Volatile
    private var cachedUsername: String? = null
    @Volatile
    private var cachedPassword: String? = null

    fun getCachedServerUrl(): String = cachedServerUrl
    fun getCachedUsername(): String? = cachedUsername
    fun getCachedPassword(): String? = cachedPassword

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

    val password: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PASSWORD].also { cachedPassword = it }
    }

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

    suspend fun setCredentials(username: String?, password: String?) {
        context.dataStore.edit { prefs ->
            if (username != null) {
                prefs[KEY_USERNAME] = username
            } else {
                prefs.remove(KEY_USERNAME)
            }
            if (password != null) {
                prefs[KEY_PASSWORD] = password
            } else {
                prefs.remove(KEY_PASSWORD)
            }
        }
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

    suspend fun setServerConfig(url: String, name: String, isLocal: Boolean, username: String? = null, password: String? = null) {
        cachedServerUrl = url
        cachedUsername = username
        cachedPassword = password
        
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
            prefs[KEY_SERVER_NAME] = name
            prefs[KEY_IS_LOCAL_SERVER] = isLocal
            if (username != null) prefs[KEY_USERNAME] = username else prefs.remove(KEY_USERNAME)
            if (password != null) prefs[KEY_PASSWORD] = password else prefs.remove(KEY_PASSWORD)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun saveLastConnection(config: dev.blazelight.p4oc.core.network.ServerConfig) {
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
    }

    suspend fun getLastConnection(): dev.blazelight.p4oc.core.network.ServerConfig? {
        val prefs = context.dataStore.data.first()
        val url = prefs[KEY_SERVER_URL] ?: return null
        return dev.blazelight.p4oc.core.network.ServerConfig(
            url = url,
            name = prefs[KEY_SERVER_NAME] ?: "",
            isLocal = prefs[KEY_IS_LOCAL_SERVER] ?: false,
            username = prefs[KEY_USERNAME]
        )
    }

    suspend fun clearLastConnection() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SERVER_URL)
            prefs.remove(KEY_SERVER_NAME)
            prefs.remove(KEY_IS_LOCAL_SERVER)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_PASSWORD)
        }
    }

    val recentServers: Flow<List<RecentServer>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_RECENT_SERVERS] ?: return@map emptyList()
        try {
            json.split("|||").mapNotNull { entry ->
                val parts = entry.split(":::")
                if (parts.size >= 2) {
                    RecentServer(url = parts[0], name = parts[1])
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRecentServer(url: String, name: String) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[KEY_RECENT_SERVERS] ?: ""
            val existingServers = if (existingJson.isBlank()) {
                mutableListOf()
            } else {
                existingJson.split("|||").mapNotNull { entry ->
                    val parts = entry.split(":::")
                    if (parts.size >= 2) RecentServer(parts[0], parts[1]) else null
                }.toMutableList()
            }
            
            existingServers.removeAll { it.url == url }
            existingServers.add(0, RecentServer(url, name))
            val trimmed = existingServers.take(MAX_RECENT_SERVERS)
            
            prefs[KEY_RECENT_SERVERS] = trimmed.joinToString("|||") { "${it.url}:::${it.name}" }
        }
    }

    suspend fun removeRecentServer(url: String) {
        context.dataStore.edit { prefs ->
            val existingJson = prefs[KEY_RECENT_SERVERS] ?: return@edit
            val servers = existingJson.split("|||").mapNotNull { entry ->
                val parts = entry.split(":::")
                if (parts.size >= 2 && parts[0] != url) RecentServer(parts[0], parts[1]) else null
            }
            prefs[KEY_RECENT_SERVERS] = servers.joinToString("|||") { "${it.url}:::${it.name}" }
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
            toolCallsExpandedByDefault = prefs[KEY_TOOL_CALLS_EXPANDED] ?: false,
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
            prefs[KEY_TOOL_CALLS_EXPANDED] = settings.toolCallsExpandedByDefault
            prefs[KEY_TOOL_WIDGET_DEFAULT_STATE] = settings.toolWidgetDefaultState
        }
    }

    val favoriteModels: Flow<Set<ModelInput>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_FAVORITE_MODELS] ?: emptySet()).mapNotNull { it.toModelInput() }.toSet()
    }

    val recentModels: Flow<List<ModelInput>> = context.dataStore.data.map { prefs ->
        prefs[KEY_RECENT_MODELS]?.split("|||")?.filter { it.isNotBlank() }?.mapNotNull { it.toModelInput() } ?: emptyList()
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
            val existing = prefs[KEY_RECENT_MODELS]?.split("|||")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
            existing.remove(key)
            existing.add(0, key)
            prefs[KEY_RECENT_MODELS] = existing.take(MAX_RECENT_MODELS).joinToString("|||")
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
    val name: String
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
    val toolCallsExpandedByDefault: Boolean = false,
    val toolWidgetDefaultState: String = "compact" // "oneline", "compact", or "expanded"
)
