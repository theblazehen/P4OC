package dev.blazelight.p4oc.data.remote.mapper

import dev.blazelight.p4oc.data.remote.dto.*
import dev.blazelight.p4oc.domain.model.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

// ============================================================================
// SessionMapper Tests
// ============================================================================

class SessionMapperTest {

    @Test
    fun `maps SessionDto with all fields`() {
        val dto = SessionDto(
            id = "sess-1",
            projectID = "proj-1",
            directory = "/home/user/project",
            parentID = "sess-0",
            title = "My Session",
            version = "1.0.0",
            time = TimeDto(created = 1000L, updated = 2000L, compacting = 3000L),
            summary = SessionSummaryDto(
                additions = 10,
                deletions = 5,
                files = 3,
                diffs = listOf(
                    FileDiffDto(
                        file = "main.kt",
                        before = "old code",
                        after = "new code",
                        additions = 7,
                        deletions = 3
                    )
                )
            ),
            share = SessionShareDto(url = "https://share.example.com/sess-1"),
            revert = SessionRevertDto(
                messageID = "msg-5",
                partID = "part-2",
                snapshot = "snap-1",
                diff = "--- a\n+++ b"
            )
        )

        val session = SessionMapper.mapToDomain(dto)

        assertEquals("sess-1", session.id)
        assertEquals("proj-1", session.projectID)
        assertEquals("/home/user/project", session.directory)
        assertEquals("sess-0", session.parentID)
        assertEquals("My Session", session.title)
        assertEquals("1.0.0", session.version)
        assertEquals(1000L, session.createdAt)
        assertEquals(2000L, session.updatedAt)
        assertEquals(3000L, session.compactingAt)

        assertNotNull(session.summary)
        assertEquals(10, session.summary!!.additions)
        assertEquals(5, session.summary!!.deletions)
        assertEquals(3, session.summary!!.files)
        assertEquals(1, session.summary!!.diffs!!.size)
        assertEquals("main.kt", session.summary!!.diffs!![0].file)
        assertEquals("old code", session.summary!!.diffs!![0].before)
        assertEquals("new code", session.summary!!.diffs!![0].after)
        assertEquals(7, session.summary!!.diffs!![0].additions)
        assertEquals(3, session.summary!!.diffs!![0].deletions)

        assertEquals("https://share.example.com/sess-1", session.shareUrl)

        assertNotNull(session.revert)
        assertEquals("msg-5", session.revert!!.messageID)
        assertEquals("part-2", session.revert!!.partID)
        assertEquals("snap-1", session.revert!!.snapshot)
        assertEquals("--- a\n+++ b", session.revert!!.diff)
    }

    @Test
    fun `maps SessionDto with null optional fields`() {
        val dto = SessionDto(
            id = "sess-2",
            projectID = "proj-2",
            directory = "/tmp",
            parentID = null,
            title = "Minimal Session",
            version = "2.0.0",
            time = TimeDto(created = 5000L, updated = null, compacting = null),
            summary = null,
            share = null,
            revert = null
        )

        val session = SessionMapper.mapToDomain(dto)

        assertEquals("sess-2", session.id)
        assertNull(session.parentID)
        assertEquals(5000L, session.createdAt)
        // updatedAt should fallback to created when updated is null
        assertEquals(5000L, session.updatedAt)
        assertNull(session.compactingAt)
        assertNull(session.summary)
        assertNull(session.shareUrl)
        assertNull(session.revert)
    }

    @Test
    fun `mapStatusToDomain maps idle busy retry correctly`() {
        val idle = SessionMapper.mapStatusToDomain(SessionStatusDto(type = "idle"))
        assertEquals(SessionStatus.Idle, idle)

        val busy = SessionMapper.mapStatusToDomain(SessionStatusDto(type = "busy"))
        assertEquals(SessionStatus.Busy, busy)

        val retry = SessionMapper.mapStatusToDomain(
            SessionStatusDto(type = "retry", attempt = 3, message = "Too many requests", next = 9999L)
        )
        assertTrue(retry is SessionStatus.Retry)
        val retryStatus = retry as SessionStatus.Retry
        assertEquals(3, retryStatus.attempt)
        assertEquals("Too many requests", retryStatus.message)
        assertEquals(9999L, retryStatus.next)
    }

    @Test
    fun `mapStatusToDomain returns Idle for unknown type`() {
        val unknown = SessionMapper.mapStatusToDomain(SessionStatusDto(type = "some-new-status"))
        assertEquals(SessionStatus.Idle, unknown)
    }
}

// ============================================================================
// PartMapper Tests
// ============================================================================

class PartMapperTest {

    @Test
    fun `maps text part correctly`() {
        val dto = PartDto(
            id = "part-1",
            sessionID = "sess-1",
            messageID = "msg-1",
            type = "text",
            text = "Hello, world!",
            synthetic = true,
            ignored = false,
            time = PartTimeDto(start = 100L, end = 200L)
        )

        val part = PartMapper.mapToDomain(dto)

        assertTrue(part is Part.Text)
        val textPart = part as Part.Text
        assertEquals("part-1", textPart.id)
        assertEquals("sess-1", textPart.sessionID)
        assertEquals("msg-1", textPart.messageID)
        assertEquals("Hello, world!", textPart.text)
        assertFalse(textPart.isStreaming)
        assertTrue(textPart.synthetic)
        assertFalse(textPart.ignored)
        assertNotNull(textPart.time)
        assertEquals(100L, textPart.time!!.start)
        assertEquals(200L, textPart.time!!.end)
    }

    @Test
    fun `maps tool part with running state`() {
        val inputJson = buildJsonObject { put("command", "ls -la") }
        val metadataJson = buildJsonObject { put("cwd", "/home") }

        val dto = PartDto(
            id = "part-2",
            sessionID = "sess-1",
            messageID = "msg-1",
            type = "tool",
            callID = "call-1",
            toolName = "bash",
            state = ToolStateDto(
                status = "running",
                input = inputJson,
                title = "Running bash",
                time = PartTimeDto(start = 500L),
                metadata = metadataJson
            )
        )

        val part = PartMapper.mapToDomain(dto)

        assertTrue(part is Part.Tool)
        val toolPart = part as Part.Tool
        assertEquals("part-2", toolPart.id)
        assertEquals("call-1", toolPart.callID)
        assertEquals("bash", toolPart.toolName)
        assertTrue(toolPart.state is ToolState.Running)
        val state = toolPart.state as ToolState.Running
        assertEquals(inputJson, state.input)
        assertEquals("Running bash", state.title)
        assertEquals(500L, state.startedAt)
        assertEquals(metadataJson, state.metadata)
    }

    @Test
    fun `maps file part`() {
        val dto = PartDto(
            id = "part-3",
            sessionID = "sess-1",
            messageID = "msg-1",
            type = "file",
            mime = "image/png",
            filename = "screenshot.png",
            url = "https://example.com/screenshot.png"
        )

        val part = PartMapper.mapToDomain(dto)

        assertTrue(part is Part.File)
        val filePart = part as Part.File
        assertEquals("part-3", filePart.id)
        assertEquals("image/png", filePart.mime)
        assertEquals("screenshot.png", filePart.filename)
        assertEquals("https://example.com/screenshot.png", filePart.url)
    }

    @Test
    fun `maps file source strings without json escapes`() {
        val dto = PartDto(
            id = "part-source",
            sessionID = "sess-1",
            messageID = "msg-1",
            type = "file",
            mime = "text/plain",
            filename = "quoted.txt",
            url = "file://quoted.txt",
            source = buildJsonObject {
                put("type", "file")
                put("path", "src/\"quoted\".txt")
                put(
                    "text",
                    buildJsonObject {
                        put("value", "hello \"world\"\nnext")
                        put("start", 1)
                        put("end", 2)
                    }
                )
            }
        )

        val part = PartMapper.mapToDomain(dto)

        assertTrue(part is Part.File)
        val source = (part as Part.File).source
        assertTrue(source is FilePartSource.FileSource)
        val fileSource = source as FilePartSource.FileSource
        assertEquals("src/\"quoted\".txt", fileSource.path)
        assertEquals("hello \"world\"\nnext", fileSource.text.value)
    }

    @Test
    fun `maps patch part`() {
        val dto = PartDto(
            id = "part-4",
            sessionID = "sess-1",
            messageID = "msg-1",
            type = "patch",
            hash = "abc123",
            files = listOf("file1.kt", "file2.kt")
        )

        val part = PartMapper.mapToDomain(dto)

        assertTrue(part is Part.Patch)
        val patchPart = part as Part.Patch
        assertEquals("part-4", patchPart.id)
        assertEquals("abc123", patchPart.hash)
        assertEquals(listOf("file1.kt", "file2.kt"), patchPart.files)
    }

    @Test
    fun `unknown type falls back to Text`() {
        val dto = PartDto(
            id = "part-5",
            sessionID = "sess-1",
            messageID = "msg-1",
            type = "some_future_type",
            text = "fallback content"
        )

        val part = PartMapper.mapToDomain(dto)

        assertTrue(part is Part.Text)
        val textPart = part as Part.Text
        assertEquals("part-5", textPart.id)
        assertEquals("fallback content", textPart.text)
        assertFalse(textPart.isStreaming)
    }
}

// ============================================================================
// MessageMapper Tests
// ============================================================================

class MessageMapperTest {

    private val messageMapper = MessageMapper()

    @Test
    fun `maps user message with model ref`() {
        val dto = MessageInfoDto(
            id = "msg-1",
            sessionID = "sess-1",
            time = MessageTimeDto(created = 1000L),
            role = "user",
            agent = "build",
            model = ModelRefDto(providerID = "anthropic", modelID = "claude-3-opus"),
            system = "You are a helpful assistant"
        )

        val message = messageMapper.mapToDomain(dto)

        assertTrue(message is Message.User)
        val user = message as Message.User
        assertEquals("msg-1", user.id)
        assertEquals("sess-1", user.sessionID)
        assertEquals(1000L, user.createdAt)
        assertEquals("build", user.agent)
        assertEquals("anthropic", user.model.providerID)
        assertEquals("claude-3-opus", user.model.modelID)
        assertEquals("You are a helpful assistant", user.system)
    }

    @Test
    fun `maps assistant message with tokens`() {
        val dto = MessageInfoDto(
            id = "msg-2",
            sessionID = "sess-1",
            time = MessageTimeDto(created = 1000L, completed = 2000L),
            role = "assistant",
            parentID = "msg-1",
            providerID = "anthropic",
            modelID = "claude-3-opus",
            mode = "default",
            agent = "build",
            cost = 0.123,
            tokens = TokenUsageDto(
                input = 500,
                output = 300,
                reasoning = 50,
                cache = TokenCacheDto(read = 10, write = 20)
            ),
            path = MessagePathDto(cwd = "/home/user", root = "/home"),
            finish = "end_turn"
        )

        val message = messageMapper.mapToDomain(dto)

        assertTrue(message is Message.Assistant)
        val assistant = message as Message.Assistant
        assertEquals("msg-2", assistant.id)
        assertEquals("sess-1", assistant.sessionID)
        assertEquals(1000L, assistant.createdAt)
        assertEquals(2000L, assistant.completedAt)
        assertEquals("msg-1", assistant.parentID)
        assertEquals("anthropic", assistant.providerID)
        assertEquals("claude-3-opus", assistant.modelID)
        assertEquals("default", assistant.mode)
        assertEquals("build", assistant.agent)
        assertEquals(0.123, assistant.cost, 0.0001)
        assertEquals(500, assistant.tokens.input)
        assertEquals(300, assistant.tokens.output)
        assertEquals(50, assistant.tokens.reasoning)
        assertEquals(10, assistant.tokens.cacheRead)
        assertEquals(20, assistant.tokens.cacheWrite)
        assertNotNull(assistant.path)
        assertEquals("/home/user", assistant.path!!.cwd)
        assertEquals("/home", assistant.path!!.root)
        assertEquals("end_turn", assistant.finish)
    }

    @Test
    fun `maps api error primitive strings without json escapes`() {
        val dto = MessageErrorDto(
            name = "APIError",
            data = buildJsonObject {
                put("message", "Bad \"request\"\nRetry")
                put("statusCode", 429)
                put("isRetryable", true)
                put("responseBody", "{\"error\":\"slow down\"}")
            }
        )

        val error = messageMapper.mapApiErrorToDomain(dto)

        assertNotNull(error)
        assertEquals("Bad \"request\"\nRetry", error!!.message)
        assertEquals(429, error.statusCode)
        assertTrue(error.isRetryable)
        assertEquals("{\"error\":\"slow down\"}", error.responseBody)
    }

    @Test
    fun `maps wrapper with parts`() {
        val wrapperDto = MessageWrapperDto(
            info = MessageInfoDto(
                id = "msg-3",
                sessionID = "sess-1",
                time = MessageTimeDto(created = 3000L),
                role = "assistant",
                parentID = "msg-2"
            ),
            parts = listOf(
                PartDto(
                    id = "part-a",
                    sessionID = "sess-1",
                    messageID = "msg-3",
                    type = "text",
                    text = "Part one"
                ),
                PartDto(
                    id = "part-b",
                    sessionID = "sess-1",
                    messageID = "msg-3",
                    type = "text",
                    text = "Part two"
                )
            )
        )

        val result = messageMapper.mapWrapperToDomain(wrapperDto)

        assertTrue(result.message is Message.Assistant)
        assertEquals("msg-3", result.message.id)
        assertEquals(2, result.parts.size)
        assertTrue(result.parts[0] is Part.Text)
        assertEquals("Part one", (result.parts[0] as Part.Text).text)
        assertTrue(result.parts[1] is Part.Text)
        assertEquals("Part two", (result.parts[1] as Part.Text).text)
    }
}
