package dev.blazelight.p4oc.ui.components.form

import android.content.Context
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.V2FormCondition
import dev.blazelight.p4oc.core.network.V2FormField
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2FormAnswerTest {
    private val context = mockk<Context> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
        every { getString(any(), *anyVararg()) } answers { "string:${firstArg<Int>()}" }
    }

    private val chain = listOf(
        V2FormField(key = "enable", type = "boolean"),
        V2FormField(key = "mode", type = "string", conditions = listOf(eq("enable", JsonPrimitive(true)))),
        V2FormField(
            key = "detail",
            type = "string",
            required = true,
            conditions = listOf(eq("mode", JsonPrimitive("advanced"))),
        ),
    )

    @Test
    fun `external field blocks submit until acknowledged and then answers true`() {
        val fields = listOf(V2FormField(key = "auth", type = "external", url = "https://example.com/auth"))

        val unacknowledged = validateForm(fields, emptyMap(), context)
        assertEquals("string:${R.string.v2_form_external_ack_error}", unacknowledged.answerErrors["auth"])
        assertFalse(unacknowledged.canSubmit)
        assertEquals(buildJsonObject { }, unacknowledged.resolution.answer)

        val acknowledged = validateForm(fields, mapOf("auth" to JsonPrimitive(true)), context)
        assertTrue(acknowledged.canSubmit)
        assertEquals(buildJsonObject { put("auth", true) }, acknowledged.resolution.answer)
    }

    @Test
    fun `inactive controller keeps a chained dependent inactive unrequired and unsent`() {
        val drafts = mapOf<String, JsonElement>(
            "enable" to JsonPrimitive(false),
            "mode" to JsonPrimitive("advanced"),
            "detail" to JsonPrimitive("leftover"),
        )

        val validation = validateForm(chain, drafts, context)

        assertEquals(listOf("enable"), validation.resolution.activeFields.map { it.key })
        assertTrue(validation.answerErrors.isEmpty())
        assertTrue(validation.canSubmit)
        assertEquals(buildJsonObject { put("enable", false) }, validation.resolution.answer)
    }

    @Test
    fun `reactivating a controller restores retained dependent drafts`() {
        val drafts = mutableMapOf<String, JsonElement>(
            "enable" to JsonPrimitive(true),
            "mode" to JsonPrimitive("advanced"),
            "detail" to JsonPrimitive("kept"),
        )
        val fullAnswer = buildJsonObject {
            put("enable", true)
            put("mode", "advanced")
            put("detail", "kept")
        }
        assertEquals(fullAnswer, resolveForm(chain, drafts).answer)

        drafts["enable"] = JsonPrimitive(false)
        val inactive = resolveForm(chain, drafts)
        assertEquals(listOf("enable"), inactive.renderedFields.map { it.key })
        assertEquals(buildJsonObject { put("enable", false) }, inactive.answer)

        drafts["enable"] = JsonPrimitive(true)
        val reactivated = resolveForm(chain, drafts)
        assertEquals(listOf("enable", "mode", "detail"), reactivated.renderedFields.map { it.key })
        assertEquals(fullAnswer, reactivated.answer)
    }

    @Test
    fun `hidden required field answers with its default without rendering a control`() {
        val fields = listOf(
            V2FormField(
                key = "token",
                type = "string",
                required = true,
                hidden = true,
                default = JsonPrimitive("preset"),
            ),
            V2FormField(key = "note", type = "string"),
        )
        val expected = buildJsonObject {
            put("token", "preset")
            put("note", "hi")
        }

        val seeded = validateForm(fields, initialDrafts(fields) + ("note" to JsonPrimitive("hi")), context)
        assertEquals(listOf("note"), seeded.resolution.renderedFields.map { it.key })
        assertEquals(listOf("token", "note"), seeded.resolution.activeFields.map { it.key })
        assertTrue(seeded.canSubmit)
        assertEquals(expected, seeded.resolution.answer)

        // Before the draft seeding effect runs, the hidden default must still be answered.
        assertEquals(expected, resolveForm(fields, mapOf("note" to JsonPrimitive("hi"))).answer)
    }

    @Test
    fun `hidden required field without a default blocks submit with a readable error`() {
        val fields = listOf(
            V2FormField(key = "token", type = "string", title = "Token", required = true, hidden = true),
        )

        val validation = validateForm(fields, initialDrafts(fields), context)

        assertTrue(validation.resolution.renderedFields.isEmpty())
        assertTrue(validation.answerErrors.isEmpty())
        assertEquals(listOf("string:${R.string.v2_form_hidden_field_error}"), validation.hiddenFieldErrors)
        assertFalse(validation.canSubmit)
        assertEquals(buildJsonObject { }, validation.resolution.answer)
    }

    private fun eq(key: String, value: JsonElement) = V2FormCondition(key = key, op = "eq", value = value)
}
