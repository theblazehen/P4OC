package com.pocketcode.data.remote.mapper

import com.pocketcode.data.remote.dto.*
import com.pocketcode.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionMapper @Inject constructor() {
    fun mapToDomain(dto: SessionDto): Session = Session(
        id = dto.id,
        slug = dto.slug,
        projectID = dto.projectID,
        directory = dto.directory,
        parentID = dto.parentID,
        title = dto.title,
        version = dto.version,
        createdAt = dto.time.created,
        updatedAt = dto.time.updated ?: dto.time.created,
        summary = dto.summary?.let { mapSummaryToDomain(it) },
        shareUrl = dto.shareUrl
    )

    private fun mapSummaryToDomain(dto: SessionSummaryDto): SessionSummary = SessionSummary(
        additions = dto.additions,
        deletions = dto.deletions,
        files = dto.files
    )

    fun mapStatusToDomain(dto: SessionStatusDto): SessionStatus = when (dto.status) {
        "idle" -> SessionStatus.Idle
        "busy" -> SessionStatus.Busy
        "retry" -> SessionStatus.Retry(dto.attempt ?: 0, dto.message ?: "")
        else -> SessionStatus.Idle
    }
}

@Singleton
class MessageMapper @Inject constructor() {
    fun mapToDomain(dto: MessageInfoDto): Message = when (dto.role) {
        "user" -> Message.User(
            id = dto.id,
            sessionID = dto.sessionID,
            createdAt = dto.time.created,
            agent = dto.agent ?: "",
            model = dto.model?.let { ModelRef(it.providerID, it.modelID) }
                ?: ModelRef("", "")
        )
        else -> Message.Assistant(
            id = dto.id,
            sessionID = dto.sessionID,
            createdAt = dto.time.created,
            completedAt = dto.time.completed,
            parentID = dto.parentID ?: "",
            providerID = dto.model?.providerID ?: "",
            modelID = dto.model?.modelID ?: "",
            agent = dto.agent ?: "",
            cost = dto.cost ?: 0.0,
            tokens = dto.tokens?.let { mapTokensToDomain(it) } ?: TokenUsage(0, 0),
            error = dto.error?.let { MessageError(it.code, it.message) }
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
        cacheRead = dto.cacheRead,
        cacheWrite = dto.cacheWrite
    )
}

@Singleton
class PartMapper @Inject constructor() {
    fun mapToDomain(dto: PartDto): Part = when (dto.type) {
        "text" -> Part.Text(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            text = dto.text ?: "",
            isStreaming = false
        )
        "reasoning" -> Part.Reasoning(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            text = dto.text ?: ""
        )
        "tool" -> Part.Tool(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            callID = dto.callID ?: "",
            toolName = dto.toolName ?: "",
            state = dto.state?.let { mapToolStateToDomain(it) }
                ?: ToolState.Pending(buildJsonObject {}, "")
        )
        "file" -> Part.File(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            mime = dto.mime ?: "",
            filename = dto.filename,
            url = dto.url ?: ""
        )
        "patch" -> Part.Patch(
            id = dto.id,
            sessionID = dto.sessionID,
            messageID = dto.messageID,
            hash = dto.hash ?: "",
            files = dto.files ?: emptyList()
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
                rawInput = dto.rawInput ?: ""
            )
            "running" -> ToolState.Running(
                input = input,
                title = dto.title,
                startedAt = time?.start ?: 0L
            )
            "completed" -> ToolState.Completed(
                input = input,
                output = dto.output ?: "",
                title = dto.title ?: "",
                startedAt = time?.start ?: 0L,
                endedAt = time?.end ?: 0L,
                metadata = dto.metadata
            )
            "error" -> ToolState.Error(
                input = input,
                error = dto.error ?: "",
                startedAt = time?.start ?: 0L,
                endedAt = time?.end ?: 0L
            )
            else -> ToolState.Pending(input, dto.rawInput ?: "")
        }
    }
}

@Singleton
class EventMapper @Inject constructor(
    private val json: Json,
    private val sessionMapper: SessionMapper,
    private val messageMapper: MessageMapper,
    private val partMapper: PartMapper
) {
    fun mapToEvent(dto: EventDataDto): OpenCodeEvent? = try {
        when (dto.type) {
            "message.updated" -> {
                val wrapper = json.decodeFromJsonElement<MessageWrapperDto>(dto.properties)
                OpenCodeEvent.MessageUpdated(messageMapper.mapToDomain(wrapper.info))
            }
            "message.part.updated" -> {
                val partDto = json.decodeFromJsonElement<PartUpdateDto>(dto.properties)
                OpenCodeEvent.MessagePartUpdated(
                    part = partMapper.mapToDomain(partDto.part),
                    delta = partDto.delta
                )
            }
            "session.created" -> {
                val sessionDto = json.decodeFromJsonElement<SessionDto>(dto.properties)
                OpenCodeEvent.SessionCreated(sessionMapper.mapToDomain(sessionDto))
            }
            "session.updated" -> {
                val sessionDto = json.decodeFromJsonElement<SessionDto>(dto.properties)
                OpenCodeEvent.SessionUpdated(sessionMapper.mapToDomain(sessionDto))
            }
            "session.status" -> {
                val statusDto = json.decodeFromJsonElement<SessionStatusEventDto>(dto.properties)
                OpenCodeEvent.SessionStatusChanged(
                    sessionID = statusDto.sessionID,
                    status = sessionMapper.mapStatusToDomain(statusDto.status)
                )
            }
            "permission.updated" -> {
                val permissionDto = json.decodeFromJsonElement<PermissionDto>(dto.properties)
                OpenCodeEvent.PermissionRequested(
                    Permission(
                        id = permissionDto.id,
                        type = permissionDto.type,
                        sessionID = permissionDto.sessionID,
                        messageID = permissionDto.messageID,
                        title = permissionDto.title,
                        metadata = permissionDto.metadata ?: buildJsonObject {},
                        createdAt = permissionDto.time?.created ?: 0L
                    )
                )
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

@kotlinx.serialization.Serializable
private data class PartUpdateDto(
    val part: PartDto,
    val delta: String? = null
)

@kotlinx.serialization.Serializable
private data class SessionStatusEventDto(
    val sessionID: String,
    val status: SessionStatusDto
)
