package com.pocketcode.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_LAST_SESSION_ID = stringPreferencesKey("last_session_id")

        const val DEFAULT_LOCAL_URL = "http://localhost:4096"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
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

    suspend fun saveLastConnection(config: com.pocketcode.core.network.ServerConfig) {
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

    suspend fun getLastConnection(): com.pocketcode.core.network.ServerConfig? {
        val prefs = context.dataStore.data.first()
        val url = prefs[KEY_SERVER_URL] ?: return null
        return com.pocketcode.core.network.ServerConfig(
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
}
