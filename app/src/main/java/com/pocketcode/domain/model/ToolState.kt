package com.pocketcode.domain.model

import kotlinx.datetime.Instant
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
        val startedAt: Instant
    ) : ToolState()

    @Serializable
    data class Completed(
        override val input: JsonObject,
        val output: String,
        val title: String,
        val startedAt: Instant,
        val endedAt: Instant,
        val metadata: JsonObject? = null
    ) : ToolState()

    @Serializable
    data class Error(
        override val input: JsonObject,
        val error: String,
        val startedAt: Instant,
        val endedAt: Instant
    ) : ToolState()
}
