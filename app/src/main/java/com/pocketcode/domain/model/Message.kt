package com.pocketcode.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class Message {
    abstract val id: String
    abstract val sessionID: String
    abstract val createdAt: Instant

    @Serializable
    data class User(
        override val id: String,
        override val sessionID: String,
        override val createdAt: Instant,
        val agent: String,
        val model: ModelRef
    ) : Message()

    @Serializable
    data class Assistant(
        override val id: String,
        override val sessionID: String,
        override val createdAt: Instant,
        val completedAt: Instant? = null,
        val parentID: String,
        val providerID: String,
        val modelID: String,
        val agent: String,
        val cost: Double,
        val tokens: TokenUsage,
        val error: MessageError? = null
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
    val code: String,
    val message: String
)

data class MessageWithParts(
    val message: Message,
    val parts: List<Part>
)
