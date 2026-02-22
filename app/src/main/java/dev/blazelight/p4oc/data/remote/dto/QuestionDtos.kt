package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// Question Types
// ============================================================================

@Serializable
data class QuestionRequestDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    val questions: List<QuestionDto>,
    val tool: QuestionToolRefDto? = null
)

@Serializable
data class QuestionToolRefDto(
    @SerialName("messageID") val messageID: String,
    @SerialName("callID") val callID: String
)

@Serializable
data class QuestionDto(
    val header: String,
    val question: String,
    val options: List<QuestionOptionDto>,
    val multiple: Boolean = false,
    val custom: Boolean = true
)

@Serializable
data class QuestionOptionDto(
    val label: String,
    val description: String
)

@Serializable
data class QuestionReplyRequest(
    val answers: List<List<String>>
)
