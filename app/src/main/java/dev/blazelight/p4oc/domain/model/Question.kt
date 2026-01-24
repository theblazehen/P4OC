package dev.blazelight.p4oc.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QuestionRequest(
    val id: String,
    val sessionID: String,
    val questions: List<Question>,
    val tool: QuestionToolRef? = null
)

@Serializable
data class QuestionToolRef(
    val messageID: String,
    val callID: String
)

@Serializable
data class Question(
    val header: String,
    val question: String,
    val options: List<QuestionOption>,
    val multiple: Boolean = false,
    val custom: Boolean = true
)

@Serializable
data class QuestionOption(
    val label: String,
    val description: String
)

@Serializable
data class QuestionReply(
    val answers: List<List<String>>
)

data class QuestionData(
    val questions: List<Question>
)
