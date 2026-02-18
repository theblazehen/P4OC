package dev.blazelight.p4oc.data.remote.mapper

import dev.blazelight.p4oc.data.remote.dto.*
import dev.blazelight.p4oc.domain.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ============================================================================
// Project Mapper
// ============================================================================


class ProjectMapper constructor() {
    fun mapToDomain(dto: ProjectDto): Project = Project(
        id = dto.id,
        worktree = dto.worktree,
        vcsDir = dto.vcsDir,
        vcs = dto.vcs,
        createdAt = dto.time.created,
        initializedAt = dto.time.initialized
    )

    fun mapVcsInfoToDomain(dto: VcsInfoDto): VcsInfo = VcsInfo(
        branch = dto.branch
    )

    fun mapPathInfoToDomain(dto: PathInfoDto): PathInfo = PathInfo(
        state = dto.state,
        config = dto.config,
        worktree = dto.worktree,
        directory = dto.directory
    )
}

// ============================================================================
// Session Mapper
// ============================================================================


class SessionMapper constructor() {
    fun mapToDomain(dto: SessionDto): Session = Session(
        id = dto.id,
        projectID = dto.projectID,
        directory = dto.directory,
        parentID = dto.parentID,
        title = dto.title,
        version = dto.version,
        createdAt = dto.time.created,
        updatedAt = dto.time.updated ?: dto.time.created,
        compactingAt = dto.time.compacting,
        summary = dto.summary?.let { mapSummaryToDomain(it) },
        shareUrl = dto.share?.url,
        revert = dto.revert?.let { mapRevertToDomain(it) }
    )

    private fun mapSummaryToDomain(dto: SessionSummaryDto): SessionSummary = SessionSummary(
        additions = dto.additions,
        deletions = dto.deletions,
        files = dto.files,
        diffs = dto.diffs?.map { mapFileDiffToDomain(it) }
    )

    fun mapFileDiffToDomain(dto: FileDiffDto): FileDiff = FileDiff(
        file = dto.file,
        before = dto.before,
        after = dto.after,
        additions = dto.additions,
        deletions = dto.deletions
    )

    private fun mapRevertToDomain(dto: SessionRevertDto): SessionRevert = SessionRevert(
        messageID = dto.messageID,
        partID = dto.partID,
        snapshot = dto.snapshot,
        diff = dto.diff
    )

    fun mapStatusToDomain(dto: SessionStatusDto): SessionStatus = when (dto.type) {
        "idle" -> SessionStatus.Idle
        "busy" -> SessionStatus.Busy
        "retry" -> SessionStatus.Retry(dto.attempt ?: 0, dto.message ?: "", dto.next ?: 0L)
        else -> SessionStatus.Idle
    }
}

// ============================================================================
// Message Mapper
// ============================================================================


class MessageMapper constructor(
    private val json: Json
) {
    fun mapToDomain(dto: MessageInfoDto): Message = when (dto.role) {
        "user" -> Message.User(
            id = dto.id,
            sessionID = dto.sessionID,
            createdAt = dto.time.created,
            agent = dto.agent ?: "",
            model = dto.model?.let { ModelRef(it.providerID, it.modelID) }
                ?: ModelRef("", ""),
            summary = mapMessageSummaryToDomain(dto.summary),
            system = dto.system,
            tools = dto.tools?.let { jsonObj ->
                jsonObj.mapValues { (_, v) -> v.toString().toBooleanStrictOrNull() ?: false }
            }
        )
        else -> Message.Assistant(
            id = dto.id,
            sessionID = dto.sessionID,
            createdAt = dto.time.created,
            completedAt = dto.time.completed,
            parentID = dto.parentID ?: "",
            providerID = dto.providerID ?: dto.model?.providerID ?: "",
            modelID = dto.modelID ?: dto.model?.modelID ?: "",
            mode = dto.mode ?: "",
            agent = dto.agent ?: "",
            cost = dto.cost ?: 0.0,
            tokens = dto.tokens?.let { mapTokensToDomain(it) } ?: TokenUsage(0, 0),
            path = dto.path?.let { MessagePath(it.cwd ?: "", it.root ?: "") },
            error = dto.error?.let { mapMessageErrorToDomain(it) },
            finish = dto.finish,
            summary = dto.summary?.toString()?.toBooleanStrictOrNull()
        )
    }

    fun mapWrapperToDomain(dto: MessageWrapperDto, partMapper: PartMapper): MessageWithParts =
        MessageWithParts(
            message = mapToDomain(dto.info),
            parts = dto.parts.map { partMapper.mapToDomain(it) }
        )

    private fun mapTokensToDomain(dto: TokenUsageDto): TokenUsage = TokenUsage(
        input = dto.input,
        output = dto.output,
        reasoning = dto.reasoning,
        cacheRead = dto.cache?.read ?: 0,
        cacheWrite = dto.cache?.write ?: 0
    )

    private fun mapMessageErrorToDomain(dto: MessageErrorDto): MessageError {
        val data = dto.data
        return MessageError(
            name = dto.name,
            message = data?.get("message")?.toString()?.removeSurrounding("\""),
            statusCode = data?.get("statusCode")?.toString()?.toIntOrNull(),
            isRetryable = data?.get("isRetryable")?.toString()?.toBooleanStrictOrNull() ?: false,
            providerID = data?.get("providerID")?.toString()?.removeSurrounding("\""),
            responseHeaders = null,
            responseBody = data?.get("responseBody")?.toString()?.removeSurrounding("\"")
        )
    }

    fun mapApiErrorToDomain(dto: MessageErrorDto): ApiError? {
        if (dto.name != "APIError") return null
        val data = dto.data ?: return null
        return ApiError(
            message = data["message"]?.toString()?.removeSurrounding("\"") ?: "",
            statusCode = data["statusCode"]?.toString()?.toIntOrNull(),
            isRetryable = data["isRetryable"]?.toString()?.toBooleanStrictOrNull() ?: false,
            responseBody = data["responseBody"]?.toString()?.removeSurrounding("\"")
        )
    }

    private fun mapMessageSummaryToDomain(summary: JsonElement?): MessageSummary? {
        if (summary == null) return null
        return try {
            val obj = summary as? JsonObject ?: return null
            val summaryDto = json.decodeFromJsonElement<MessageSummaryDto>(obj)
            MessageSummary(
                title = summaryDto.title,
                body = summaryDto.body,
                diffs = summaryDto.diffs.map { dto ->
                    FileDiff(
                        file = dto.file,
                        before = dto.before,
                        after = dto.after,
                        additions = dto.additions,
                        deletions = dto.deletions
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }
}

// ============================================================================
// Part Mapper
// ============================================================================


class PartMapper constructor() {
    fun mapToDomain(dto: PartDto): Part = when (dto.type) {
        "text" -> Part.Text(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            text = dto.text ?: "",
            isStreaming = false,
            synthetic = dto.synthetic ?: false,
            ignored = dto.ignored ?: false,
            time = dto.time?.let { PartTime(it.start ?: 0L, it.end) },
            metadata = dto.metadata
        )
        "reasoning" -> Part.Reasoning(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            text = dto.text ?: "",
            time = dto.time?.let { PartTime(it.start ?: 0L, it.end) },
            metadata = dto.metadata
        )
        "tool" -> Part.Tool(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            callID = dto.callID ?: "",
            toolName = dto.toolName ?: "",
            state = dto.state?.let { mapToolStateToDomain(it) }
                ?: ToolState.Pending(buildJsonObject {}, ""),
            metadata = dto.metadata
        )
        "file" -> Part.File(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            mime = dto.mime ?: "",
            filename = dto.filename,
            url = dto.url ?: "",
            source = dto.source?.let { mapFileSourceToDomain(it) }
        )
        "patch" -> Part.Patch(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            hash = dto.hash ?: "",
            files = dto.files ?: emptyList()
        )
        "step-start" -> Part.StepStart(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            snapshot = dto.snapshot
        )
        "step-finish" -> Part.StepFinish(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            reason = dto.reason ?: "",
            snapshot = dto.snapshot,
            cost = dto.cost ?: 0.0,
            tokens = dto.tokens?.let { mapTokensToUsage(it) }
        )
        "snapshot" -> Part.Snapshot(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            snapshot = dto.snapshot ?: ""
        )
        "agent" -> Part.Agent(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            name = dto.name ?: "",
            source = dto.source?.let { mapAgentSourceToDomain(it) }
        )
        "retry" -> Part.Retry(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            attempt = dto.attempt ?: 0,
            error = dto.error?.let { mapApiErrorFromRetry(it) },
            createdAt = dto.time?.start
        )
        "compaction" -> Part.Compaction(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            auto = dto.auto ?: false
        )
        "subtask" -> Part.Subtask(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            prompt = dto.prompt ?: "",
            description = dto.description ?: "",
            agent = dto.agent ?: ""
        )
        else -> Part.Text(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            text = dto.text ?: "",
            isStreaming = false
        )
    }

    private fun mapToolStateToDomain(dto: ToolStateDto): ToolState {
        val input = dto.input ?: buildJsonObject {}
        val time = dto.time
        return when (dto.status) {
            "pending" -> ToolState.Pending(
                input = input,
                rawInput = dto.raw ?: ""
            )
            "running" -> ToolState.Running(
                input = input,
                title = dto.title,
                startedAt = time?.start ?: 0L,
                metadata = dto.metadata
            )
            "completed" -> ToolState.Completed(
                input = input,
                output = dto.output ?: "",
                title = dto.title ?: "",
                startedAt = time?.start ?: 0L,
                endedAt = time?.end ?: 0L,
                compactedAt = time?.compacted,
                metadata = dto.metadata,
                attachments = dto.attachments?.map { mapToDomain(it) }
            )
            "error" -> ToolState.Error(
                input = input,
                error = dto.error ?: "",
                startedAt = time?.start ?: 0L,
                endedAt = time?.end ?: 0L,
                metadata = dto.metadata
            )
            else -> ToolState.Pending(input, dto.raw ?: "")
        }
    }

    private fun mapTokensToUsage(dto: TokenUsageDto): TokenUsage = TokenUsage(
        input = dto.input,
        output = dto.output,
        reasoning = dto.reasoning,
        cacheRead = dto.cache?.read ?: 0,
        cacheWrite = dto.cache?.write ?: 0
    )

    private fun mapAgentSourceToDomain(source: JsonObject): AgentPartSource? {
        val value = source["value"]?.toString()?.removeSurrounding("\"") ?: return null
        val start = source["start"]?.toString()?.toIntOrNull() ?: return null
        val end = source["end"]?.toString()?.toIntOrNull() ?: return null
        return AgentPartSource(value, start, end)
    }

    private fun mapApiErrorFromRetry(dto: MessageErrorDto): ApiError? {
        if (dto.name != "APIError") return null
        val data = dto.data ?: return null
        return ApiError(
            message = data["message"]?.toString()?.removeSurrounding("\"") ?: "",
            statusCode = data["statusCode"]?.toString()?.toIntOrNull(),
            isRetryable = data["isRetryable"]?.toString()?.toBooleanStrictOrNull() ?: false,
            responseBody = data["responseBody"]?.toString()?.removeSurrounding("\"")
        )
    }

    private fun mapFileSourceToDomain(source: JsonObject): FilePartSource? {
        val typeValue = source["type"]?.toString()?.removeSurrounding("\"") ?: return null
        val textObj = source["text"] as? JsonObject ?: return null
        val text = FilePartSourceText(
            value = textObj["value"]?.toString()?.removeSurrounding("\"") ?: "",
            start = textObj["start"]?.toString()?.toIntOrNull() ?: 0,
            end = textObj["end"]?.toString()?.toIntOrNull() ?: 0
        )
        val path = source["path"]?.toString()?.removeSurrounding("\"") ?: ""

        return when (typeValue) {
            "file" -> FilePartSource.FileSource(text = text, path = path)
            "symbol" -> {
                val rangeObj = source["range"] as? JsonObject
                val startObj = rangeObj?.get("start") as? JsonObject
                val endObj = rangeObj?.get("end") as? JsonObject
                val range = if (startObj != null && endObj != null) {
                    SymbolRange(
                        startLine = startObj["line"]?.toString()?.toIntOrNull() ?: 0,
                        startCharacter = startObj["character"]?.toString()?.toIntOrNull() ?: 0,
                        endLine = endObj["line"]?.toString()?.toIntOrNull() ?: 0,
                        endCharacter = endObj["character"]?.toString()?.toIntOrNull() ?: 0
                    )
                } else {
                    SymbolRange(0, 0, 0, 0)
                }
                FilePartSource.SymbolSource(
                    text = text,
                    path = path,
                    range = range,
                    name = source["name"]?.toString()?.removeSurrounding("\"") ?: "",
                    kind = source["kind"]?.toString()?.toIntOrNull() ?: 0
                )
            }
            else -> null
        }
    }
}

// ============================================================================
// Provider Mapper
// ============================================================================


class ProviderMapper constructor() {
    fun mapToDomain(dto: ProviderDto): Provider = Provider(
        id = dto.id,
        name = dto.name,
        source = dto.source,
        env = dto.env,
        key = dto.key,
        options = dto.options,
        models = dto.models.mapValues { mapModelToDomain(it.value) }
    )

    fun mapModelToDomain(dto: ModelDto): Model = Model(
        id = dto.id,
        providerID = dto.providerId,
        name = dto.name,
        api = dto.api?.let { ModelApi(it.id, it.url, it.npm) },
        capabilities = dto.capabilities?.let { mapCapabilitiesToDomain(it) },
        cost = dto.cost?.let { mapCostToDomain(it) },
        limit = dto.limit?.let { mapLimitToDomain(it) },
        status = dto.status,
        options = dto.options,
        headers = dto.headers
    )

    private fun mapCapabilitiesToDomain(dto: ModelCapabilitiesDto): ModelCapabilities =
        ModelCapabilities(
            temperature = dto.temperature,
            reasoning = dto.reasoning,
            attachment = dto.attachment,
            toolcall = dto.toolcall,
            inputModalities = dto.input?.let { mapModalitiesToDomain(it) },
            outputModalities = dto.output?.let { mapModalitiesToDomain(it) }
        )

    private fun mapModalitiesToDomain(dto: ModalitiesDto): Modalities = Modalities(
        text = dto.text,
        audio = dto.audio,
        image = dto.image,
        video = dto.video,
        pdf = dto.pdf
    )

    private fun mapCostToDomain(dto: ModelCostDto): ModelCost = ModelCost(
        input = dto.input,
        output = dto.output,
        cacheRead = dto.cache?.read ?: 0.0,
        cacheWrite = dto.cache?.write ?: 0.0
    )

    private fun mapLimitToDomain(dto: ModelLimitDto): ModelLimit = ModelLimit(
        context = dto.context,
        output = dto.output
    )
}

// ============================================================================
// Agent Mapper
// ============================================================================


class AgentMapper constructor() {
    fun mapToDomain(dto: AgentDto): Agent = Agent(
        name = dto.name,
        description = dto.description,
        mode = dto.mode ?: "subagent",
        builtIn = dto.builtIn,
        topP = dto.topP,
        temperature = dto.temperature,
        color = dto.color,
        permission = AgentPermission(),
        model = dto.model?.let { ModelRef(it.providerID, it.modelID) },
        prompt = dto.prompt,
        tools = dto.tools ?: emptyMap(),
        maxSteps = dto.maxSteps
    )
}

// ============================================================================
// Command Mapper
// ============================================================================


class CommandMapper constructor() {
    fun mapToDomain(dto: CommandDto): Command = Command(
        name = dto.name,
        description = dto.description,
        agent = dto.agent,
        model = dto.model,
        template = (dto.template as? JsonPrimitive)?.contentOrNull,
        subtask = dto.subtask ?: false,
        mcp = dto.mcp ?: false
    )
}

// ============================================================================
// Todo Mapper
// ============================================================================


class TodoMapper constructor() {
    fun mapToDomain(dto: TodoDto): Todo = Todo(
        id = dto.id,
        content = dto.content,
        status = dto.status,
        priority = dto.priority
    )
}

// ============================================================================
// Symbol Mapper
// ============================================================================


class SymbolMapper constructor() {
    fun mapToDomain(dto: SymbolDto): Symbol = Symbol(
        name = dto.name,
        kind = dto.kind,
        uri = dto.location.uri,
        range = SymbolRange(
            startLine = dto.location.range.start.line,
            startCharacter = dto.location.range.start.character,
            endLine = dto.location.range.end.line,
            endCharacter = dto.location.range.end.character
        )
    )
}

// ============================================================================
// Status Mappers
// ============================================================================


class StatusMapper constructor() {
    fun mapLspStatusToDomain(dto: LspStatusDto): LspStatus = LspStatus(
        id = dto.id,
        name = dto.name,
        root = dto.root,
        status = dto.status
    )

    fun mapFormatterStatusToDomain(dto: FormatterStatusDto): FormatterStatus = FormatterStatus(
        name = dto.name,
        extensions = dto.extensions,
        enabled = dto.enabled
    )

    fun mapMcpStatusToDomain(dto: McpStatusDto): McpStatus = McpStatus(
        status = dto.status,
        error = dto.error
    )
}

// ============================================================================
// Event Mapper
// ============================================================================


class EventMapper constructor(
    private val json: Json,
    private val sessionMapper: SessionMapper,
    private val messageMapper: MessageMapper,
    private val partMapper: PartMapper,
    private val todoMapper: TodoMapper
) {
    fun mapToEvent(dto: EventDataDto): OpenCodeEvent? = try {
        when (dto.type) {
            "message.updated" -> {
                val wrapper = json.decodeFromJsonElement<MessageEventDto>(dto.properties)
                OpenCodeEvent.MessageUpdated(messageMapper.mapToDomain(wrapper.info))
            }
            "message.part.updated" -> {
                val partDto = json.decodeFromJsonElement<PartUpdateDto>(dto.properties)
                OpenCodeEvent.MessagePartUpdated(
                    part = partMapper.mapToDomain(partDto.part),
                    delta = partDto.delta
                )
            }
            "message.removed" -> {
                val props = json.decodeFromJsonElement<MessageRemovedPropertiesDto>(dto.properties)
                OpenCodeEvent.MessageRemoved(props.sessionID, props.messageID)
            }
            "message.part.removed" -> {
                val props = json.decodeFromJsonElement<PartRemovedPropertiesDto>(dto.properties)
                OpenCodeEvent.PartRemoved(props.sessionID, props.messageID, props.partID)
            }
            "session.created" -> {
                val wrapper = json.decodeFromJsonElement<SessionEventDto>(dto.properties)
                OpenCodeEvent.SessionCreated(sessionMapper.mapToDomain(wrapper.info))
            }
            "session.updated" -> {
                val wrapper = json.decodeFromJsonElement<SessionEventDto>(dto.properties)
                OpenCodeEvent.SessionUpdated(sessionMapper.mapToDomain(wrapper.info))
            }
            "session.deleted" -> {
                val wrapper = json.decodeFromJsonElement<SessionEventDto>(dto.properties)
                OpenCodeEvent.SessionDeleted(sessionMapper.mapToDomain(wrapper.info))
            }
            "session.status" -> {
                val statusDto = json.decodeFromJsonElement<SessionStatusEventDto>(dto.properties)
                OpenCodeEvent.SessionStatusChanged(
                    sessionID = statusDto.sessionID,
                    status = sessionMapper.mapStatusToDomain(statusDto.status)
                )
            }
            "session.diff" -> {
                val props = json.decodeFromJsonElement<SessionDiffPropertiesDto>(dto.properties)
                OpenCodeEvent.SessionDiff(
                    sessionID = props.sessionID,
                    diffs = props.diff.map { sessionMapper.mapFileDiffToDomain(it) }
                )
            }
            "session.error" -> {
                val props = json.decodeFromJsonElement<SessionErrorPropertiesDto>(dto.properties)
                OpenCodeEvent.SessionError(
                    sessionID = props.sessionID,
                    error = props.error?.let { MessageError(it.name, it.data?.toString()) }
                )
            }
            "session.compacted" -> {
                val props = json.decodeFromJsonElement<SessionCompactedDto>(dto.properties)
                OpenCodeEvent.SessionCompacted(props.sessionID)
            }
            "permission.asked" -> {
                val permissionDto = json.decodeFromJsonElement<PermissionDto>(dto.properties)
                val title = generatePermissionTitle(permissionDto.permission, permissionDto.patterns)
                OpenCodeEvent.PermissionRequested(
                    Permission(
                        id = permissionDto.id,
                        type = permissionDto.permission,
                        patterns = permissionDto.patterns,
                        sessionID = permissionDto.sessionID,
                        messageID = permissionDto.tool?.messageID ?: "",
                        callID = permissionDto.tool?.callID,
                        title = title,
                        metadata = permissionDto.metadata,
                        always = permissionDto.always
                    )
                )
            }
            "permission.replied" -> {
                val props = json.decodeFromJsonElement<PermissionRepliedPropertiesDto>(dto.properties)
                OpenCodeEvent.PermissionReplied(props.sessionID, props.requestID, props.reply)
            }
            "question.asked" -> {
                val questionDto = json.decodeFromJsonElement<QuestionRequestDto>(dto.properties)
                OpenCodeEvent.QuestionAsked(
                    QuestionRequest(
                        id = questionDto.id,
                        sessionID = questionDto.sessionID,
                        questions = questionDto.questions.map { q ->
                            Question(
                                header = q.header,
                                question = q.question,
                                options = q.options.map { o ->
                                    QuestionOption(label = o.label, description = o.description)
                                },
                                multiple = q.multiple,
                                custom = q.custom
                            )
                        },
                        tool = questionDto.tool?.let {
                            QuestionToolRef(messageID = it.messageID, callID = it.callID)
                        }
                    )
                )
            }
            "todo.updated" -> {
                val props = json.decodeFromJsonElement<TodoEventPropertiesDto>(dto.properties)
                OpenCodeEvent.TodoUpdated(
                    sessionID = props.sessionID,
                    todos = props.todos.map { todoMapper.mapToDomain(it) }
                )
            }
            "file.edited" -> {
                val props = json.decodeFromJsonElement<FileEditedPropertiesDto>(dto.properties)
                OpenCodeEvent.FileEdited(props.file)
            }
            "file.watcher.updated" -> {
                val props = json.decodeFromJsonElement<FileWatcherPropertiesDto>(dto.properties)
                OpenCodeEvent.FileWatcherUpdated(props.file, props.event)
            }
            "vcs.branch.updated" -> {
                val props = json.decodeFromJsonElement<VcsBranchPropertiesDto>(dto.properties)
                OpenCodeEvent.VcsBranchUpdated(props.branch)
            }
            "session.idle" -> {
                val props = json.decodeFromJsonElement<SessionIdlePropertiesDto>(dto.properties)
                OpenCodeEvent.SessionIdle(props.sessionID)
            }
            "command.executed" -> {
                val props = json.decodeFromJsonElement<CommandExecutedPropertiesDto>(dto.properties)
                OpenCodeEvent.CommandExecuted(
                    name = props.name,
                    sessionID = props.sessionID,
                    arguments = props.arguments,
                    messageID = props.messageID
                )
            }
            "server.connected" -> OpenCodeEvent.Connected
            "installation.updated" -> {
                val props = json.decodeFromJsonElement<InstallationUpdatedPropertiesDto>(dto.properties)
                OpenCodeEvent.InstallationUpdated(props.version)
            }
            "installation.update-available" -> {
                val props = json.decodeFromJsonElement<InstallationUpdatedPropertiesDto>(dto.properties)
                OpenCodeEvent.InstallationUpdateAvailable(props.version)
            }
            "lsp.client.diagnostics" -> {
                val props = json.decodeFromJsonElement<LspClientDiagnosticsPropertiesDto>(dto.properties)
                OpenCodeEvent.LspClientDiagnostics(props.serverID, props.path)
            }
            "lsp.updated" -> OpenCodeEvent.LspUpdated
            "pty.created" -> {
                val props = json.decodeFromJsonElement<PtyEventPropertiesDto>(dto.properties)
                OpenCodeEvent.PtyCreated(mapPtyToDomain(props.info))
            }
            "pty.updated" -> {
                val props = json.decodeFromJsonElement<PtyEventPropertiesDto>(dto.properties)
                OpenCodeEvent.PtyUpdated(mapPtyToDomain(props.info))
            }
            "pty.exited" -> {
                val props = json.decodeFromJsonElement<PtyExitedPropertiesDto>(dto.properties)
                OpenCodeEvent.PtyExited(props.id, props.exitCode)
            }
            "pty.deleted" -> {
                val props = json.decodeFromJsonElement<PtyDeletedPropertiesDto>(dto.properties)
                OpenCodeEvent.PtyDeleted(props.id)
            }
            "server.instance.disposed" -> {
                val props = json.decodeFromJsonElement<ServerInstanceDisposedPropertiesDto>(dto.properties)
                OpenCodeEvent.ServerInstanceDisposed(props.directory)
            }
            else -> null
        }
    } catch (e: Exception) {
        android.util.Log.e("EventMapper", "Failed to map event type=${dto.type}: ${e.message}", e)
        null
    }
}

// ============================================================================
// Internal Event DTOs
// ============================================================================

@kotlinx.serialization.Serializable
private data class PartUpdateDto(
    val part: PartDto,
    val delta: String? = null
)

@kotlinx.serialization.Serializable
private data class SessionStatusEventDto(
    @SerialName("sessionID") val sessionID: String,
    val status: SessionStatusDto
)

@kotlinx.serialization.Serializable
private data class SessionEventDto(
    val info: SessionDto
)

@kotlinx.serialization.Serializable
private data class MessageEventDto(
    val info: MessageInfoDto
)

@kotlinx.serialization.Serializable
private data class SessionCompactedDto(
    @SerialName("sessionID") val sessionID: String
)

private fun mapPatternToList(pattern: JsonElement?): List<String>? {
    if (pattern == null) return null
    return when {
        pattern is kotlinx.serialization.json.JsonArray -> pattern.map { it.toString().removeSurrounding("\"") }
        pattern is kotlinx.serialization.json.JsonPrimitive -> listOf(pattern.content)
        else -> null
    }
}

private fun mapPtyToDomain(dto: PtyDto): Pty = Pty(
    id = dto.id,
    title = dto.title,
    command = dto.command,
    args = dto.args,
    cwd = dto.cwd,
    status = dto.status,
    pid = dto.pid
)

@kotlinx.serialization.Serializable
private data class InstallationUpdatedPropertiesDto(
    val version: String
)

@kotlinx.serialization.Serializable
private data class LspClientDiagnosticsPropertiesDto(
    @SerialName("serverID") val serverID: String,
    val path: String
)

@kotlinx.serialization.Serializable
private data class PtyEventPropertiesDto(
    val info: PtyDto
)

@kotlinx.serialization.Serializable
private data class PtyExitedPropertiesDto(
    val id: String,
    val exitCode: Int
)

@kotlinx.serialization.Serializable
private data class PtyDeletedPropertiesDto(
    val id: String
)

@kotlinx.serialization.Serializable
private data class ServerInstanceDisposedPropertiesDto(
    val directory: String
)

private fun generatePermissionTitle(permission: String, patterns: List<String>): String {
    val action = when (permission) {
        "bash", "shell" -> "Execute command"
        "edit", "write" -> "Write to file"
        "patch" -> "Edit file"
        "webfetch" -> "Fetch URL"
        "task" -> "Run sub-agent"
        "skill" -> "Use skill"
        "external_directory" -> "Access external directory"
        "doom_loop" -> "Continue execution"
        else -> permission.replaceFirstChar { it.uppercase() }
    }
    val pattern = patterns.firstOrNull() ?: ""
    return if (pattern.isNotEmpty()) "$action: $pattern" else action
}
