package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ExecuteCommandRequest
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.InitSessionRequest
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.PermissionDto
import dev.blazelight.p4oc.data.remote.dto.PermissionResponseRequest
import dev.blazelight.p4oc.data.remote.dto.PermissionV2RequestListResponseDto
import dev.blazelight.p4oc.data.remote.dto.RevertSessionRequest
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import dev.blazelight.p4oc.data.remote.dto.SnapshotFileDiffDto
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Session/chat transport mapping for OpenCode's `/api` v2 surface. */
@Suppress("TooManyFunctions")
internal class V2SessionOpenCodeApi(
    private val next: OpenCodeApi,
    private val http: V2Http,
    private val json: Json,
) : OpenCodeApi by next {
    private val mapper = V2SessionDtoMapper(json)
    private val commandSupport = V2SessionCommandSupport(http, mapper)

    override suspend fun listSessions(
        directory: String?,
        workspace: String?,
        scope: String?,
        path: String?,
        roots: Boolean?,
        start: Long?,
        search: String?,
        limit: Int?,
    ): List<SessionDto> {
        if (workspace != null) {
            unsupported("list sessions", "v2 session lists are scoped by directory, not workspace")
        }
        if (scope != null && scope != "project") {
            unsupported("list sessions", "v2 has no equivalent for the legacy scope filter")
        }
        if (start != null) {
            unsupported("list sessions", "v2 uses opaque cursors instead of the legacy start timestamp")
        }
        return listV2Sessions(
            directory = directory,
            subpath = path,
            parentID = if (roots == true) "null" else null,
            search = search,
            limit = limit,
        )
    }

    override suspend fun createSession(
        directory: String?,
        workspace: String?,
        request: CreateSessionRequest,
    ): SessionDto {
        if (workspace != null) {
            unsupported("create session", "v2 sessions are created at a location, not a workspace")
        }
        if (request.parentID != null) {
            unsupported("create child session", "v2 session creation has no parentID; use forkSession")
        }

        val body = buildJsonObject {
            request.title?.let { put("title", it) }
            put(
                "location",
                directory?.let { buildJsonObject { put("directory", it) } } ?: JsonNull,
            )
        }
        val response = http.request("POST", "api/session", body = body)
        return mapper.toSessionDto(mapper.data(response, "create session"), directoryHint = directory)
    }

    override suspend fun getSession(id: String, directory: String?, workspace: String?): SessionDto =
        mapper.toSessionDto(
            mapper.data(http.request("GET", sessionPath(id)), "get session"),
            expectedId = id,
        )

    override suspend fun deleteSession(id: String, directory: String?, workspace: String?): Boolean {
        http.request("DELETE", sessionPath(id))
        return true
    }

    override suspend fun updateSession(
        id: String,
        request: UpdateSessionRequest,
        directory: String?,
        workspace: String?,
    ): SessionDto {
        if (request.archived != null) unsupported("update session", "v2 session archival is server-managed")
        if (request.title == null) {
            throw IOException("OpenCode v2 session update requires a title change")
        }
        http.request(
            "PATCH",
            sessionPath(id),
            body = buildJsonObject { put("title", request.title) },
        )
        return getSession(id, directory, workspace)
    }

    override suspend fun forkSession(
        id: String,
        request: ForkSessionRequest,
        directory: String?,
        workspace: String?,
    ): SessionDto {
        val body = buildJsonObject {
            request.messageID?.let { put("before", it) }
        }
        return mapper.toSessionDto(
            mapper.data(http.request("POST", "${sessionPath(id)}/fork", body = body), "fork session"),
        )
    }

    override suspend fun getSessionChildren(
        id: String,
        directory: String?,
        workspace: String?,
    ): List<SessionDto> {
        val parent = getSession(id, directory, workspace)
        return listV2Sessions(directory = parent.directory).filter { it.parentID == id }
    }

    override suspend fun getSessionStatuses(directory: String?, workspace: String?): Map<String, SessionStatusDto> {
        if (workspace != null) unsupported("session status", "v2 statuses are scoped by session location")
        val response = mapper.requireObject(http.request("GET", "api/session/active"), "active sessions")
        val active = mapper.requireObject(mapper.required(response, "data", "active sessions"), "active sessions")
        // Every listed session is active; its sub-state ("running", or one a newer server adds) is still busy.
        val running = active.keys
        if (directory == null) return running.associateWith { SessionStatusDto(type = "busy") }
        return running.mapNotNull { id ->
            val session = try {
                getSession(id, directory, null)
            } catch (error: HttpException) {
                if (error.code() != HTTP_NOT_FOUND) throw error
                null
            }
            if (session?.directory == directory) id to SessionStatusDto(type = "busy") else null
        }.toMap()
    }

    override suspend fun abortSession(
        id: String,
        directory: String?,
        workspace: String?,
    ): Response<Unit> {
        val result = mapper.requireObject(
            http.request(
                "POST",
                "${sessionPath(id)}/interrupt",
                query = mapOf("resume" to "false"),
            ),
            "interrupt session",
        )
        val interrupted = (result["interrupted"] as? JsonPrimitive)?.booleanOrNull
            ?: throw IOException("OpenCode v2 interrupt response did not include `interrupted`")
        if (!interrupted) {
            throw IOException("OpenCode v2 did not interrupt session `$id`")
        }
        return Response.success(Unit)
    }

    override suspend fun summarizeSession(
        id: String,
        directory: String?,
        workspace: String?,
    ): Boolean {
        mapper.data(
            http.request("POST", "${sessionPath(id)}/compact", body = buildJsonObject {}),
            "compact session",
        )
        return true
    }

    override suspend fun getSessionDiff(
        id: String,
        messageID: String?,
        directory: String?,
        workspace: String?,
    ): List<SnapshotFileDiffDto> {
        if (messageID != null) {
            val messageType = mapper.string(
                mapper.requireObject(
                    mapper.data(
                        http.request("GET", "${sessionPath(id)}/message/$messageID"),
                        "get diff boundary message",
                    ),
                    "get diff boundary message",
                ),
                "type",
            )
            if (messageType != "user") {
                throw IOException(
                    "OpenCode v2 session diffs require a user-message ID; `$messageID` is not a user message",
                )
            }
        }
        val response = mapper.data(
            http.request(
                "GET",
                "${sessionPath(id)}/diff",
                query = mapOf("from" to messageID),
            ),
            "load session diff",
        )
        return mapper.requireArray(response, "load session diff").map { diff ->
            val value = mapper.requireObject(diff, "session diff entry")
            SnapshotFileDiffDto(
                file = mapper.string(value, "file"),
                patch = mapper.string(value, "patch"),
                additions = mapper.number(value, "additions")
                    ?: throw IOException("OpenCode v2 diff entry did not include additions"),
                deletions = mapper.number(value, "deletions")
                    ?: throw IOException("OpenCode v2 diff entry did not include deletions"),
                status = mapper.string(value, "status"),
            )
        }
    }

    override suspend fun revertSession(
        id: String,
        request: RevertSessionRequest,
        directory: String?,
        workspace: String?,
    ): SessionDto {
        if (request.partID != null) {
            unsupported("revert session", "OpenCode v2 revert boundaries do not support a partID")
        }
        val staged = mapper.toSessionRevertDto(
            mapper.data(
                http.request(
                    "POST",
                    "${sessionPath(id)}/revert/stage",
                    body = buildJsonObject {
                        put("messageID", request.messageID)
                        put("files", true)
                    },
                ),
                "stage session revert",
            ),
        )
        return getSession(id, directory, workspace).copy(revert = staged)
    }

    override suspend fun unrevertSession(
        id: String,
        directory: String?,
        workspace: String?,
    ): SessionDto {
        http.request("DELETE", "${sessionPath(id)}/revert")
        return getSession(id, directory, workspace)
    }

    override suspend fun getMessages(
        sessionId: String,
        limit: Int?,
        before: String?,
        directory: String?,
        workspace: String?,
    ): List<MessageWrapperDto> {
        if (before != null) {
            unsupported(
                "load messages before a message",
                "v2 pagination uses opaque cursors that cannot be derived from a message ID",
            )
        }
        if (limit == 0) return emptyList()

        val messages = ArrayList<MessageWrapperDto>(limit?.coerceAtLeast(0) ?: DEFAULT_PAGE_CAPACITY)
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var hasMore = true
        while (hasMore) {
            val query = buildMap {
                val remaining = limit?.minus(messages.size)
                if (remaining != null) put("limit", remaining.toString())
                when (val pageCursor = cursor) {
                    null -> put("order", "desc")
                    else -> put("cursor", pageCursor)
                }
            }
            val page = mapper.requireObject(
                http.request("GET", "${sessionPath(sessionId)}/message", query = query),
                "list session messages",
            )
            val data = mapper.requireArray(
                mapper.required(page, "data", "list session messages"),
                "list session messages",
            )
            val full = appendMessagePage(data, messages, sessionId, limit)
            cursor = if (full) null else nextPageCursor(page, seenCursors, "message", mapper)
            hasMore = cursor != null
        }
        // Pages are fetched newest first so the limit keeps the latest messages;
        // callers expect v1's oldest-first order.
        return messages.asReversed()
    }

    private fun appendMessagePage(
        data: JsonArray,
        messages: MutableList<MessageWrapperDto>,
        sessionId: String,
        limit: Int?,
    ): Boolean {
        for (item in data) {
            val message = mapper.requireObject(item, "session message")
            val type = mapper.requiredString(message, "type", "session message")
            // The chat timeline intentionally renders user, synthetic, and assistant messages only.
            if (type !in NON_RENDERABLE_MESSAGE_TYPES) {
                messages += mapper.toMessageWrapper(message, expectedSessionId = sessionId)
                if (limit != null && messages.size >= limit) return true
            }
        }
        return false
    }

    override suspend fun getMessage(
        sessionId: String,
        messageId: String,
        directory: String?,
        workspace: String?,
    ): MessageWrapperDto {
        val message = mapper.requireObject(
            mapper.data(
                http.request("GET", "${sessionPath(sessionId)}/message/$messageId"),
                "get session message",
            ),
            "get session message",
        )
        val type = mapper.requiredString(message, "type", "session message")
        if (type in NON_RENDERABLE_MESSAGE_TYPES) {
            throw IOException("OpenCode v2 message `$messageId` is a `$type` control record, not a chat message")
        }
        return mapper.toMessageWrapper(message, expectedSessionId = sessionId, expectedMessageId = messageId)
    }

    override suspend fun sendMessageAsync(
        sessionId: String,
        request: SendMessageRequest,
        directory: String?,
        workspace: String?,
    ) {
        val body = commandSupport.promptBody(request)
        request.model?.let { model ->
            http.request(
                "POST",
                "${sessionPath(sessionId)}/model",
                body = buildJsonObject {
                    put(
                        "model",
                        buildJsonObject {
                            put("providerID", model.providerID)
                            put("id", model.modelID)
                            request.variant?.let { put("variant", it) }
                        },
                    )
                },
            )
        }
        request.agent?.let { agent ->
            val agentId = resolveV2AgentId(http, mapper, agent, directory)
            http.request(
                "POST",
                "${sessionPath(sessionId)}/agent",
                body = buildJsonObject { put("agent", agentId) },
            )
        }
        http.request("POST", "${sessionPath(sessionId)}/prompt", body = body)
    }

    override suspend fun initSession(
        id: String,
        request: InitSessionRequest,
        directory: String?,
        workspace: String?,
    ): Boolean {
        if (workspace != null) unsupported("initialize session", "v2 sessions are scoped by location")
        http.request(
            "POST",
            "${sessionPath(id)}/model",
            body = buildJsonObject {
                put(
                    "model",
                    buildJsonObject {
                        put("providerID", request.providerID)
                        put("id", request.modelID)
                    },
                )
            },
        )
        dispatchCommand(id, ExecuteCommandRequest(command = "init", arguments = ""))
        return true
    }

    internal suspend fun dispatchCommand(sessionId: String, request: ExecuteCommandRequest) {
        if (request.messageID != null || request.agent != null || request.model != null) {
            unsupported(
                "execute command",
                "v2 commands do not accept a legacy message ID, agent override, or model override",
            )
        }
        val body = buildJsonObject {
            put("name", request.command)
            put("text", request.arguments)
            put("files", JsonArray(emptyList()))
            put("agents", JsonArray(emptyList()))
            put("skills", JsonArray(emptyList()))
        }
        http.request("POST", "${sessionPath(sessionId)}/command", body = body)
    }

    override suspend fun executeCommand(
        sessionId: String,
        request: ExecuteCommandRequest,
        directory: String?,
        workspace: String?,
    ): MessageWrapperDto = unsupported(
        "execute command",
        "OpenCode v2 returns 204 without a message body; use dispatchCommand",
    )

    override suspend fun executeShellCommand(
        sessionId: String,
        request: ShellCommandRequest,
        directory: String?,
        workspace: String?,
    ): MessageWrapperDto = unsupported(
        "execute shell command",
        "OpenCode v2 returns a shell transcript in the session timeline, " +
            "not a legacy message wrapper; use executeShellForOutput",
    )

    override suspend fun listPermissions(
        directory: String?,
        workspace: String?,
    ): List<PermissionDto> {
        if (workspace != null) {
            unsupported("list permissions", "v2 permissions are scoped by location, not workspace")
        }
        val response = mapper.data(
            http.request(
                "GET",
                "api/permission/request",
                query = locationQuery(directory),
            ),
            "list permission requests",
        )
        return mapper.requireArray(response, "list permission requests")
            .map { mapper.toLegacyPermissionDto(it) }
    }

    override suspend fun listSessionPermissionsV2(sessionId: String): PermissionV2RequestListResponseDto {
        val response = mapper.data(
            http.request("GET", "${sessionPath(sessionId)}/permission"),
            "list session permissions",
        )
        return PermissionV2RequestListResponseDto(
            data = mapper.requireArray(response, "list session permissions").map {
                mapper.toPermissionV2RequestDto(it, expectedSessionId = sessionId)
            },
        )
    }

    override suspend fun respondToPermissionV2(
        sessionId: String,
        requestId: String,
        request: PermissionResponseRequest,
    ): Response<Unit> {
        if (request.reply !in PERMISSION_DECISIONS) {
            throw IOException("Unsupported v2 permission decision `${request.reply}`")
        }
        http.request(
            "POST",
            "${sessionPath(sessionId)}/permission/$requestId/reply",
            body = buildJsonObject {
                put("decision", request.reply)
                request.message?.let { put("message", it) }
            },
        )
        return Response.success(Unit)
    }

    /** Run a v2 shell request and return its exact persisted shell transcript output. */
    internal suspend fun executeShellForOutput(sessionId: String, command: String): String =
        commandSupport.executeShellForOutput(sessionId, command)

    private suspend fun listV2Sessions(
        directory: String? = null,
        subpath: String? = null,
        parentID: String? = null,
        search: String? = null,
        limit: Int? = null,
    ): List<SessionDto> {
        if (limit == 0) return emptyList()
        val initialCapacity = minOf(limit?.coerceAtLeast(0) ?: DEFAULT_PAGE_CAPACITY, DEFAULT_PAGE_CAPACITY)
        val sessions = ArrayList<SessionDto>(initialCapacity)
        val seenCursors = mutableSetOf<String>()
        val filters = buildMap {
            directory?.let { put("directory", it) }
            subpath?.let { put("subpath", it) }
            parentID?.let { put("parentID", it) }
            search?.let { put("search", it) }
        }
        var cursor: String? = null
        var hasMore = true
        while (hasMore) {
            val query = sessionPageQuery(filters, limit?.minus(sessions.size), cursor)
            val page = mapper.requireObject(http.request("GET", "api/session", query = query), "list sessions")
            val data = mapper.requireArray(mapper.required(page, "data", "list sessions"), "list sessions")
            val full = appendSessionPage(data, sessions, directory, limit, mapper)
            cursor = if (full) null else nextPageCursor(page, seenCursors, "session", mapper)
            hasMore = cursor != null
        }
        return sessions
    }

    private companion object {
        const val DEFAULT_PAGE_CAPACITY = 50
        val PERMISSION_DECISIONS = setOf("once", "always", "reject")
        val NON_RENDERABLE_MESSAGE_TYPES = setOf(
            "agent-switched",
            "model-switched",
            "location-switched",
            "system",
            "skill",
            "shell",
            "compaction",
            "idle",
        )
    }
}
private fun sessionPageQuery(
    filters: Map<String, String>,
    remaining: Int?,
    cursor: String?,
): Map<String, String> = buildMap {
    putAll(filters)
    if (remaining != null) put("limit", remaining.toString())
    when (cursor) {
        null -> put("order", "desc")
        else -> put("cursor", cursor)
    }
}

private fun appendSessionPage(
    data: JsonArray,
    sessions: MutableList<SessionDto>,
    directory: String?,
    limit: Int?,
    mapper: V2SessionDtoMapper,
): Boolean {
    for (item in data) {
        sessions += mapper.toSessionDto(item, directoryHint = directory)
        if (limit != null && sessions.size >= limit) return true
    }
    return false
}

private fun nextPageCursor(
    page: JsonObject,
    seenCursors: MutableSet<String>,
    resource: String,
    mapper: V2SessionDtoMapper,
): String? {
    val next = mapper.cursor(page, "next") ?: return null
    if (!seenCursors.add(next)) {
        throw IOException("OpenCode v2 $resource pagination repeated a cursor")
    }
    return next
}

private suspend fun resolveV2AgentId(
    http: V2Http,
    mapper: V2SessionDtoMapper,
    name: String,
    directory: String?,
): String {
    val agents = mapper.requireArray(
        mapper.data(http.request("GET", "api/agent", query = locationQuery(directory)), "list agents"),
        "list agents",
    )
    return agents.asSequence()
        .map { mapper.requireObject(it, "agent") }
        .firstOrNull { mapper.string(it, "name") == name || mapper.string(it, "id") == name }
        ?.let { mapper.requiredString(it, "id", "agent") }
        ?: throw IOException("OpenCode v2 agent `$name` is not available in this workspace")
}

private fun locationQuery(directory: String?): Map<String, String?> =
    if (directory == null) emptyMap() else mapOf("location[directory]" to directory)

private fun sessionPath(id: String): String = "api/session/$id"

private fun unsupported(operation: String, reason: String): Nothing =
    throw IOException("$operation is unavailable through the current OpenCode v2 API contract: $reason")

/** V2 content events address a type-specific ordinal, not an index in the mixed content list. */
internal fun v2AssistantPartId(messageId: String, kind: String, ordinal: Int): String =
    "p4oc.v2.$messageId.$kind.$ordinal"
