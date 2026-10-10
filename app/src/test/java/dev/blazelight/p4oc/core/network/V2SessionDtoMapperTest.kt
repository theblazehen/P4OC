package dev.blazelight.p4oc.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Payload shapes follow the OpenCode v2.0.26 schema (packages/schema/src/session*.ts, tool.ts). */
class V2SessionDtoMapperTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapper = V2SessionDtoMapper(json)

    @Test
    fun `a fork is an independent session while a parentID marks ownership`() {
        val fork = mapper.toSessionDto(session("""{"id":"ses_fork","fork":{"sessionID":"ses_origin","boundary":{}}}"""))
        val child = mapper.toSessionDto(session("""{"id":"ses_child","parentID":"ses_owner"}"""))

        assertNull(fork.parentID)
        assertEquals("ses_owner", child.parentID)
    }

    @Test
    fun `an image returned by a tool becomes an attachment instead of failing the history`() {
        val message = mapper.toMessageWrapper(
            assistant(
                """{"type":"tool","id":"call_1","name":"read","state":{"status":"completed","input":{"filePath":"probe.png"},
                "content":[{"type":"text","text":"Image read successfully"},
                {"type":"file","uri":"file:///work/probe.png","mime":"image/png","name":"probe.png"}]}}""",
            ),
            expectedSessionId = "ses_1",
        )

        val state = message.parts.single().state!!
        assertEquals("Image read successfully", state.output)
        val attachment = state.attachments!!.single()
        assertEquals("file", attachment.type)
        assertEquals("image/png", attachment.mime)
        assertEquals("probe.png", attachment.filename)
        assertEquals("file:///work/probe.png", attachment.url)
    }

    @Test
    fun `a failed tool shows the server's error when it returns no content`() {
        val message = mapper.toMessageWrapper(
            assistant(
                """{"type":"tool","id":"call_1","name":"bash","state":{"status":"error","input":{"command":"x"},
                "error":{"type":"tool.failed","message":"command not found: x"}}}""",
            ),
            expectedSessionId = "ses_1",
        )

        assertEquals("command not found: x", message.parts.single().state!!.error)
    }

    @Test
    fun `skill references and unknown content types do not block the rest of a message`() {
        val user = mapper.toMessageWrapper(
            element(
                """{"id":"msg_u","type":"user","time":{"created":1},"text":"use the skill",
                "skills":[{"skill":"skl_1","name":"release"}]}""",
            ),
            expectedSessionId = "ses_1",
        )
        val assistant = mapper.toMessageWrapper(
            assistant("""{"type":"future-widget","id":"w1"}""", """{"type":"text","text":"still here"}"""),
            expectedSessionId = "ses_1",
        )

        assertEquals(listOf("use the skill"), user.parts.map { it.text })
        assertEquals(listOf("still here"), assistant.parts.map { it.text })
    }

    private fun session(fields: String): JsonElement = element(
        fields.removeSuffix("}") +
            ""","projectID":"prj_1","location":{"directory":"/repo"},"time":{"created":1,"updated":2},"cost":0}""",
    )

    private fun assistant(vararg content: String): JsonElement = element(
        """{"id":"msg_a","type":"assistant","time":{"created":1},"content":[${content.joinToString(",")}]}""",
    )

    private fun element(text: String): JsonElement = json.parseToJsonElement(text)
}
