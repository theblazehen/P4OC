package dev.blazelight.p4oc.data.remote.mapper

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.EventDataDto
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.SessionStatus
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EventMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val messageMapper = MessageMapper(json)
    private val eventMapper = EventMapper(json, messageMapper)

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.e(any(), any<String>()) } returns Unit
        every { AppLog.e(any(), any<String>(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    // ── message.updated ─────────────────────────────────────────────────────

    @Test
    fun `maps message_updated event correctly`() {
        val properties = buildJsonObject {
            putJsonObject("info") {
                put("id", "msg-1")
                put("sessionID", "sess-1")
                putJsonObject("time") {
                    put("created", 1000L)
                    put("completed", 2000L)
                }
                put("role", "assistant")
                put("parentID", "msg-0")
                put("providerID", "anthropic")
                put("modelID", "claude-3")
                put("mode", "default")
                put("agent", "build")
                put("cost", 0.05)
                putJsonObject("tokens") {
                    put("input", 100)
                    put("output", 200)
                }
            }
        }
        val dto = EventDataDto(type = "message.updated", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.MessageUpdated)
        val msg = (event as OpenCodeEvent.MessageUpdated).message
        assertTrue(msg is Message.Assistant)
        val assistant = msg as Message.Assistant
        assertEquals("msg-1", assistant.id)
        assertEquals("sess-1", assistant.sessionID)
        assertEquals(1000L, assistant.createdAt)
        assertEquals(2000L, assistant.completedAt)
        assertEquals("msg-0", assistant.parentID)
        assertEquals("anthropic", assistant.providerID)
        assertEquals("claude-3", assistant.modelID)
        assertEquals("build", assistant.agent)
        assertEquals(0.05, assistant.cost, 0.001)
        assertEquals(100, assistant.tokens.input)
        assertEquals(200, assistant.tokens.output)
    }

    // ── message.part.updated ────────────────────────────────────────────────

    @Test
    fun `maps message_part_updated with delta`() {
        val properties = buildJsonObject {
            putJsonObject("part") {
                put("id", "part-1")
                put("sessionID", "sess-1")
                put("messageID", "msg-1")
                put("type", "text")
                put("text", "Hello world")
            }
            put("delta", " more text")
        }
        val dto = EventDataDto(type = "message.part.updated", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.MessagePartUpdated)
        val partEvent = event as OpenCodeEvent.MessagePartUpdated
        assertTrue(partEvent.part is Part.Text)
        val textPart = partEvent.part as Part.Text
        assertEquals("part-1", textPart.id)
        assertEquals("sess-1", textPart.sessionID)
        assertEquals("msg-1", textPart.messageID)
        assertEquals("Hello world", textPart.text)
        assertEquals(" more text", partEvent.delta)
    }

    // ── permission.asked ────────────────────────────────────────────────────

    @Test
    fun `maps permission_asked with tool callID`() {
        val properties = buildJsonObject {
            put("id", "perm-1")
            put("permission", "bash")
            putJsonArray("patterns") {
                add(JsonPrimitive("rm -rf /tmp/test"))
            }
            put("sessionID", "sess-1")
            putJsonObject("metadata") {
                put("key", "value")
            }
            putJsonArray("always") {
                add(JsonPrimitive("once"))
            }
            putJsonObject("tool") {
                put("messageID", "msg-42")
                put("callID", "call-99")
            }
        }
        val dto = EventDataDto(type = "permission.asked", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.PermissionRequested)
        val perm = (event as OpenCodeEvent.PermissionRequested).permission
        assertEquals("perm-1", perm.id)
        assertEquals("bash", perm.type)
        assertEquals(listOf("rm -rf /tmp/test"), perm.patterns)
        assertEquals("sess-1", perm.sessionID)
        assertEquals("msg-42", perm.messageID)
        assertEquals("call-99", perm.callID)
        assertEquals("Execute command: rm -rf /tmp/test", perm.title)
        assertEquals(listOf("once"), perm.always)
    }

    @Test
    fun `maps permission_v2_asked with source callID`() {
        val properties = buildJsonObject {
            put("id", "per_1")
            put("sessionID", "sess-1")
            put("action", "bash")
            putJsonArray("resources") {
                add(JsonPrimitive("npm test"))
            }
            putJsonArray("save") {
                add(JsonPrimitive("npm test"))
            }
            putJsonObject("metadata") {
                put("key", "value")
            }
            putJsonObject("source") {
                put("type", "tool")
                put("messageID", "msg-42")
                put("callID", "call-99")
            }
        }
        val dto = EventDataDto(type = "permission.v2.asked", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.PermissionRequested)
        val perm = (event as OpenCodeEvent.PermissionRequested).permission
        assertEquals("per_1", perm.id)
        assertEquals("bash", perm.type)
        assertEquals(listOf("npm test"), perm.patterns)
        assertEquals("sess-1", perm.sessionID)
        assertEquals("msg-42", perm.messageID)
        assertEquals("call-99", perm.callID)
        assertEquals(listOf("npm test"), perm.always)
    }

    @Test
    fun `maps permission_v2_replied`() {
        val properties = buildJsonObject {
            put("sessionID", "sess-1")
            put("requestID", "per_1")
            put("reply", "once")
        }
        val dto = EventDataDto(type = "permission.v2.replied", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.PermissionReplied)
        val reply = event as OpenCodeEvent.PermissionReplied
        assertEquals("sess-1", reply.sessionID)
        assertEquals("per_1", reply.requestID)
        assertEquals("once", reply.reply)
    }

    // ── session.status ──────────────────────────────────────────────────────

    @Test
    fun `maps session_status idle`() {
        val properties = buildJsonObject {
            put("sessionID", "sess-1")
            putJsonObject("status") {
                put("type", "idle")
            }
        }
        val dto = EventDataDto(type = "session.status", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.SessionStatusChanged)
        val statusEvent = event as OpenCodeEvent.SessionStatusChanged
        assertEquals("sess-1", statusEvent.sessionID)
        assertEquals(SessionStatus.Idle, statusEvent.status)
    }

    @Test
    fun `maps session_status busy`() {
        val properties = buildJsonObject {
            put("sessionID", "sess-2")
            putJsonObject("status") {
                put("type", "busy")
            }
        }
        val dto = EventDataDto(type = "session.status", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.SessionStatusChanged)
        val statusEvent = event as OpenCodeEvent.SessionStatusChanged
        assertEquals("sess-2", statusEvent.sessionID)
        assertEquals(SessionStatus.Busy, statusEvent.status)
    }

    @Test
    fun `maps session_status retry`() {
        val properties = buildJsonObject {
            put("sessionID", "sess-3")
            putJsonObject("status") {
                put("type", "retry")
                put("attempt", 2)
                put("message", "Rate limited")
                put("next", 1700000000L)
            }
        }
        val dto = EventDataDto(type = "session.status", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.SessionStatusChanged)
        val statusEvent = event as OpenCodeEvent.SessionStatusChanged
        assertEquals("sess-3", statusEvent.sessionID)
        assertTrue(statusEvent.status is SessionStatus.Retry)
        val retry = statusEvent.status as SessionStatus.Retry
        assertEquals(2, retry.attempt)
        assertEquals("Rate limited", retry.message)
        assertEquals(1700000000L, retry.next)
    }

    @Test
    fun `maps session_error message field without raw json`() {
        val properties = buildJsonObject {
            put("sessionID", "sess-1")
            putJsonObject("error") {
                put("name", "MessageAbortedError")
                putJsonObject("data") {
                    put("message", "Aborted")
                }
            }
        }
        val dto = EventDataDto(type = "session.error", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNotNull(event)
        assertTrue(event is OpenCodeEvent.SessionError)
        val error = (event as OpenCodeEvent.SessionError).error
        assertEquals("MessageAbortedError", error?.name)
        assertEquals("Aborted", error?.message)
    }

    // ── Unknown type ────────────────────────────────────────────────────────

    @Test
    fun `maps unknown event type returns null`() {
        val properties = buildJsonObject {
            put("foo", "bar")
        }
        val dto = EventDataDto(type = "some.unknown.event", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNull(event)
    }

    // ── Malformed data ──────────────────────────────────────────────────────

    @Test
    fun `maps malformed event data returns null without crash`() {
        val properties = buildJsonObject {
            put("not_info", "garbage")
        }
        val dto = EventDataDto(type = "message.updated", properties = properties)

        val event = eventMapper.mapToEvent(dto)

        assertNull(event)
    }
}
