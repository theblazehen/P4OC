package com.pocketcode.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// Provider
// ============================================================================

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val source: String,
    val env: List<String> = emptyList(),
    val key: String? = null,
    val options: JsonObject? = null,
    val models: Map<String, Model> = emptyMap()
)

@Serializable
data class Model(
    val id: String,
    val providerID: String,
    val name: String,
    val api: ModelApi? = null,
    val capabilities: ModelCapabilities? = null,
    val cost: ModelCost? = null,
    val limit: ModelLimit? = null,
    val status: String? = null,
    val options: JsonObject? = null,
    val headers: Map<String, String>? = null
)

@Serializable
data class ModelApi(
    val id: String,
    val url: String,
    val npm: String
)

@Serializable
data class ModelCapabilities(
    val temperature: Boolean = false,
    val reasoning: Boolean = false,
    val attachment: Boolean = false,
    val toolcall: Boolean = false,
    val inputModalities: Modalities? = null,
    val outputModalities: Modalities? = null
)

@Serializable
data class Modalities(
    val text: Boolean = true,
    val audio: Boolean = false,
    val image: Boolean = false,
    val video: Boolean = false,
    val pdf: Boolean = false
)

@Serializable
data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cacheRead: Double = 0.0,
    val cacheWrite: Double = 0.0
)

@Serializable
data class ModelLimit(
    val context: Int = 0,
    val output: Int = 0
)

// ============================================================================
// Agent
// ============================================================================

@Serializable
data class Agent(
    val name: String,
    val description: String? = null,
    val mode: String,
    val builtIn: Boolean = false,
    val topP: Double? = null,
    val temperature: Double? = null,
    val color: String? = null,
    val permission: AgentPermission? = null,
    val model: ModelRef? = null,
    val prompt: String? = null,
    val tools: Map<String, Boolean> = emptyMap(),
    val maxSteps: Int? = null
)

@Serializable
data class AgentPermission(
    val edit: String? = null,
    val bash: JsonElement? = null,
    val webfetch: String? = null,
    val doomLoop: String? = null,
    val externalDirectory: String? = null
)

// ============================================================================
// Command
// ============================================================================

@Serializable
data class Command(
    val name: String,
    val description: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val template: String,
    val subtask: Boolean = false
)

// ============================================================================
// Todo
// ============================================================================

@Serializable
data class Todo(
    val id: String,
    val content: String,
    val status: String,
    val priority: String
)

// ============================================================================
// Symbol (LSP)
// ============================================================================

@Serializable
data class Symbol(
    val name: String,
    val kind: Int,
    val uri: String,
    val range: SymbolRange
)

@Serializable
data class SymbolRange(
    val startLine: Int,
    val startCharacter: Int,
    val endLine: Int,
    val endCharacter: Int
)

// ============================================================================
// Status Types
// ============================================================================

@Serializable
data class LspStatus(
    val id: String,
    val name: String,
    val root: String,
    val status: String
)

@Serializable
data class FormatterStatus(
    val name: String,
    val extensions: List<String>,
    val enabled: Boolean
)

@Serializable
data class McpStatus(
    val status: String,
    val error: String? = null
)

@Serializable
data class Pty(
    val id: String,
    val title: String,
    val command: String,
    val args: List<String>,
    val cwd: String,
    val status: String,
    val pid: Int
)
