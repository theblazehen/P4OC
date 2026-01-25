package dev.blazelight.p4oc.data.repository

import dev.blazelight.p4oc.core.database.dao.MessageDao
import dev.blazelight.p4oc.core.database.entity.MessageEntity
import dev.blazelight.p4oc.core.database.entity.PartEntity
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.data.remote.mapper.PartMapper
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.TokenUsage
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val messageDao: MessageDao,
    private val messageMapper: MessageMapper,
    private val partMapper: PartMapper,
    private val json: Json
) : MessageRepository {

    override fun getMessagesForSession(sessionId: String): Flow<List<MessageWithParts>> {
        return combine(
            messageDao.getMessagesForSession(sessionId),
            messageDao.getPartsForSession(sessionId)
        ) { messages, parts ->
            val partsByMessageId = parts.groupBy { it.messageID }
            messages.map { messageEntity ->
                MessageWithParts(
                    message = messageEntity.toDomain(),
                    parts = partsByMessageId[messageEntity.id]?.map { it.toDomain() } ?: emptyList()
                )
            }
        }
    }

    override suspend fun fetchMessages(sessionId: String, limit: Int?): ApiResult<List<MessageWithParts>> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dtos = api.getMessages(sessionId, limit)
            val messagesWithParts = dtos.map { dto ->
                messageMapper.mapWrapperToDomain(dto, partMapper)
            }
            messagesWithParts.forEach { mwp ->
                messageDao.insertMessageWithParts(
                    mwp.message.toEntity(),
                    mwp.parts.map { it.toEntity() }
                )
            }
            messagesWithParts
        }
    }

    override suspend fun sendMessage(sessionId: String, text: String): ApiResult<Unit> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            // Use async endpoint - returns immediately, all content streams via SSE
            // This avoids HTTP timeout issues for long-running tool executions
            api.sendMessageAsync(
                sessionId,
                SendMessageRequest(
                    parts = listOf(PartInputDto(type = "text", text = text))
                )
            )
        }
    }

    override suspend fun respondToPermission(
        sessionId: String,
        permissionId: String,
        response: String
    ): ApiResult<Boolean> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            api.respondToPermission(
                sessionId,
                permissionId,
                PermissionResponseRequest(response = response)
            )
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun updateMessagePart(part: Part, delta: String?) {
        repositoryScope.launch {
            messageDao.updatePart(part.toEntity())
        }
    }

    private fun MessageEntity.toDomain(): Message {
        return if (role == "user") {
            Message.User(
                id = id,
                sessionID = sessionID,
                createdAt = createdAt,
                agent = agent ?: "",
                model = dev.blazelight.p4oc.domain.model.ModelRef(providerID ?: "", modelID ?: "")
            )
        } else {
            Message.Assistant(
                id = id,
                sessionID = sessionID,
                createdAt = createdAt,
                completedAt = completedAt,
                parentID = parentID ?: "",
                providerID = providerID ?: "",
                modelID = modelID ?: "",
                mode = "",
                agent = agent ?: "",
                cost = cost ?: 0.0,
                tokens = TokenUsage(
                    input = tokensInput ?: 0,
                    output = tokensOutput ?: 0,
                    reasoning = tokensReasoning ?: 0,
                    cacheRead = tokensCacheRead ?: 0,
                    cacheWrite = tokensCacheWrite ?: 0
                ),
                error = if (errorCode != null && errorMessage != null) {
                    dev.blazelight.p4oc.domain.model.MessageError(errorCode, errorMessage)
                } else null
            )
        }
    }

    private fun Message.toEntity(): MessageEntity = when (this) {
        is Message.User -> MessageEntity(
            id = id,
            sessionID = sessionID,
            createdAt = createdAt,
            role = "user",
            completedAt = null,
            parentID = null,
            providerID = model.providerID,
            modelID = model.modelID,
            agent = agent,
            cost = null,
            tokensInput = null,
            tokensOutput = null,
            tokensReasoning = null,
            tokensCacheRead = null,
            tokensCacheWrite = null,
            errorCode = null,
            errorMessage = null
        )
        is Message.Assistant -> MessageEntity(
            id = id,
            sessionID = sessionID,
            createdAt = createdAt,
            role = "assistant",
            completedAt = completedAt,
            parentID = parentID,
            providerID = providerID,
            modelID = modelID,
            agent = agent,
            cost = cost,
            tokensInput = tokens.input,
            tokensOutput = tokens.output,
            tokensReasoning = tokens.reasoning,
            tokensCacheRead = tokens.cacheRead,
            tokensCacheWrite = tokens.cacheWrite,
            errorCode = error?.name,
            errorMessage = error?.message
        )
    }

    private fun PartEntity.toDomain(): Part = when (type) {
        "text" -> Part.Text(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            text = text ?: "",
            isStreaming = false
        )
        "reasoning" -> Part.Reasoning(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            text = text ?: ""
        )
        "tool" -> Part.Tool(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            callID = callID ?: "",
            toolName = toolName ?: "",
            state = parseToolState()
        )
        "file" -> Part.File(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            mime = mime ?: "",
            filename = filename,
            url = url ?: ""
        )
        "patch" -> Part.Patch(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            hash = hash ?: "",
            files = files ?: emptyList()
        )
        else -> Part.Text(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            text = text ?: "",
            isStreaming = false
        )
    }

    private fun PartEntity.parseToolState(): ToolState {
        val input = toolStateInput?.let {
            try { json.decodeFromString<JsonObject>(it) }
            catch (e: Exception) { kotlinx.serialization.json.buildJsonObject {} }
        } ?: kotlinx.serialization.json.buildJsonObject {}

        return when (toolStateStatus) {
            "running" -> ToolState.Running(
                input = input,
                title = toolStateTitle,
                startedAt = toolStateStartedAt!!
            )
            "completed" -> ToolState.Completed(
                input = input,
                output = toolStateOutput ?: "",
                title = toolStateTitle ?: "",
                startedAt = toolStateStartedAt!!,
                endedAt = toolStateEndedAt!!,
                metadata = toolStateMetadata?.let {
                    try { json.decodeFromString<JsonObject>(it) }
                    catch (e: Exception) { null }
                }
            )
            "error" -> ToolState.Error(
                input = input,
                error = toolStateError ?: "",
                startedAt = toolStateStartedAt!!,
                endedAt = toolStateEndedAt!!
            )
            else -> ToolState.Pending(input = input, rawInput = toolStateRawInput ?: "")
        }
    }

    private fun Part.toEntity(): PartEntity = when (this) {
        is Part.Text -> PartEntity(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            type = "text",
            text = text,
            callID = null,
            toolName = null,
            toolStateStatus = null,
            toolStateInput = null,
            toolStateRawInput = null,
            toolStateTitle = null,
            toolStateOutput = null,
            toolStateError = null,
            toolStateStartedAt = null,
            toolStateEndedAt = null,
            toolStateMetadata = null,
            mime = null,
            filename = null,
            url = null,
            hash = null,
            files = null
        )
        is Part.Reasoning -> PartEntity(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            type = "reasoning",
            text = text,
            callID = null,
            toolName = null,
            toolStateStatus = null,
            toolStateInput = null,
            toolStateRawInput = null,
            toolStateTitle = null,
            toolStateOutput = null,
            toolStateError = null,
            toolStateStartedAt = null,
            toolStateEndedAt = null,
            toolStateMetadata = null,
            mime = null,
            filename = null,
            url = null,
            hash = null,
            files = null
        )
        is Part.Tool -> PartEntity(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            type = "tool",
            text = null,
            callID = callID,
            toolName = toolName,
            toolStateStatus = when (state) {
                is ToolState.Pending -> "pending"
                is ToolState.Running -> "running"
                is ToolState.Completed -> "completed"
                is ToolState.Error -> "error"
            },
            toolStateInput = json.encodeToString(JsonObject.serializer(), state.input),
            toolStateRawInput = (state as? ToolState.Pending)?.rawInput,
            toolStateTitle = when (state) {
                is ToolState.Running -> state.title
                is ToolState.Completed -> state.title
                else -> null
            },
            toolStateOutput = (state as? ToolState.Completed)?.output,
            toolStateError = (state as? ToolState.Error)?.error,
            toolStateStartedAt = when (state) {
                is ToolState.Running -> state.startedAt
                is ToolState.Completed -> state.startedAt
                is ToolState.Error -> state.startedAt
                else -> null
            },
            toolStateEndedAt = when (state) {
                is ToolState.Completed -> state.endedAt
                is ToolState.Error -> state.endedAt
                else -> null
            },
            toolStateMetadata = (state as? ToolState.Completed)?.metadata?.let {
                json.encodeToString(JsonObject.serializer(), it)
            },
            mime = null,
            filename = null,
            url = null,
            hash = null,
            files = null
        )
        is Part.File -> PartEntity(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            type = "file",
            text = null,
            callID = null,
            toolName = null,
            toolStateStatus = null,
            toolStateInput = null,
            toolStateRawInput = null,
            toolStateTitle = null,
            toolStateOutput = null,
            toolStateError = null,
            toolStateStartedAt = null,
            toolStateEndedAt = null,
            toolStateMetadata = null,
            mime = mime,
            filename = filename,
            url = url,
            hash = null,
            files = null
        )
        is Part.Patch -> PartEntity(
            id = id,
            sessionID = sessionID,
            messageID = messageID,
            type = "patch",
            text = null,
            callID = null,
            toolName = null,
            toolStateStatus = null,
            toolStateInput = null,
            toolStateRawInput = null,
            toolStateTitle = null,
            toolStateOutput = null,
            toolStateError = null,
            toolStateStartedAt = null,
            toolStateEndedAt = null,
            toolStateMetadata = null,
            mime = null,
            filename = null,
            url = null,
            hash = hash,
            files = files
        )
        is Part.StepStart -> createMinimalEntity(id, sessionID, messageID, "step-start")
        is Part.StepFinish -> createMinimalEntity(id, sessionID, messageID, "step-finish")
        is Part.Snapshot -> createMinimalEntity(id, sessionID, messageID, "snapshot")
        is Part.Agent -> createMinimalEntity(id, sessionID, messageID, "agent")
        is Part.Retry -> createMinimalEntity(id, sessionID, messageID, "retry")
        is Part.Compaction -> createMinimalEntity(id, sessionID, messageID, "compaction")
        is Part.Subtask -> createMinimalEntity(id, sessionID, messageID, "subtask")
    }

    private fun createMinimalEntity(id: String, sessionID: String, messageID: String, type: String) = PartEntity(
        id = id,
        sessionID = sessionID,
        messageID = messageID,
        type = type,
        text = null,
        callID = null,
        toolName = null,
        toolStateStatus = null,
        toolStateInput = null,
        toolStateRawInput = null,
        toolStateTitle = null,
        toolStateOutput = null,
        toolStateError = null,
        toolStateStartedAt = null,
        toolStateEndedAt = null,
        toolStateMetadata = null,
        mime = null,
        filename = null,
        url = null,
        hash = null,
        files = null
    )
}
