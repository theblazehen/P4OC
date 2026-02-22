package dev.blazelight.p4oc.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.blazelight.p4oc.core.log.AppLog

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
        private const val TAG = "CredentialStore"
        private const val FILE_NAME = "p4oc_credentials"
        private const val KEY_ACTIVE_PASSWORD = "active_password"
        private fun serverPasswordKey(url: String): String = "server_password:$url"
    }

    private val prefs: SharedPreferences = try {
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
    } catch (e: Exception) {
        // If Keystore is corrupted (rare, e.g., after OS upgrade), fall back
        // to wiping and recreating. User will need to re-enter passwords.
        AppLog.e(TAG, "Failed to open EncryptedSharedPreferences, recreating", e)
        context.deleteSharedPreferences(FILE_NAME)
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

    // ── Migration ───────────────────────────────────────────────────────

    /**
     * Migrate a plaintext password from DataStore into encrypted storage.
     * Idempotent: if the active password is already set, this is a no-op.
     *
     * @param plaintextPassword the password previously stored in DataStore
     * @param serverUrl optional server URL to also store as a per-server password
     */
    fun migrateFromPlaintext(plaintextPassword: String?, serverUrl: String?) {
        if (plaintextPassword.isNullOrBlank()) return

        // Only migrate if there's no active password yet (idempotent)
        if (getActivePassword() == null) {
            AppLog.d(TAG, "Migrating active password to encrypted storage")
            setActivePassword(plaintextPassword)
        }

        // Also store as per-server password if URL is provided
        if (serverUrl != null && getServerPassword(serverUrl) == null) {
            AppLog.d(TAG, "Migrating server password to encrypted storage")
            setServerPassword(serverUrl, plaintextPassword)
        }
    }

    /**
     * Migrate passwords from RecentServer entries.
     * Called during the migration phase — extracts passwords from the legacy
     * JSON entries and stores them encrypted, keyed by server URL.
     */
    fun migrateRecentServerPassword(serverUrl: String, plaintextPassword: String?) {
        if (plaintextPassword.isNullOrBlank()) return
        if (getServerPassword(serverUrl) == null) {
            AppLog.d(TAG, "Migrating recent server password")
            setServerPassword(serverUrl, plaintextPassword)
        }
    }

    /**
     * Clear all stored credentials. Used for logout/clear-all scenarios.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
