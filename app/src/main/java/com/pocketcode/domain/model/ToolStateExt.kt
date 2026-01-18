package com.pocketcode.domain.model

import kotlinx.serialization.json.*

fun ToolState.asQuestionData(): QuestionData? {
    return try {
        val questionsElement = input["questions"] ?: return null
        
        if (questionsElement !is JsonArray) return null
        
        val questions = questionsElement.mapNotNull { questionElement ->
            parseQuestion(questionElement as? JsonObject ?: return@mapNotNull null)
        }
        
        if (questions.isEmpty()) null else QuestionData(questions)
    } catch (e: Exception) {
        null
    }
}

private fun parseQuestion(json: JsonObject): Question? {
    return try {
        val header = json["header"]?.jsonPrimitive?.content ?: return null
        val question = json["question"]?.jsonPrimitive?.content ?: return null
        val optionsArray = json["options"]?.jsonArray ?: return null
        val multiple = json["multiple"]?.jsonPrimitive?.booleanOrNull ?: false
        
        val options = optionsArray.mapNotNull { optionElement ->
            parseQuestionOption(optionElement as? JsonObject ?: return@mapNotNull null)
        }
        
        if (options.isEmpty()) return null
        
        Question(
            header = header,
            question = question,
            options = options,
            multiple = multiple
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseQuestionOption(json: JsonObject): QuestionOption? {
    return try {
        val label = json["label"]?.jsonPrimitive?.content ?: return null
        val description = json["description"]?.jsonPrimitive?.content ?: return null
        
        QuestionOption(
            label = label,
            description = description
        )
    } catch (e: Exception) {
        null
    }
}

fun Part.Tool.isQuestionTool(): Boolean = toolName == "question"

fun Part.Tool.getQuestionData(): QuestionData? {
    if (!isQuestionTool()) return null
    return state.asQuestionData()
}
