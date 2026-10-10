package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.remote.dto.MessageErrorDto
import dev.blazelight.p4oc.data.remote.dto.MessageInfoDto
import dev.blazelight.p4oc.data.remote.dto.MessageTimeDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.ModelRefDto
import dev.blazelight.p4oc.data.remote.dto.PartDto
import dev.blazelight.p4oc.data.remote.dto.PartTimeDto
import dev.blazelight.p4oc.data.remote.dto.PermissionDto
import dev.blazelight.p4oc.data.remote.dto.PermissionToolDto
import dev.blazelight.p4oc.data.remote.dto.PermissionV2RequestDto
import dev.blazelight.p4oc.data.remote.dto.PermissionV2SourceDto
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionModelDto
import dev.blazelight.p4oc.data.remote.dto.SessionRevertDto
import dev.blazelight.p4oc.data.remote.dto.TimeDto
import dev.blazelight.p4oc.data.remote.dto.TokenUsageDto
import dev.blazelight.p4oc.data.remote.dto.ToolStateDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.IOException

/** Translates OpenCode v2 session, message, and permission payloads into the app's DTOs. */
internal class V2SessionDtoMapper(json: Json) {
    private val fields = V2SessionJson()
    private val sessions = V2SessionInfoMapper(fields, json)
    private val messages = V2SessionMessageMapper(fields, json)
    private val permissions = V2SessionPermissionMapper(fields)

    fun toSessionDto(
        element: JsonElement,
        directoryHint: String? = null,
        expectedId: String? = null,
    ): SessionDto = sessions.toSessionDto(element, directoryHint, expectedId)

    fun toSessionRevertDto(element: JsonElement): SessionRevertDto = sessions.toSessionRevertDto(element)

    fun toMessageWrapper(
        element: JsonElement,
        expectedSessionId: String,
        expectedMessageId: String? = null,
    ): MessageWrapperDto = messages.toMessageWrapper(element, expectedSessionId, expectedMessageId)

    fun toPermissionV2RequestDto(
        element: JsonElement,
        expectedSessionId: String? = null,
    ): PermissionV2RequestDto = permissions.toPermissionV2RequestDto(element, expectedSessionId)

    fun toLegacyPermissionDto(element: JsonElement): PermissionDto = permissions.toLegacyPermissionDto(element)

    fun data(element: JsonElement, operation: String): JsonElement = fields.data(element, operation)

    fun requireObject(element: JsonElement, description: String): JsonObject =
        fields.requireObject(element, description)

    fun requireArray(element: JsonElement, description: String): JsonArray =
        fields.requireArray(element, description)

    fun required(objectValue: JsonObject, key: String, description: String): JsonElement =
        fields.required(objectValue, key, description)

    fun requiredString(objectValue: JsonObject, key: String, description: String): String =
        fields.requiredString(objectValue, key, description)

    fun string(objectValue: JsonObject, key: String): String? = fields.string(objectValue, key)

    fun number(objectValue: JsonObject, key: String): Double? = fields.number(objectValue, key)

    fun cursor(objectValue: JsonObject, direction: String): String? = fields.cursor(objectValue, direction)
}

private class V2SessionJson {
    fun data(element: JsonElement, operation: String): JsonElement =
        required(requireObject(element, operation), "data", operation)

    fun requireObject(element: JsonElement, description: String): JsonObject = element as? JsonObject
        ?: throw IOException("OpenCode v2 $description was not a JSON object")

    fun requireArray(element: JsonElement, description: String): JsonArray = element as? JsonArray
        ?: throw IOException("OpenCode v2 $description was not a JSON array")

    fun required(objectValue: JsonObject, key: String, description: String): JsonElement = objectValue[key]
        ?: throw IOException("OpenCode v2 $description did not include `$key`")

    fun requiredString(objectValue: JsonObject, key: String, description: String): String =
        string(objectValue, key) ?: throw IOException("OpenCode v2 $description did not include `$key`")

    fun string(objectValue: JsonObject, key: String): String? = (objectValue[key] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull

    fun requireString(element: JsonElement, description: String): String =
        (element as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: throw IOException("OpenCode v2 $description was not a string")

    fun number(objectValue: JsonObject, key: String): Double? =
        (objectValue[key] as? JsonPrimitive)?.doubleOrNull

    fun numberLong(objectValue: JsonObject, key: String): Long? =
        (objectValue[key] as? JsonPrimitive)?.let { value ->
            value.longOrNull ?: value.doubleOrNull?.toLong()
        }

    fun cursor(objectValue: JsonObject, direction: String): String? {
        val value = objectValue["cursor"] ?: throw IOException("OpenCode v2 page did not include cursor metadata")
        if (value == JsonNull) return null
        return string(requireObject(value, "cursor metadata"), direction)
    }

    fun requiredInt(objectValue: JsonObject, key: String, description: String): Int =
        (objectValue[key] as? JsonPrimitive)?.intOrNull
            ?: throw IOException("OpenCode v2 $description did not include `$key`")
}

private class V2SessionInfoMapper(
    private val fields: V2SessionJson,
    private val json: Json,
) {
    fun toSessionDto(
        element: JsonElement,
        directoryHint: String?,
        expectedId: String?,
    ): SessionDto {
        val info = fields.requireObject(element, "session info")
        val id = fields.requiredString(info, "id", "session info")
        requireExpectedId(id, expectedId)
        val location = info["location"] as? JsonObject
        val directory = sessionDirectory(id, location, directoryHint)
        val time = fields.requireObject(fields.required(info, "time", "session info"), "session time")
        val model = (info["model"] as? JsonObject)?.let(::toSessionModel)
        val revert = (info["revert"] as? JsonObject)?.let(::toSessionRevertDto)

        return SessionDto(
            id = id,
            projectID = fields.requiredString(info, "projectID", "session info"),
            directory = directory,
            path = fields.string(info, "subpath"),
            // Ownership only: a fork records where it was copied from but is an independent session, as on v1.
            parentID = fields.string(info, "parentID"),
            // SessionDto requires a title; the app presents blank titles as “Untitled session”.
            title = fields.string(info, "title") ?: "",
            version = null,
            time = TimeDto(
                created = createdAt(time),
                updated = fields.numberLong(time, "updated"),
                compacting = fields.numberLong(time, "compacting"),
            ),
            cost = fields.number(info, "cost"),
            tokens = (info["tokens"] as? JsonObject)?.let { json.decodeFromJsonElement<TokenUsageDto>(it) },
            agent = fields.string(info, "agent"),
            model = model,
            metadata = info["metadata"] as? JsonObject,
            permission = info["permissions"],
            revert = revert,
        )
    }

    fun toSessionRevertDto(element: JsonElement): SessionRevertDto {
        val info = fields.requireObject(element, "session revert")
        // v2 also returns structured changed-file details; the legacy DTO has no file-list field.
        return SessionRevertDto(
            messageID = fields.requiredString(info, "messageID", "session revert"),
            partID = fields.string(info, "partID"),
            snapshot = fields.string(info, "snapshot"),
            diff = null,
        )
    }

    private fun requireExpectedId(id: String, expectedId: String?) {
        if (expectedId != null && id != expectedId) {
            throw IOException("OpenCode v2 returned session `$id` for requested session `$expectedId`")
        }
    }

    private fun sessionDirectory(id: String, location: JsonObject?, directoryHint: String?): String =
        location?.let { fields.string(it, "directory") } ?: directoryHint
            ?: throw IOException("OpenCode v2 session `$id` did not include location.directory")

    private fun toSessionModel(modelInfo: JsonObject): SessionModelDto {
        val modelId = fields.string(modelInfo, "modelID") ?: fields.string(modelInfo, "id")
            ?: throw IOException("OpenCode v2 session model did not include modelID")
        return SessionModelDto(
            id = modelId,
            providerID = fields.requiredString(modelInfo, "providerID", "session model"),
            variant = fields.string(modelInfo, "variant"),
        )
    }

    private fun createdAt(time: JsonObject): Long = fields.numberLong(time, "created")
        ?: throw IOException("OpenCode v2 session time did not include created")
}

private class V2SessionMessageMapper(
    private val fields: V2SessionJson,
    private val json: Json,
) {
    fun toMessageWrapper(
        element: JsonElement,
        expectedSessionId: String,
        expectedMessageId: String?,
    ): MessageWrapperDto {
        val message = fields.requireObject(element, "session message")
        val id = fields.requiredString(message, "id", "session message")
        requireExpectedMessageId(id, expectedMessageId)
        requireExpectedSessionId(id, fields.string(message, "sessionID"), expectedSessionId)
        val type = fields.requiredString(message, "type", "session message")
        val time = fields.requireObject(fields.required(message, "time", "session message"), "message time")
        val model = (message["model"] as? JsonObject)?.let(::toModelRef)
        val parts = messageParts(message, id, expectedSessionId, type)

        return MessageWrapperDto(
            info = MessageInfoDto(
                id = id,
                sessionID = expectedSessionId,
                time = MessageTimeDto(
                    created = messageCreatedAt(time),
                    completed = fields.numberLong(time, "completed"),
                ),
                role = if (type == "assistant") "assistant" else "user",
                parentID = fields.string(message, "parentID"),
                model = model,
                modelID = model?.modelID,
                providerID = model?.providerID,
                agent = fields.string(message, "agent"),
                cost = fields.number(message, "cost"),
                tokens = (message["tokens"] as? JsonObject)?.let { json.decodeFromJsonElement<TokenUsageDto>(it) },
                error = (message["error"] as? JsonObject)?.let(::toMessageErrorDto),
                finish = fields.string(message, "finish"),
            ),
            parts = parts,
        )
    }

    private fun messageParts(
        message: JsonObject,
        id: String,
        sessionId: String,
        type: String,
    ): List<PartDto> = when (type) {
        "user", "synthetic" -> userMessageParts(message, id, sessionId, synthetic = type == "synthetic")
        "assistant" -> assistantMessageParts(message, id, sessionId)
        else -> throw IOException("OpenCode v2 message `$id` has unsupported chat type `$type`")
    }

    private fun requireExpectedMessageId(id: String, expectedId: String?) {
        if (expectedId != null && id != expectedId) {
            throw IOException("OpenCode v2 returned message `$id` for requested message `$expectedId`")
        }
    }

    private fun requireExpectedSessionId(id: String, actualSessionId: String?, expectedSessionId: String) {
        if (actualSessionId != null && actualSessionId != expectedSessionId) {
            throw IOException(
                "OpenCode v2 returned message `$id` from session `$actualSessionId` " +
                    "while loading `$expectedSessionId`",
            )
        }
    }

    private fun messageCreatedAt(time: JsonObject): Long = fields.numberLong(time, "created")
        ?: throw IOException("OpenCode v2 message time did not include created")

    private fun toModelRef(modelRef: JsonObject): ModelRefDto = ModelRefDto(
        providerID = fields.requiredString(modelRef, "providerID", "message model"),
        modelID = fields.requiredString(modelRef, "id", "message model"),
    )

    private fun userMessageParts(
        message: JsonObject,
        messageId: String,
        sessionId: String,
        synthetic: Boolean,
    ): List<PartDto> = buildList {
        val text = fields.string(message, "text")
        if (!text.isNullOrEmpty()) {
            add(
                PartDto(
                    id = localPartId(messageId, "text", 0),
                    sessionID = sessionId,
                    messageID = messageId,
                    type = "text",
                    text = text,
                    synthetic = synthetic,
                ),
            )
        }
        (message["files"] as? JsonArray).orEmpty().forEachIndexed { index, attachment ->
            add(toMessageFilePart(attachment, messageId, sessionId, index))
        }
        (message["agents"] as? JsonArray).orEmpty().forEachIndexed { index, attachment ->
            val agent = fields.requireObject(attachment, "user agent attachment")
            val mention = (agent["mention"] as? JsonObject)?.let(::agentMention)
            add(
                PartDto(
                    id = localPartId(messageId, "agent", index),
                    sessionID = sessionId,
                    messageID = messageId,
                    type = "agent",
                    name = fields.requiredString(agent, "name", "user agent attachment"),
                    source = mention,
                ),
            )
        }
        // Skill references carry no chat-visible content; ignore them rather than fail the whole history.
    }

    private fun agentMention(value: JsonObject): JsonObject = buildJsonObject {
        put("value", fields.requiredString(value, "text", "agent mention"))
        put("start", fields.requiredInt(value, "start", "agent mention"))
        put("end", fields.requiredInt(value, "end", "agent mention"))
    }

    private fun assistantMessageParts(message: JsonObject, messageId: String, sessionId: String): List<PartDto> {
        val content = fields.requireArray(fields.required(message, "content", "assistant message"), "assistant content")
        val ordinals = mutableMapOf<String, Int>()
        // Unknown future content types are skipped so one item never makes the whole history unloadable.
        return content.mapIndexedNotNull { index, item ->
            val part = fields.requireObject(item, "assistant content item")
            val type = fields.requiredString(part, "type", "assistant content item")
            val ordinal = ordinals.getOrDefault(type, 0)
            ordinals[type] = ordinal + 1
            val id = assistantPartId(part, messageId, type, ordinal, index)
            when (type) {
                "text", "reasoning" -> PartDto(
                    id = id,
                    sessionID = sessionId,
                    messageID = messageId,
                    type = type,
                    text = fields.requiredString(part, "text", "assistant $type content"),
                    time = if (type == "reasoning") partTime(part["time"] as? JsonObject) else null,
                    metadata = part["metadata"] as? JsonObject,
                )
                "tool" -> toToolPart(part, id, sessionId, messageId)
                else -> null
            }
        }
    }

    private fun assistantPartId(part: JsonObject, messageId: String, type: String, ordinal: Int, index: Int): String =
        if (type == "text" || type == "reasoning") {
            v2AssistantPartId(messageId, type, ordinal)
        } else {
            fields.string(part, "id") ?: localPartId(messageId, type, index)
        }

    private fun toToolPart(part: JsonObject, id: String, sessionId: String, messageId: String): PartDto {
        val state = fields.requireObject(
            fields.required(part, "state", "assistant tool content"),
            "assistant tool state",
        )
        val stateName = fields.requiredString(state, "status", "assistant tool state")
        val input = state["input"]
        val inputObject = input as? JsonObject
        val rawInput = rawInput(input)
        val status = toolStatus(stateName)
        requireToolInput(stateName, inputObject)
        val toolTime = (part["time"] as? JsonObject)?.let(::toolTime)
        val content = toolContent(state["content"])

        return PartDto(
            id = id,
            sessionID = sessionId,
            messageID = messageId,
            type = "tool",
            callID = fields.requiredString(part, "id", "assistant tool content"),
            toolName = toolName(part),
            state = ToolStateDto(
                status = status,
                input = inputObject,
                raw = if (stateName == "streaming") rawInput else null,
                title = fields.string(state, "title"),
                output = if (stateName == "completed") content.text else null,
                error = if (stateName == "error") toolErrorText(state["error"]) ?: content.text else null,
                time = toolTime,
                metadata = state["metadata"] as? JsonObject,
                attachments = content.files.mapIndexed { index, file ->
                    PartDto(
                        id = "$id.file.$index",
                        sessionID = sessionId,
                        messageID = messageId,
                        type = "file",
                        mime = fields.string(file, "mime"),
                        filename = fields.string(file, "name"),
                        url = fields.string(file, "uri"),
                    )
                }.ifEmpty { null },
            ),
        )
    }

    private fun rawInput(input: JsonElement?): String? = when (input) {
        null, JsonNull -> null
        is JsonPrimitive -> if (input.isString) input.content else input.toString()
        else -> input.toString()
    }

    private fun toolStatus(stateName: String): String = when (stateName) {
        "running" -> "running"
        "completed" -> "completed"
        "error" -> "error"
        // "streaming" and any state a newer server adds are shown as in progress.
        else -> "pending"
    }

    private fun requireToolInput(stateName: String, input: JsonObject?) {
        if (stateName in SETTLED_TOOL_STATES && input == null) {
            throw IOException("OpenCode v2 `$stateName` tool state did not include object input")
        }
    }

    private fun toolTime(value: JsonObject): PartTimeDto? = PartTimeDto(
        start = fields.numberLong(value, "ran") ?: fields.numberLong(value, "created"),
        end = fields.numberLong(value, "completed"),
    ).takeIf { it.start != null || it.end != null }

    private fun toolName(part: JsonObject): String = fields.string(part, "name") ?: fields.string(part, "tool")
        ?: throw IOException("OpenCode v2 assistant tool content did not include its tool name")

    private class ToolContent(val text: String?, val files: List<JsonObject>)

    /** Text items become the tool output; file items (e.g. an image the read tool returned) become attachments. */
    private fun toolContent(value: JsonElement?): ToolContent = when (value) {
        null, JsonNull -> ToolContent(null, emptyList())
        is JsonPrimitive -> ToolContent(if (value.isString) value.content else value.toString(), emptyList())
        is JsonArray -> {
            val items = value.filterIsInstance<JsonObject>()
            val texts = items.filter { fields.string(it, "type") == "text" }.mapNotNull { fields.string(it, "text") }
            ToolContent(
                text = texts.takeIf { it.isNotEmpty() }?.joinToString(separator = ""),
                files = items.filter { item ->
                    fields.string(item, "type") == "file" &&
                        fields.string(item, "uri") != null &&
                        fields.string(item, "mime") != null
                },
            )
        }
        else -> ToolContent(value.toString(), emptyList())
    }

    private fun toolErrorText(value: JsonElement?): String? = (value as? JsonObject)?.let { error ->
        fields.string(error, "message") ?: fields.string(error, "type")
    }

    private fun toMessageFilePart(
        element: JsonElement,
        messageId: String,
        sessionId: String,
        index: Int,
    ): PartDto {
        val attachment = fields.requireObject(element, "user file attachment")
        val source = attachment["source"] as? JsonObject
        val mime = fields.string(attachment, "mime")
            ?: throw IOException("OpenCode v2 user file attachment did not include its MIME type")
        val uri = source?.let { fields.string(it, "uri") }
        val data = fields.string(attachment, "data")
        val url = uri ?: data?.let { "data:$mime;base64,$it" }
            ?: throw IOException("OpenCode v2 user file attachment did not include a URI or inline data")
        return PartDto(
            id = localPartId(messageId, "file", index),
            sessionID = sessionId,
            messageID = messageId,
            type = "file",
            mime = mime,
            filename = fields.string(attachment, "name"),
            url = url,
        )
    }

    private fun toMessageErrorDto(error: JsonObject): MessageErrorDto {
        val type = fields.string(error, "type")
            ?: throw IOException("OpenCode v2 message error did not include a type")
        val data = buildJsonObject {
            fields.string(error, "message")?.let { put("message", it) }
            error["status"]?.let { put("statusCode", it) }
        }
        return MessageErrorDto(name = type, data = data)
    }

    private fun partTime(value: JsonObject?): PartTimeDto? = value?.let {
        PartTimeDto(
            start = fields.numberLong(it, "start") ?: fields.numberLong(it, "created"),
            end = fields.numberLong(it, "end") ?: fields.numberLong(it, "completed"),
        ).takeIf { time -> time.start != null || time.end != null }
    }

    private fun localPartId(messageId: String, type: String, index: Int): String =
        "p4oc.v2.$messageId.$type.$index"
}

private class V2SessionPermissionMapper(private val fields: V2SessionJson) {
    fun toPermissionV2RequestDto(element: JsonElement, expectedSessionId: String?): PermissionV2RequestDto {
        val request = fields.requireObject(element, "permission request")
        val sessionId = fields.requiredString(request, "sessionID", "permission request")
        requireExpectedSessionId(sessionId, expectedSessionId)
        val source = (request["source"] as? JsonObject)?.let(::toPermissionSource)
        return PermissionV2RequestDto(
            id = fields.requiredString(request, "id", "permission request"),
            sessionID = sessionId,
            action = fields.requiredString(request, "action", "permission request"),
            resources = fields.requireArray(
                fields.required(request, "resources", "permission request"),
                "permission resources",
            ).map { fields.requireString(it, "permission resource") },
            save = (request["save"] as? JsonArray).orEmpty()
                .map { fields.requireString(it, "saved permission") },
            metadata = request["metadata"] as? JsonObject,
            source = source,
        )
    }

    fun toLegacyPermissionDto(element: JsonElement): PermissionDto {
        val request = fields.requireObject(element, "permission request")
        val source = (request["source"] as? JsonObject)
            ?.takeIf { fields.string(it, "type") == "tool" }
            ?.let(::toPermissionTool)
        return PermissionDto(
            id = fields.requiredString(request, "id", "permission request"),
            permission = fields.requiredString(request, "action", "permission request"),
            patterns = fields.requireArray(
                fields.required(request, "resources", "permission request"),
                "permission resources",
            ).map { fields.requireString(it, "permission resource") },
            sessionID = fields.requiredString(request, "sessionID", "permission request"),
            metadata = request["metadata"] as? JsonObject ?: buildJsonObject {},
            always = (request["save"] as? JsonArray).orEmpty()
                .map { fields.requireString(it, "saved permission") },
            tool = source,
        )
    }

    private fun requireExpectedSessionId(sessionId: String, expectedSessionId: String?) {
        if (expectedSessionId != null && sessionId != expectedSessionId) {
            throw IOException(
                "OpenCode v2 returned a permission request for session `$sessionId` " +
                    "while loading `$expectedSessionId`",
            )
        }
    }

    private fun toPermissionSource(value: JsonObject): PermissionV2SourceDto = PermissionV2SourceDto(
        type = fields.requiredString(value, "type", "permission source"),
        messageID = fields.requiredString(value, "messageID", "permission source"),
        callID = fields.requiredString(value, "id", "permission source"),
    )

    private fun toPermissionTool(value: JsonObject): PermissionToolDto = PermissionToolDto(
        messageID = fields.requiredString(value, "messageID", "permission source"),
        callID = fields.requiredString(value, "id", "permission source"),
    )
}

/** Tool states that always carry their parsed input object. */
private val SETTLED_TOOL_STATES = setOf("running", "completed", "error")
