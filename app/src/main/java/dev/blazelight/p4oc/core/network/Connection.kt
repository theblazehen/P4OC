package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.domain.server.ServerGeneration
import kotlinx.serialization.Serializable

/**
 * How requests to this server authenticate.
 *
 * - [BASIC]: username + password (HTTP Basic), the original opencode `OPENCODE_SERVER_PASSWORD` mode.
 * - [OIDC]: OAuth2 Authorization Code + PKCE; the (auto-refreshed) access token is sent as a
 *   bearer credential. Lets opencode sit behind an auth proxy / identity provider.
 */
@Serializable
enum class AuthMode { BASIC, OIDC }

@Serializable
data class ServerConfig(
    val url: String,
    val name: String = "",
    val isLocal: Boolean = false,
    val username: String? = null,
    // When true, skip TLS certificate and hostname verification.
    // Intended for users running self-signed certs on reverse proxies.
    val allowInsecure: Boolean = false,
    // password REMOVED — stored exclusively in CredentialStore
    // Auth — default BASIC keeps existing (username+password) behaviour for old saved configs.
    val authMode: AuthMode = AuthMode.BASIC,
    // OIDC (only when authMode == OIDC); non-secret config. Tokens live in CredentialStore.
    val oidcIssuer: String? = null,
    val oidcClientId: String? = null
) {
    companion object {
        val LOCAL_DEFAULT = ServerConfig(
            url = "http://localhost:4096",
            name = "Local (Termux)",
            isLocal = true
        )
    }
}

data class Connection(
    val config: ServerConfig,
    val generation: ServerGeneration,
    val api: OpenCodeApi,
    val eventSource: OpenCodeEventSource
) {
    fun disconnect() {
        eventSource.shutdown()
    }
}
