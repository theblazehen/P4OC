package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class Message {
    abstract val id: String
    abstract val sessionID: String
    abstract val createdAt: Long

    @Serializable
    data class User(
        override val id: String,
        override val sessionID: String,
        override val createdAt: Long,
        val agent: String,
        val model: ModelRef,
        val summary: MessageSummary? = null,
        val system: String? = null,
        val tools: Map<String, Boolean>? = null
    ) : Message()

    @Serializable
    data class Assistant(
        override val id: String,
        override val sessionID: String,
        override val createdAt: Long,
        val completedAt: Long? = null,
        val parentID: String,
        val providerID: String,
        val modelID: String,
        val mode: String,
        val agent: String,
        val cost: Double,
        val tokens: TokenUsage,
        val path: MessagePath? = null,
        val error: MessageError? = null,
        val finish: String? = null,
        val summary: Boolean? = null
    ) : Message()
}

@Serializable
data class ModelRef(
    val providerID: String,
    val modelID: String
)

@Serializable
data class TokenUsage(
    val input: Int,
    val output: Int,
    val reasoning: Int = 0,
    val cacheRead: Int = 0,
    val cacheWrite: Int = 0
)

@Serializable
data class MessageError(
    val name: String,
    val message: String? = null,
    val statusCode: Int? = null,
    val isRetryable: Boolean = false
)

@Serializable
data class ApiError(
    val message: String,
    val statusCode: Int? = null,
    val isRetryable: Boolean = false,
    val responseHeaders: Map<String, String>? = null,
    val responseBody: String? = null
)

data class MessageWithParts(
    val message: Message,
    val parts: List<Part>
)

@Serializable
data class MessageSummary(
    val title: String? = null,
    val body: String? = null,
    val diffs: List<FileDiff> = emptyList()
)

@Serializable
data class MessagePath(
    val cwd: String,
    val root: String
)
