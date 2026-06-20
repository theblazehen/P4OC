package dev.blazelight.p4oc.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted credential storage backed by EncryptedSharedPreferences.
 *
 * All passwords are stored encrypted using AES256-GCM with keys managed
 * by the Android Keystore. Credentials are keyed by server URL to support
 * multiple saved servers.
 *
 * This is the SOLE authority for password storage. No passwords should be
 * persisted in DataStore, ServerConfig, or RecentServer JSON.
 */
class CredentialStore(context: Context) {

    companion object {
        private const val FILE_NAME = "p4oc_credentials"
        private const val KEY_ACTIVE_PASSWORD = "active_password"
        private fun serverPasswordKey(url: String): String = "server_password:$url"
    }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val keyActiveOidc = "active_oidc"
    private fun serverOidcKey(url: String): String = "server_oidc:$url"

    private fun putOrRemove(key: String, value: String?) {
        prefs.edit().apply {
            if (value != null) putString(key, value) else remove(key)
            apply()
        }
    }

    // ── Active connection password ──────────────────────────────────────

    /**
     * Store the password for the currently active connection.
     * This is the password used by ConnectionManager for auth interceptors.
     */
    fun setActivePassword(password: String?) {
        prefs.edit().apply {
            if (password != null) {
                putString(KEY_ACTIVE_PASSWORD, password)
            } else {
                remove(KEY_ACTIVE_PASSWORD)
            }
            apply()
        }
    }

    /**
     * Get the active connection password. Used during auto-reconnect
     * and by ConnectionManager to build auth interceptors.
     */
    fun getActivePassword(): String? = prefs.getString(KEY_ACTIVE_PASSWORD, null)

    /**
     * Clear the active connection password (e.g., on disconnect or logout).
     */
    fun clearActivePassword() {
        prefs.edit().remove(KEY_ACTIVE_PASSWORD).apply()
    }

    // ── Per-server passwords (for recent servers) ───────────────────────

    /**
     * Store a password associated with a specific server URL.
     * Used for the recent servers list so users can reconnect without re-entering.
     */
    fun setServerPassword(serverUrl: String, password: String?) {
        prefs.edit().apply {
            val key = serverPasswordKey(serverUrl)
            if (password != null) {
                putString(key, password)
            } else {
                remove(key)
            }
            apply()
        }
    }

    /**
     * Retrieve the stored password for a specific server URL.
     */
    fun getServerPassword(serverUrl: String): String? {
        return prefs.getString(serverPasswordKey(serverUrl), null)
    }

    /**
     * Remove stored password for a server (e.g., when removing from recent servers list).
     */
    fun removeServerPassword(serverUrl: String) {
        prefs.edit().remove(serverPasswordKey(serverUrl)).apply()
    }

    // ── OIDC AuthState (OAuth2) ─────────────────────────────────────────
    // Stores the serialized AppAuth state (access/refresh material + endpoints), encrypted at rest.

    fun setActiveOidc(state: String?) = putOrRemove(keyActiveOidc, state)

    fun getActiveOidc(): String? = prefs.getString(keyActiveOidc, null)

    fun clearActiveOidc() = putOrRemove(keyActiveOidc, null)

    fun setServerOidc(serverUrl: String, state: String?) = putOrRemove(serverOidcKey(serverUrl), state)

    fun getServerOidc(serverUrl: String): String? = prefs.getString(serverOidcKey(serverUrl), null)

    fun removeServerOidc(serverUrl: String) = putOrRemove(serverOidcKey(serverUrl), null)

    /**
     * Clear all stored credentials. Used for logout/clear-all scenarios.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
