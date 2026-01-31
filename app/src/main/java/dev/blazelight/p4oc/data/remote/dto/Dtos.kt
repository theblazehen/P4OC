package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HealthResponse(
    val healthy: Boolean,
    val version: String? = null
)

// ============================================================================
// Project Types (aligned with SDK Project type)
// ============================================================================

@Serializable
data class ProjectDto(
    val id: String,
    val worktree: String,
    @SerialName("vcsDir") val vcsDir: String? = null,
    val vcs: String? = null, // "git" or null
    val time: ProjectTimeDto
)

@Serializable
data class ProjectTimeDto(
    val created: Long,
    val initialized: Long? = null
)

// ============================================================================
// VCS Types (aligned with SDK VcsInfo type)
// ============================================================================

@Serializable
data class VcsInfoDto(
    val branch: String
)

// ============================================================================
// Path Types (aligned with SDK Path type)
// ============================================================================

@Serializable
data class PathInfoDto(
    val state: String,
    val config: String,
    val worktree: String,
    val directory: String
)

// ============================================================================
// Session Types
// ============================================================================

@Serializable
data class TimeDto(
    val created: Long,
    val updated: Long? = null,
    val compacting: Long? = null
)

@Serializable
data class SessionDto(
    val id: String,
    @SerialName("projectID") val projectID: String,
    val directory: String,
    @SerialName("parentID") val parentID: String? = null,
    val title: String,
    val version: String,
    val time: TimeDto,
    val summary: SessionSummaryDto? = null,
    val share: SessionShareDto? = null,
    val revert: SessionRevertDto? = null
)

@Serializable
data class SessionSummaryDto(
    val additions: Int,
    val deletions: Int,
    val files: Int,
    val diffs: List<FileDiffDto>? = null
)

@Serializable
data class FileDiffDto(
    val file: String,
    val before: String,
    val after: String,
    val additions: Int,
    val deletions: Int
)

@Serializable
data class SessionShareDto(
    val url: String
)

@Serializable
data class SessionRevertDto(
    @SerialName("messageID") val messageID: String,
    @SerialName("partID") val partID: String? = null,
    val snapshot: String? = null,
    val diff: String? = null
)

@Serializable
data class SessionStatusDto(
    val type: String,
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null
)

@Serializable
data class CreateSessionRequest(
    @SerialName("parentID") val parentID: String? = null,
    val title: String? = null
)

@Serializable
data class UpdateSessionRequest(
    val title: String? = null,
    val archived: Boolean? = null
)

@Serializable
data class ForkSessionRequest(
    @SerialName("messageID") val messageID: String? = null
)

@Serializable
data class RevertSessionRequest(
    @SerialName("messageID") val messageID: String,
    @SerialName("partID") val partID: String? = null
)

@Serializable
data class SummarizeSessionRequest(
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)

@Serializable
data class InitSessionRequest(
    @SerialName("messageID") val messageID: String,
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)

// ============================================================================
// Message Types
// ============================================================================

@Serializable
data class MessageWrapperDto(
    val info: MessageInfoDto,
    val parts: List<PartDto>
)

@Serializable
data class MessageInfoDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    val time: MessageTimeDto,
    val role: String,
    @SerialName("parentID") val parentID: String? = null,
    val model: ModelRefDto? = null,
    @SerialName("modelID") val modelID: String? = null,
    @SerialName("providerID") val providerID: String? = null,
    val agent: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    val error: MessageErrorDto? = null,
    val path: MessagePathDto? = null,
    val summary: JsonElement? = null,
    val finish: String? = null,
    val mode: String? = null,
    val system: String? = null,
    val tools: JsonObject? = null
)

@Serializable
data class MessageTimeDto(
    val created: Long,
    val completed: Long? = null
)

@Serializable
data class MessagePathDto(
    val cwd: String? = null,
    val root: String? = null
)

@Serializable
data class ModelRefDto(
    @SerialName("providerID") val providerID: String,
    @SerialName("modelID") val modelID: String
)

@Serializable
data class TokenUsageDto(
    val input: Int = 0,
    val output: Int = 0,
    val reasoning: Int = 0,
    val cache: TokenCacheDto? = null
)

@Serializable
data class TokenCacheDto(
    val read: Int = 0,
    val write: Int = 0
)

// ============================================================================
// Error Types (aligned with SDK error union types)
// ============================================================================

@Serializable
data class MessageErrorDto(
    val name: String, // "ProviderAuthError" | "UnknownError" | "MessageOutputLengthError" | "MessageAbortedError" | "APIError"
    val data: JsonObject? = null
)

@Serializable
data class ProviderAuthErrorDataDto(
    @SerialName("providerID") val providerID: String,
    val message: String
)

@Serializable
data class ApiErrorDataDto(
    val message: String,
    val statusCode: Int? = null,
    val isRetryable: Boolean = false,
    val responseHeaders: Map<String, String>? = null,
    val responseBody: String? = null
)

// ============================================================================
// Part Types (aligned with SDK Part union type)
// ============================================================================

@Serializable
data class PartDto(
    val id: String,
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    val type: String, // "text" | "reasoning" | "tool" | "file" | "patch" | "step-start" | "step-finish" | "snapshot" | "agent" | "retry" | "compaction" | "subtask"
    val time: PartTimeDto? = null,
    // TextPart
    val text: String? = null,
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
    // ToolPart
    @SerialName("callID") val callID: String? = null,
    @SerialName("tool") val toolName: String? = null,
    val state: ToolStateDto? = null,
    // FilePart
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val source: JsonObject? = null,
    // PatchPart
    val hash: String? = null,
    val files: List<String>? = null,
    // StepStartPart, StepFinishPart, SnapshotPart
    val snapshot: String? = null,
    // StepFinishPart
    val reason: String? = null,
    val cost: Double? = null,
    val tokens: TokenUsageDto? = null,
    // AgentPart
    val name: String? = null,
    // RetryPart
    val attempt: Int? = null,
    val error: MessageErrorDto? = null,
    // CompactionPart
    val auto: Boolean? = null,
    // SubtaskPart
    val prompt: String? = null,
    val description: String? = null,
    val agent: String? = null,
    // Common metadata
    val metadata: JsonObject? = null
)

@Serializable
data class PartTimeDto(
    val start: Long? = null,
    val end: Long? = null,
    val compacted: Long? = null
)

@Serializable
data class ToolStateDto(
    val status: String, // "pending" | "running" | "completed" | "error"
    val input: JsonObject? = null,
    val raw: String? = null,
    val title: String? = null,
    val output: String? = null,
    val error: String? = null,
    val time: PartTimeDto? = null,
    val metadata: JsonObject? = null,
    val attachments: List<PartDto>? = null
)

@Serializable
data class ModelInput(
    val providerID: String,
    val modelID: String
)

@Serializable
data class SendMessageRequest(
    @SerialName("messageID") val messageID: String? = null,
    val model: ModelInput? = null,
    val agent: String? = null,
    val noReply: Boolean? = null,
    val system: String? = null,
    val tools: JsonObject? = null,
    val parts: List<PartInputDto>
)

@Serializable
data class PartInputDto(
    val id: String? = null,
    val type: String, // "text" | "file" | "agent" | "subtask"
    // TextPartInput
    val text: String? = null,
    val synthetic: Boolean? = null,
    val ignored: Boolean? = null,
    val time: PartTimeDto? = null,
    val metadata: JsonObject? = null,
    // FilePartInput
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val source: JsonObject? = null,
    // AgentPartInput
    val name: String? = null,
    // SubtaskPartInput
    val prompt: String? = null,
    val description: String? = null,
    val agent: String? = null
)

// ============================================================================
// Permission Types
// ============================================================================

@Serializable
data class PermissionDto(
    val id: String,
    val type: String,
    val pattern: JsonElement? = null, // string | Array<string>
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    @SerialName("callID") val callID: String? = null,
    val title: String,
    val metadata: JsonObject? = null,
    val time: PermissionTimeDto? = null
)

@Serializable
data class PermissionTimeDto(
    val created: Long
)

@Serializable
data class PermissionResponseRequest(
    val response: String,
    val remember: Boolean? = null
)

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

// ============================================================================
// File Types
// ============================================================================

@Serializable
data class FileNodeDto(
    val name: String,
    val path: String,
    val absolute: String,
    val type: String, // "file" | "directory"
    val ignored: Boolean = false
)

@Serializable
data class FileContentDto(
    val type: String, // "text"
    val content: String,
    val diff: String? = null,
    val patch: PatchDto? = null,
    val encoding: String? = null, // "base64"
    val mimeType: String? = null
)

@Serializable
data class PatchDto(
    val oldFileName: String,
    val newFileName: String,
    val oldHeader: String? = null,
    val newHeader: String? = null,
    val hunks: List<HunkDto>,
    val index: String? = null
)

@Serializable
data class HunkDto(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<String>
)

@Serializable
data class FileStatusDto(
    val path: String,
    val status: String, // "added" | "deleted" | "modified"
    val added: Int = 0,
    val removed: Int = 0
)

@Serializable
data class SearchResultDto(
    val path: String,
    val lines: List<SearchLineDto>? = null,
    @SerialName("line_number") val lineNumber: Int? = null,
    @SerialName("absolute_offset") val absoluteOffset: Int? = null,
    val submatches: List<SubmatchDto>? = null
)

@Serializable
data class SearchLineDto(
    val text: String
)

@Serializable
data class SubmatchDto(
    val match: String,
    val start: Int,
    val end: Int
)

@Serializable
data class SymbolDto(
    val name: String,
    val kind: Int,
    val location: SymbolLocationDto
)

@Serializable
data class SymbolLocationDto(
    val uri: String,
    val range: RangeDto
)

@Serializable
data class RangeDto(
    val start: PositionDto,
    val end: PositionDto
)

@Serializable
data class PositionDto(
    val line: Int,
    val character: Int
)

// ============================================================================
// Provider Types (aligned with SDK Provider type)
// ============================================================================

@Serializable
data class ProviderDto(
    val id: String,
    val name: String,
    val source: String, // "env" | "config" | "custom" | "api"
    val env: List<String> = emptyList(),
    val key: String? = null,
    val options: JsonObject? = null,
    val models: Map<String, ModelDto> = emptyMap()
)

@Serializable
data class ModelDto(
    val id: String,
    @SerialName("providerID") val providerId: String,
    val api: ModelApiDto? = null,
    val name: String,
    val capabilities: ModelCapabilitiesDto? = null,
    val cost: ModelCostDto? = null,
    val limit: ModelLimitDto? = null,
    val status: String? = null, // "alpha" | "beta" | "deprecated" | "active"
    val options: JsonObject? = null,
    val headers: Map<String, String>? = null,
    val contextLength: Int? = null,
    val inputCostPer1k: Double? = null,
    val outputCostPer1k: Double? = null,
    val supportsTools: Boolean? = null,
    val supportsReasoning: Boolean? = null
)

@Serializable
data class ModelApiDto(
    val id: String? = null,
    val url: String? = null,
    val npm: String? = null
)

@Serializable
data class ModelCapabilitiesDto(
    val temperature: Boolean = false,
    val reasoning: Boolean = false,
    val attachment: Boolean = false,
    val toolcall: Boolean = false,
    val input: ModalitiesDto? = null,
    val output: ModalitiesDto? = null
)

@Serializable
data class ModalitiesDto(
    val text: Boolean = true,
    val audio: Boolean = false,
    val image: Boolean = false,
    val video: Boolean = false,
    val pdf: Boolean = false
)

@Serializable
data class ModelCostDto(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cache: CacheCostDto? = null
)

@Serializable
data class CacheCostDto(
    val read: Double = 0.0,
    val write: Double = 0.0
)

@Serializable
data class ModelLimitDto(
    val context: Int = 0,
    val output: Int = 0
)

@Serializable
data class ProvidersResponseDto(
    val all: List<ProviderDto>,
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList()
)

@Serializable
data class ProviderAuthMethodDto(
    val type: String, // "oauth" | "api"
    val label: String
)

@Serializable
data class ProviderAuthAuthorizationDto(
    val url: String,
    val method: String, // "auto" | "code"
    val instructions: String
)

// ============================================================================
// Agent Types (aligned with SDK Agent type)
// ============================================================================

@Serializable
data class AgentDto(
    val name: String,
    val description: String? = null,
    val mode: String? = null, // "subagent" | "primary" | "all"
    val builtIn: Boolean = false,
    @SerialName("native") val isNative: Boolean = false,
    val hidden: Boolean? = null,
    val topP: Double? = null,
    val temperature: Double? = null,
    val color: String? = null,
    val permission: JsonElement? = null, // Array of PermissionRuleDto from server
    val model: ModelRefDto? = null,
    val prompt: String? = null,
    val tools: Map<String, Boolean>? = null,
    val options: JsonObject? = null,
    val maxSteps: Int? = null,
    val systemPrompt: String? = null,
    val isEnabled: Boolean? = null,
    val isBuiltIn: Boolean? = null
)

@Serializable
data class PermissionRuleDto(
    val permission: String,
    val pattern: String,
    val action: String
)

// ============================================================================
// Command Types (aligned with SDK Command type)
// ============================================================================

@Serializable
data class CommandDto(
    val name: String,
    val description: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val template: String,
    val subtask: Boolean? = null
)

@Serializable
data class ExecuteCommandRequest(
    @SerialName("messageID") val messageID: String? = null,
    val agent: String? = null,
    val model: String? = null,
    val command: String,
    val arguments: String
)

@Serializable
data class ShellCommandRequest(
    val agent: String,
    val model: String? = null,
    val command: String
)

// ============================================================================
// Config Types (aligned with SDK Config type)
// ============================================================================

@Serializable
data class ConfigDto(
    val theme: String? = null,
    val logLevel: String? = null,
    val model: String? = null,
    @SerialName("small_model") val smallModel: String? = null,
    val username: String? = null,
    val share: String? = null, // "manual" | "auto" | "disabled"
    val autoupdate: JsonElement? = null, // boolean | "notify"
    @SerialName("disabled_providers") val disabledProviders: List<String>? = null,
    @SerialName("enabled_providers") val enabledProviders: List<String>? = null,
    val agent: Map<String, AgentConfigDto>? = null,
    val provider: Map<String, ProviderConfigDto>? = null,
    val mcp: Map<String, McpConfigDto>? = null,
    val tools: Map<String, Boolean>? = null,
    val permission: JsonElement? = null,
    val instructions: List<String>? = null
)

@Serializable
data class AgentConfigDto(
    val model: String? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    val prompt: String? = null,
    val tools: Map<String, Boolean>? = null,
    val disable: Boolean? = null,
    val description: String? = null,
    val mode: String? = null, // "subagent" | "primary" | "all"
    val color: String? = null,
    val maxSteps: Int? = null,
    val permission: JsonElement? = null
)

@Serializable
data class ProviderConfigDto(
    val api: String? = null,
    val name: String? = null,
    val env: List<String>? = null,
    val id: String? = null,
    val npm: String? = null,
    val models: Map<String, ModelConfigDto>? = null,
    val whitelist: List<String>? = null,
    val blacklist: List<String>? = null,
    val options: ProviderOptionsDto? = null
)

@Serializable
data class ProviderOptionsDto(
    val apiKey: String? = null,
    val baseURL: String? = null,
    val enterpriseUrl: String? = null,
    val setCacheKey: Boolean? = null,
    val timeout: Int? = null
)

@Serializable
data class ModelConfigDto(
    val id: String? = null,
    val name: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val attachment: Boolean? = null,
    val reasoning: Boolean? = null,
    val temperature: Boolean? = null,
    @SerialName("tool_call") val toolCall: Boolean? = null,
    val cost: ModelCostConfigDto? = null,
    val limit: ModelLimitDto? = null,
    val experimental: Boolean? = null,
    val status: String? = null,
    val options: JsonObject? = null,
    val headers: Map<String, String>? = null
)

@Serializable
data class ModelCostConfigDto(
    val input: Double = 0.0,
    val output: Double = 0.0,
    @SerialName("cache_read") val cacheRead: Double? = null,
    @SerialName("cache_write") val cacheWrite: Double? = null
)

@Serializable
data class McpConfigDto(
    val type: String, // "local" | "remote"
    // Local MCP
    val command: List<String>? = null,
    val environment: Map<String, String>? = null,
    // Remote MCP
    val url: String? = null,
    val headers: Map<String, String>? = null,
    val oauth: JsonElement? = null, // McpOAuthConfig | false
    // Common
    val enabled: Boolean? = null,
    val timeout: Int? = null
)

// ============================================================================
// Todo Types (aligned with SDK Todo type)
// ============================================================================

@Serializable
data class TodoDto(
    val id: String,
    val content: String,
    val status: String, // "pending" | "in_progress" | "completed" | "cancelled"
    val priority: String // "high" | "medium" | "low"
)

// ============================================================================
// Event Types (aligned with SDK Event union type)
// ============================================================================

@Serializable
data class EventDataDto(
    val type: String,
    val properties: JsonObject
)

@Serializable
data class GlobalEventDto(
    val directory: String,
    val payload: EventDataDto
)

// Event-specific property DTOs

@Serializable
data class TodoEventPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    val todos: List<TodoDto>
)

@Serializable
data class SessionDiffPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    val diff: List<FileDiffDto>
)

@Serializable
data class SessionErrorPropertiesDto(
    @SerialName("sessionID") val sessionID: String? = null,
    val error: MessageErrorDto? = null
)

@Serializable
data class FileEditedPropertiesDto(
    val file: String
)

@Serializable
data class MessageRemovedPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String
)

@Serializable
data class PartRemovedPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    @SerialName("messageID") val messageID: String,
    @SerialName("partID") val partID: String
)

@Serializable
data class PermissionRepliedPropertiesDto(
    @SerialName("sessionID") val sessionID: String,
    @SerialName("permissionID") val permissionID: String,
    val response: String
)

@Serializable
data class FileWatcherPropertiesDto(
    val file: String,
    val event: String // "add" | "change" | "unlink"
)

@Serializable
data class VcsBranchPropertiesDto(
    val branch: String? = null
)

// ============================================================================
// Session Idle Event Types
// ============================================================================

@Serializable
data class SessionIdlePropertiesDto(
    @SerialName("sessionID") val sessionID: String
)

@Serializable
data class CommandExecutedPropertiesDto(
    val name: String,
    @SerialName("sessionID") val sessionID: String,
    val arguments: String,
    @SerialName("messageID") val messageID: String
)

// ============================================================================
// LSP, Formatter, MCP Status Types
// ============================================================================

@Serializable
data class LspStatusDto(
    val id: String,
    val name: String,
    val root: String,
    val status: String // "connected" | "error"
)

@Serializable
data class FormatterStatusDto(
    val name: String,
    val extensions: List<String>,
    val enabled: Boolean
)

@Serializable
data class McpStatusDto(
    val status: String, // "connected" | "disabled" | "failed" | "needs_auth" | "needs_client_registration"
    val error: String? = null
)

// ============================================================================
// Auth Types
// ============================================================================

@Serializable
data class OAuthDto(
    val type: String = "oauth",
    val refresh: String,
    val access: String,
    val expires: Long,
    val enterpriseUrl: String? = null
)

@Serializable
data class ApiAuthDto(
    val type: String = "api",
    val key: String
)

@Serializable
data class WellKnownAuthDto(
    val type: String = "wellknown",
    val key: String,
    val token: String
)

@Serializable
data class AuthDto(
    val type: String,
    val refresh: String? = null,
    val access: String? = null,
    val expires: Long? = null,
    val enterpriseUrl: String? = null,
    val key: String? = null,
    val token: String? = null
)

@Serializable
data class OAuthCallbackRequest(
    val code: String? = null,
    val state: String? = null
)

@Serializable
data class AddMcpServerRequest(
    val name: String,
    val config: McpConfigDto
)

@Serializable
data class LogRequest(
    val service: String,
    val level: String,
    val message: String,
    val extra: kotlinx.serialization.json.JsonObject? = null
)

@Serializable
data class PtyDto(
    val id: String,
    val title: String,
    val command: String,
    val args: List<String>,
    val cwd: String,
    val status: String,
    val pid: Int? = null  // Server may return null for pid
)

@Serializable
data class CreatePtyRequest(
    val command: String = "/bin/bash",
    val args: List<String> = emptyList(),
    val cwd: String = ".",
    val title: String = "Terminal",
    val env: Map<String, String> = emptyMap()
)

@Serializable
data class UpdatePtyRequest(
    val title: String? = null,
    val size: PtySizeDto? = null
)

@Serializable
data class PtySizeDto(
    val rows: Int,
    val cols: Int
)

@Serializable
data class PtyInputRequest(
    val data: String
)

@Serializable
data class PtyOutputDto(
    val lines: List<String> = emptyList(),
    val totalLines: Int = 0,
    val hasMore: Boolean = false
)

@Serializable
data class MessageSummaryDto(
    val title: String? = null,
    val body: String? = null,
    val diffs: List<FileDiffDto> = emptyList()
)

// ============================================================================
// Experimental Tools Types
// ============================================================================

@Serializable
data class ToolListDto(
    val tools: List<ToolDto> = emptyList()
)

@Serializable
data class ToolDto(
    val id: String,
    val description: String? = null,
    val parameters: JsonObject? = null
)

// ============================================================================
// Config Providers Types
// ============================================================================

@Serializable
data class ConfigProvidersDto(
    val providers: List<ProviderDto> = emptyList(),
    val default: Map<String, String> = emptyMap()
)

// ============================================================================
// Model Active Request
// ============================================================================

@Serializable
data class SetActiveModelRequest(
    val model: ModelInput
)
