package dev.blazelight.p4oc.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val url: String,
    val name: String = "",
    val isLocal: Boolean = false,
    val username: String? = null,
    // When true, skip TLS certificate and hostname verification.
    // Intended for users running self-signed certs on reverse proxies.
    val allowInsecure: Boolean = false
    // password REMOVED — stored exclusively in CredentialStore
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
    val api: OpenCodeApi,
    val eventSource: OpenCodeEventSource
) {
    fun disconnect() {
        eventSource.shutdown()
    }
}
