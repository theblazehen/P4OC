package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.remote.dto.PartInputDto
import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

/** Builds v2 prompt bodies and reads the persisted output from v2 shell transcripts. */
internal class V2SessionCommandSupport(
    private val http: V2Http,
    private val fields: V2SessionDtoMapper,
) {
    fun promptBody(request: SendMessageRequest): JsonObject {
        requireRepresentablePrompt(request)
        val parts = V2PromptParts()
        request.parts.forEach(parts::append)
        return buildJsonObject {
            request.messageID?.let { put("id", it) }
            put("text", parts.text.toString())
            put("files", JsonArray(parts.files))
            put("agents", JsonArray(parts.agents))
        }
    }

    suspend fun executeShellForOutput(sessionId: String, command: String): String {
        val messageId = "msg_${UUID.randomUUID()}"
        http.request(
            "POST",
            "api/session/$sessionId/shell",
            body = buildJsonObject {
                put("id", messageId)
                put("command", command)
            },
        )
        return awaitShellOutput(sessionId, messageId)
            ?: throw IOException("OpenCode v2 shell command exceeded ${SHELL_COMMAND_TIMEOUT_MS}ms")
    }

    private fun requireRepresentablePrompt(request: SendMessageRequest) {
        if (request.system != null) {
            unsupportedPrompt("OpenCode v2 prompt does not accept a per-message system prompt")
        }
        if (request.tools != null) {
            unsupportedPrompt("OpenCode v2 prompt does not accept per-message tool overrides")
        }
        if (request.variant != null && request.model == null) {
            unsupportedPrompt("OpenCode v2 requires a selected model for a reasoning variant")
        }
        if (request.noReply == true) {
            unsupportedPrompt("OpenCode v2 prompt has no noReply equivalent")
        }
    }

    private suspend fun awaitShellOutput(sessionId: String, messageId: String): String? =
        withTimeoutOrNull(SHELL_COMMAND_TIMEOUT_MS) {
            while (true) {
                val message = fetchShellMessage(sessionId, messageId)
                val output = message?.let { shellOutputIfComplete(it, sessionId, messageId) }
                if (output != null) return@withTimeoutOrNull output
                delay(SHELL_POLL_INTERVAL_MS)
            }
            error("unreachable")
        }

    private suspend fun fetchShellMessage(sessionId: String, messageId: String): JsonObject? = try {
        val element = http.request("GET", "api/session/$sessionId/message/$messageId")
        val data = fields.data(element, "read shell output")
        fields.requireObject(data, "shell message")
    } catch (error: HttpException) {
        if (error.code() == HTTP_NOT_FOUND) null else throw error
    }

    private fun shellOutputIfComplete(message: JsonObject, sessionId: String, messageId: String): String? {
        requireShellMessageId(message, messageId)
        requireShellMessageSession(message, sessionId)
        requireShellMessageType(message)
        val status = requiredShellStatus(message)
        return when (status) {
            "running" -> null
            "timeout" -> throw IOException("OpenCode v2 shell command timed out")
            "exited" -> completedShellOutput(message)
            else -> throw IOException("OpenCode v2 shell command ended with status `$status`")
        }
    }

    private fun requiredShellStatus(message: JsonObject): String =
        fields.string(message, "status")
            ?: throw IOException("OpenCode v2 shell transcript did not include a status")

    private fun requireShellMessageId(message: JsonObject, messageId: String) {
        if (fields.string(message, "id") != messageId) {
            throw IOException("OpenCode v2 returned a different shell message than requested")
        }
    }

    private fun requireShellMessageSession(message: JsonObject, sessionId: String) {
        val owningSessionId = fields.string(message, "sessionID")
        if (owningSessionId != null && owningSessionId != sessionId) {
            throw IOException(
                "OpenCode v2 returned shell output from session `$owningSessionId` while loading `$sessionId`",
            )
        }
    }

    private fun requireShellMessageType(message: JsonObject) {
        if (fields.string(message, "type") != "shell") {
            throw IOException("OpenCode v2 shell request did not produce a shell transcript")
        }
    }

    private fun completedShellOutput(message: JsonObject): String {
        val outputInfo = fields.requireObject(
            fields.required(message, "output", "shell transcript"),
            "shell output",
        )
        requireNotTruncated(outputInfo)
        val output = requiredOutputText(outputInfo)
        requireSuccessfulExit(exitCode(message), output)
        return output
    }

    private fun requireNotTruncated(outputInfo: JsonObject) {
        val truncated = (outputInfo["truncated"] as? JsonPrimitive)?.booleanOrNull
            ?: throw IOException("OpenCode v2 shell transcript did not include its truncation status")
        if (truncated) throw IOException("OpenCode v2 shell output was truncated")
    }

    private fun requiredOutputText(outputInfo: JsonObject): String =
        fields.string(outputInfo, "output")
            ?: throw IOException("OpenCode v2 shell transcript did not include output text")

    private fun exitCode(message: JsonObject): Int =
        (message["exit"] as? JsonPrimitive)?.intOrNull
            ?: throw IOException("OpenCode v2 shell transcript did not include an exit code")

    private fun requireSuccessfulExit(exit: Int, output: String) {
        if (exit != 0) throw IOException("OpenCode v2 shell command exited with status $exit: $output")
    }

    private fun unsupportedPrompt(reason: String): Nothing = throw IOException(
        "send message is unavailable through the current OpenCode v2 API contract: $reason",
    )

    private companion object {
        const val SHELL_COMMAND_TIMEOUT_MS = 120_000L
        const val SHELL_POLL_INTERVAL_MS = 250L
    }
}

private class V2PromptParts {
    val text = StringBuilder()
    val files = mutableListOf<JsonObject>()
    val agents = mutableListOf<JsonObject>()
    private var attachmentsStarted = false

    fun append(part: PartInputDto) {
        when (part.type) {
            "text" -> appendText(part)
            "file" -> appendFile(part)
            "agent" -> appendAgent(part)
            else -> unsupportedPromptPart(part.type)
        }
    }

    private fun appendText(part: PartInputDto) {
        if (attachmentsStarted) {
            unsupportedPromptPart("OpenCode v2 cannot preserve text/file ordering from the legacy parts array")
        }
        if (part.synthetic == true || part.ignored == true) {
            unsupportedPromptPart("OpenCode v2 prompt cannot preserve synthetic or ignored text flags")
        }
        text.append(part.text ?: throw IOException("Text part is missing its text"))
    }

    private fun appendFile(part: PartInputDto) {
        attachmentsStarted = true
        val uri = part.url ?: throw IOException("File part is missing its URI")
        files += buildJsonObject {
            put("uri", uri)
            part.filename?.let { put("name", it) }
        }
    }

    private fun appendAgent(part: PartInputDto) {
        attachmentsStarted = true
        val name = part.name ?: throw IOException("Agent attachment is missing its name")
        agents += buildJsonObject { put("name", name) }
    }

    private fun unsupportedPromptPart(reason: String): Nothing = throw IOException(
        "send message is unavailable through the current OpenCode v2 API contract: $reason",
    )
}
