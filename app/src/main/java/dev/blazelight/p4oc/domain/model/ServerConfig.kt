package dev.blazelight.p4oc.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val id: String = "",
    val url: String,
    val name: String,
    val username: String? = null,
    val password: String? = null,
    val isLocal: Boolean = false,
    val lastConnected: Instant? = null
) {
    companion object {
        const val DEFAULT_LOCAL_URL = "http://localhost:4096"
        const val DEFAULT_LOCAL_NAME = "Local (Termux)"
        
        fun local(port: Int = 4096) = ServerConfig(
            id = "local",
            url = "http://localhost:$port",
            name = DEFAULT_LOCAL_NAME,
            isLocal = true
        )
    }
}
