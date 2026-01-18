package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class Part {
    abstract val id: String
    abstract val sessionID: String
    abstract val messageID: String

    @Serializable
    data class Text(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val text: String,
        val isStreaming: Boolean = false
    ) : Part()

    @Serializable
    data class Reasoning(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val text: String
    ) : Part()

    @Serializable
    data class Tool(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val callID: String,
        val toolName: String,
        val state: ToolState
    ) : Part()

    @Serializable
    data class File(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val mime: String,
        val filename: String?,
        val url: String
    ) : Part()

    @Serializable
    data class Patch(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val hash: String,
        val files: List<String>
    ) : Part()
}
