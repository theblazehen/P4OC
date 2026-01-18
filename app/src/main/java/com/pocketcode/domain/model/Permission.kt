package com.pocketcode.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Permission(
    val id: String,
    val type: String,
    val sessionID: String,
    val messageID: String,
    val title: String,
    val metadata: JsonObject,
    val createdAt: Long
)

enum class PermissionResponse(val value: String) {
    ALLOW("allow"),
    DENY("deny"),
    ALWAYS("always"),
    NEVER("never")
}
