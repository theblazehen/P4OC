package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.Serializable

// ============================================================================
// Auth Types
// ============================================================================

@Serializable
data class OAuthDto(
    val type: String = "oauth",
    val refresh: String,
    val access: String,
    val expires: Long,
    val enterpriseUrl: String? = null
)

@Serializable
data class ApiAuthDto(
    val type: String = "api",
    val key: String
)

@Serializable
data class WellKnownAuthDto(
    val type: String = "wellknown",
    val key: String,
    val token: String
)

@Serializable
data class AuthDto(
    val type: String,
    val refresh: String? = null,
    val access: String? = null,
    val expires: Long? = null,
    val enterpriseUrl: String? = null,
    val key: String? = null,
    val token: String? = null
)

@Serializable
data class OAuthCallbackRequest(
    val code: String? = null,
    val state: String? = null
)
