package com.pocketcode.data.remote.mapper

import com.pocketcode.data.remote.dto.*
import com.pocketcode.domain.model.*
import kotlinx.serialization.json.Json
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
        createdAt = dto.createdAt,
        updatedAt = dto.updatedAt,
        archivedAt = dto.archivedAt,
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
    fun mapToDomain(dto: MessageDto): Message = when (dto.role) {
        "user" -> Message.User(
            id = dto.id,
            sessionID = dto.sessionID,
            createdAt = dto.createdAt,
            agent = dto.agent ?: "",
            model = dto.model?.let { ModelRef(it.providerID, it.modelID) }
                ?: ModelRef("", "")
        )
        else -> Message.Assistant(
            id = dto.id,
            sessionID = dto.sessionID,
            createdAt = dto.createdAt,
            completedAt = dto.completedAt,
            parentID = dto.parentID ?: "",
            providerID = dto.providerID ?: "",
            modelID = dto.modelID ?: "",
            agent = dto.agent ?: "",
            cost = dto.cost ?: 0.0,
            tokens = dto.tokens?.let { mapTokensToDomain(it) } ?: TokenUsage(0, 0),
            error = dto.error?.let { MessageError(it.code, it.message) }
        )
    }

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
                ?: ToolState.Pending(kotlinx.serialization.json.buildJsonObject {}, "")
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

    private fun mapToolStateToDomain(dto: ToolStateDto): ToolState = when (dto.status) {
        "pending" -> ToolState.Pending(
            input = dto.input,
            rawInput = dto.rawInput ?: ""
        )
        "running" -> ToolState.Running(
            input = dto.input,
            title = dto.title,
            startedAt = dto.startedAt!!
        )
        "completed" -> ToolState.Completed(
            input = dto.input,
            output = dto.output ?: "",
            title = dto.title ?: "",
            startedAt = dto.startedAt!!,
            endedAt = dto.endedAt!!,
            metadata = dto.metadata
        )
        "error" -> ToolState.Error(
            input = dto.input,
            error = dto.error ?: "",
            startedAt = dto.startedAt!!,
            endedAt = dto.endedAt!!
        )
        else -> ToolState.Pending(dto.input, dto.rawInput ?: "")
    }
}

@Singleton
class EventMapper @Inject constructor(
    private val json: Json,
    private val sessionMapper: SessionMapper,
    private val messageMapper: MessageMapper,
    private val partMapper: PartMapper
) {
    fun mapToEvent(dto: EventDataDto): OpenCodeEvent? = when (dto.type) {
        "message.updated" -> {
            val messageDto = json.decodeFromJsonElement<MessageDto>(dto.properties)
            OpenCodeEvent.MessageUpdated(messageMapper.mapToDomain(messageDto))
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
                    metadata = permissionDto.metadata,
                    createdAt = permissionDto.createdAt
                )
            )
        }
        else -> null
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
