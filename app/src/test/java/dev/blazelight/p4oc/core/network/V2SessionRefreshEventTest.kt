package dev.blazelight.p4oc.core.network

import dev.blazelight.p4oc.data.remote.mapper.EventMapper
import dev.blazelight.p4oc.data.remote.mapper.MessageMapper
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V2SessionRefreshEventTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapper = V2EventMapper(json, EventMapper(json, MessageMapper()))

    @Test
    fun `agent and model selection do not trigger session catalog reads`() {
        assertNull(
            mapper.map(
                """{"type":"session.agent.selected","data":{"sessionID":"s1","agent":"build"}}""",
            ),
        )
        assertNull(
            mapper.map(
                """{"type":"session.model.selected","data":{"sessionID":"s1",
                    "model":{"providerID":"test","modelID":"test-model"}}}""",
            ),
        )
    }

    @Test
    fun `rename and revert events retain targeted refresh identity`() {
        listOf("renamed", "revert.staged", "revert.cleared", "revert.committed").forEach { suffix ->
            val mapped = mapper.map(
                """{"type":"session.$suffix","location":{"directory":"/workspace"},
                    "data":{"sessionID":"s1","title":"Renamed"}}""",
            )
            assertEquals(OpenCodeEvent.SessionRefreshRequested("s1"), mapped?.event)
            assertEquals("/workspace", mapped?.directory)
        }
    }

    @Test
    fun `delete keeps its removal flag`() {
        assertEquals(
            OpenCodeEvent.SessionRefreshRequested("s1", removed = true),
            mapper.map("""{"type":"session.deleted","data":{"sessionID":"s1"}}""")?.event,
        )
    }
}
