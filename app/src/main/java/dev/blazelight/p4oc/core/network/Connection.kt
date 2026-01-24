package dev.blazelight.p4oc.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val url: String,
    val name: String = "",
    val isLocal: Boolean = false,
    val username: String? = null
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
