package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.EventDataDto
import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.domain.model.MessageError
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Permission
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.ConcurrentHashMap

/** Maps the v2 `{ id, type, data, location }` stream envelope into owned REST refreshes. */
internal class V2EventMapper(private val json: Json, private val legacyMapper: EventMapper) {
    private val loggedUnmappedTypes = ConcurrentHashMap.newKeySet<String>()

    data class MappedEvent(val event: OpenCodeEvent, val directory: String?)

    fun map(serialized: String): MappedEvent? = parseEnvelope(serialized)?.let(::mapEnvelope)

    private fun parseEnvelope(serialized: String): JsonObject? = try {
        json.parseToJsonElement(serialized) as? JsonObject
    } catch (error: SerializationException) {
        AppLog.w(TAG, "Ignoring malformed v2 SSE envelope (${error.javaClass.simpleName})")
        null
    }

    private fun mapEnvelope(envelope: JsonObject): MappedEvent? =
        envelope.string("type")
            ?.takeUnless { it in IGNORED_EVENT_TYPES }
            ?.let { type ->
                envelope.objectValue("data")?.let { data ->
                    val directory = envelope.objectValue("location")
                        ?.string("directory")
                        ?.takeIf(String::isNotBlank)
                    mapAndLog(type, envelope, data, directory)
                }
            }

    private fun mapAndLog(
        type: String,
        envelope: JsonObject,
        data: JsonObject,
        directory: String?,
    ): MappedEvent? {
        val sessionID = data.string("sessionID")
        val event = mapEvent(type, envelope, data, directory, sessionID)
        return if (event == null) {
            logUnmapped(type)
            null
        } else {
            MappedEvent(event, directory)
        }
    }

    private fun mapEvent(
        type: String,
        envelope: JsonObject,
        data: JsonObject,
        directory: String?,
        sessionID: String?,
    ): OpenCodeEvent? = when (type) {
        "server.connected" -> OpenCodeEvent.Connected
        in CONTENT_EVENT_TYPES -> mapContentEvent(type, data, sessionID)
        in LEGACY_EVENT_TYPES -> legacyMapper.mapToEvent(
            EventDataDto(id = envelope.string("id"), type = type, properties = data),
        )
        else -> mapOtherEvent(type, data, directory, sessionID)
    }

    private fun mapOtherEvent(
        type: String,
        data: JsonObject,
        directory: String?,
        sessionID: String?,
    ): OpenCodeEvent? = when (type) {
        "project.updated" -> data.string("id")?.let { OpenCodeEvent.ProjectRefreshRequested(it) }
        in MCP_CHANGE_EVENT_TYPES -> data.string("server")?.let { OpenCodeEvent.McpToolsChanged(it) }
        "location.shutdown" -> OpenCodeEvent.LocationShutdown(directory)
        "permission.asked" -> mapPermissionAsked(data, sessionID)
        "permission.replied" -> mapPermissionReply(data, sessionID)
        in FORM_EVENT_TYPES -> mapFormEvent(data, sessionID)
        "filesystem.changed" -> mapFilesystemChange(data)
        else -> if (type.startsWith("session.")) mapSessionEvent(type, data, sessionID) else null
    }

    private fun mapSessionEvent(
        type: String,
        data: JsonObject,
        sessionID: String?,
    ): OpenCodeEvent? = when {
        type == "session.deleted" -> sessionID?.let {
            OpenCodeEvent.SessionRefreshRequested(it, removed = true)
        }
        type == "session.execution.failed" -> mapExecutionFailure(data, sessionID)
        type in SESSION_REFRESH_TYPES -> sessionID?.let {
            OpenCodeEvent.SessionRefreshRequested(it)
        }
        type in MESSAGE_REFRESH_TYPES -> sessionID?.let {
            OpenCodeEvent.MessageRefreshRequested(it)
        }
        else -> null
    }

    private fun mapContentEvent(
        type: String,
        data: JsonObject,
        sessionID: String?,
    ): OpenCodeEvent? {
        val payload = parseContentPayload(type, data) ?: return null
        return sessionID?.let { id ->
            OpenCodeEvent.V2ContentChanged(
                sessionID = id,
                messageID = payload.messageID,
                partID = v2AssistantPartId(payload.messageID, payload.kind, payload.ordinal),
                kind = payload.kind,
                text = payload.text,
                phase = payload.phase,
            )
        }
    }

    private fun parseContentPayload(type: String, data: JsonObject): ContentPayload? {
        val kind = type.substringAfter("session.").substringBefore('.')
        val phase = type.substringAfterLast('.')
        val text = when (phase) {
            "delta" -> data.string("delta")
            "ended" -> data.string("text")
            else -> ""
        } ?: return null

        return data.string("assistantMessageID")?.let { messageID ->
            data.int("ordinal")?.takeIf { it >= 0 }?.let { ordinal ->
                ContentPayload(messageID, kind, ordinal, text, phase)
            }
        }
    }

    private fun mapExecutionFailure(data: JsonObject, sessionID: String?): OpenCodeEvent? =
        sessionID?.let { id ->
            OpenCodeEvent.SessionError(id, data.objectValue("error")?.toMessageError())
        }

    private fun JsonObject.toMessageError(): MessageError = MessageError(
        name = string("type") ?: "UnknownError",
        message = string("message"),
        statusCode = (this["status"] as? JsonPrimitive)?.content?.toIntOrNull(),
    )

    private fun mapPermissionAsked(data: JsonObject, sessionID: String?): OpenCodeEvent? {
        val id = data.string("id")
        val action = data.string("action")
        val permission = if (id == null || action == null) {
            null
        } else {
            sessionID?.let { session ->
                val source = data.objectValue("source")
                Permission(
                    id = id,
                    type = action,
                    patterns = data.stringList("resources"),
                    sessionID = session,
                    messageID = source?.string("messageID").orEmpty(),
                    callID = source?.string("id"),
                    metadata = data.objectValue("metadata") ?: JsonObject(emptyMap()),
                    always = data.stringList("save"),
                )
            }
        }
        return permission?.let { OpenCodeEvent.PermissionRequested(it) }
    }

    private fun mapPermissionReply(data: JsonObject, sessionID: String?): OpenCodeEvent? =
        sessionID?.let { session ->
            val requestID = data.string("requestID")
            val reply = data.string("reply")
            if (requestID != null && reply != null) {
                OpenCodeEvent.PermissionReplied(session, requestID, reply)
            } else {
                null
            }
        }

    private fun mapFormEvent(data: JsonObject, sessionID: String?): OpenCodeEvent? =
        (data.objectValue("form")?.string("sessionID") ?: sessionID)
            ?.let { OpenCodeEvent.FormRefreshRequested(it) }

    private fun mapFilesystemChange(data: JsonObject): OpenCodeEvent? {
        val file = data.string("file")
        val change = data.string("event")
        return if (file != null && change != null) {
            OpenCodeEvent.FileWatcherUpdated(file, change)
        } else {
            null
        }
    }

    private fun JsonObject.stringList(name: String): List<String> =
        (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    private fun logUnmapped(type: String) {
        if (loggedUnmappedTypes.add(type)) {
            AppLog.w(TAG, "Unmapped v2 SSE event type=$type")
        }
    }

    private data class ContentPayload(
        val messageID: String,
        val kind: String,
        val ordinal: Int,
        val text: String,
        val phase: String,
    )

    private companion object {
        const val TAG = "V2EventMapper"
        val IGNORED_EVENT_TYPES = setOf(
            "session.tool.input.delta",
            "session.tool.progress",
            "session.compaction.delta",
            "session.usage.updated",
            "server.heartbeat",
            "sync",
            "session.execution.started",
            "session.execution.succeeded",
            "session.execution.interrupted",
            // These only select execution defaults, not metadata represented by the session catalog.
            "session.agent.selected",
            "session.model.selected",
        )
        val CONTENT_EVENT_TYPES = setOf(
            "session.text.started",
            "session.text.delta",
            "session.text.ended",
            "session.reasoning.started",
            "session.reasoning.delta",
            "session.reasoning.ended",
        )
        val SESSION_REFRESH_TYPES = setOf(
            "session.created",
            "session.moved",
            "session.renamed",
            "session.permissions",
            "session.viewed",
            "session.forked",
            "session.revert.staged",
            "session.revert.cleared",
            "session.revert.committed",
        )
        val MESSAGE_REFRESH_TYPES = setOf(
            "session.inbox.delivered",
            "session.inbox.enqueued",
            "session.inbox.cancelled",
            "session.inbox.delivery.changed",
            "session.synthetic",
            "session.step.started",
            "session.step.streamed",
            "session.step.ended",
            "session.step.failed",
            "session.tool.input.started",
            "session.tool.input.ended",
            "session.tool.called",
            "session.tool.success",
            "session.tool.failed",
            "session.retry.scheduled",
            "session.compaction.started",
            "session.compaction.ended",
            "session.compaction.failed",
        )
        val LEGACY_EVENT_TYPES = setOf(
            "session.status",
            "session.idle",
            "pty.created",
            "pty.updated",
            "pty.exited",
            "pty.deleted",
            "installation.updated",
            "installation.update-available",
            "vcs.branch.updated",
            "models-dev.refreshed",
        )
        val MCP_CHANGE_EVENT_TYPES = setOf("mcp.status.changed", "mcp.resources.changed")
        val FORM_EVENT_TYPES = setOf("form.created", "form.replied", "form.cancelled")
    }
}
