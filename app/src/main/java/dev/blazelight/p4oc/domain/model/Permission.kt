package dev.blazelight.p4oc.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Permission(
    val id: String,
    val type: String,
    val patterns: List<String>,
    val sessionID: String,
    val messageID: String,
    val callID: String? = null,
    val title: String,
    val metadata: JsonObject,
    val always: List<String>
)

enum class PermissionResponse(val value: String) {
    ONCE("once"),
    REJECT("reject"),
    ALWAYS("always")
}
