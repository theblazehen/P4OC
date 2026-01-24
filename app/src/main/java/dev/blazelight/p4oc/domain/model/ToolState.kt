package dev.blazelight.p4oc.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class ToolState {
    abstract val input: JsonObject

    @Serializable
    data class Pending(
        override val input: JsonObject,
        val rawInput: String
    ) : ToolState()

    @Serializable
    data class Running(
        override val input: JsonObject,
        val title: String?,
        val startedAt: Long,
        val metadata: JsonObject? = null
    ) : ToolState()

    @Serializable
    data class Completed(
        override val input: JsonObject,
        val output: String,
        val title: String,
        val startedAt: Long,
        val endedAt: Long,
        val compactedAt: Long? = null,
        val metadata: JsonObject? = null,
        val attachments: List<Part>? = null
    ) : ToolState()

    @Serializable
    data class Error(
        override val input: JsonObject,
        val error: String,
        val startedAt: Long,
        val endedAt: Long,
        val metadata: JsonObject? = null
    ) : ToolState()
}
