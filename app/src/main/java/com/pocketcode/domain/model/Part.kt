package com.pocketcode.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
        val isStreaming: Boolean = false,
        val synthetic: Boolean = false,
        val ignored: Boolean = false,
        val time: PartTime? = null,
        val metadata: JsonObject? = null
    ) : Part()

    @Serializable
    data class Reasoning(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val text: String,
        val time: PartTime? = null,
        val metadata: JsonObject? = null
    ) : Part()

    @Serializable
    data class Tool(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val callID: String,
        val toolName: String,
        val state: ToolState,
        val metadata: JsonObject? = null
    ) : Part()

    @Serializable
    data class File(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val mime: String,
        val filename: String?,
        val url: String,
        val source: FilePartSource? = null
    ) : Part()

    @Serializable
    data class Patch(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val hash: String,
        val files: List<String>
    ) : Part()

    @Serializable
    data class StepStart(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val snapshot: String? = null
    ) : Part()

    @Serializable
    data class StepFinish(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val reason: String,
        val snapshot: String? = null,
        val cost: Double = 0.0,
        val tokens: TokenUsage? = null
    ) : Part()

    @Serializable
    data class Snapshot(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val snapshot: String
    ) : Part()

    @Serializable
    data class Agent(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val name: String,
        val source: AgentPartSource? = null
    ) : Part()

    @Serializable
    data class Retry(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val attempt: Int,
        val error: ApiError?,
        val createdAt: Long? = null
    ) : Part()

    @Serializable
    data class Compaction(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val auto: Boolean
    ) : Part()

    @Serializable
    data class Subtask(
        override val id: String,
        override val sessionID: String,
        override val messageID: String,
        val prompt: String,
        val description: String,
        val agent: String
    ) : Part()
}

@Serializable
data class PartTime(
    val start: Long,
    val end: Long? = null
)

@Serializable
sealed class FilePartSource {
    @Serializable
    data class FileSource(
        val text: FilePartSourceText,
        val path: String
    ) : FilePartSource()

    @Serializable
    data class SymbolSource(
        val text: FilePartSourceText,
        val path: String,
        val range: SymbolRange,
        val name: String,
        val kind: Int
    ) : FilePartSource()
}

@Serializable
data class FilePartSourceText(
    val value: String,
    val start: Int,
    val end: Int
)

@Serializable
data class AgentPartSource(
    val value: String,
    val start: Int,
    val end: Int
)
