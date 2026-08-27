package dev.blazelight.p4oc.data.remote.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamContractDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `message decoding discards multi-megabyte object summary`() {
        val patchMarker = "summary-patch-must-not-be-retained"
        val patch = patchMarker + "x".repeat(3 * 1024 * 1024)
        val payload =
            """
            {
              "info": {
                "id": "msg-user",
                "sessionID": "ses-1",
                "time": { "created": 1 },
                "role": "user",
                "summary": {
                  "title": "large diff",
                  "diffs": [
                    {
                      "file": "src/Main.kt",
                      "patch": "$patch",
                      "additions": 1,
                      "deletions": 0
                    }
                  ]
                }
              },
              "parts": []
            }
            """.trimIndent()

        val message = json.decodeFromString<MessageWrapperDto>(payload)
        val encoded = json.encodeToString(message)

        assertEquals("user", message.info.role)
        assertFalse(encoded.contains(patchMarker))
        assertFalse(encoded.contains("\"summary\""))
        assertTrue(encoded.length < payload.length / 100)
    }

    @Test
    fun `assistant message decoding ignores boolean summary`() {
        val payload =
            """
            {
              "info": {
                "id": "msg-assistant",
                "sessionID": "ses-1",
                "time": { "created": 2, "completed": 3 },
                "role": "assistant",
                "parentID": "msg-user",
                "modelID": "model-1",
                "providerID": "provider-1",
                "agent": "build",
                "summary": true
              },
              "parts": []
            }
            """.trimIndent()

        val message = json.decodeFromString<MessageWrapperDto>(payload)
        val encoded = json.encodeToString(message)

        assertEquals("assistant", message.info.role)
        assertFalse(encoded.contains("\"summary\""))
    }

    @Test
    fun `mcp add response decodes status map`() {
        val payload =
            """
            {
              "local-tools": { "status": "connected" },
              "remote-tools": { "status": "failed", "error": "connection refused" },
              "oauth-tools": {
                "status": "needs_client_registration",
                "error": "dynamic client registration is unavailable"
              }
            }
            """.trimIndent()

        val statuses = json.decodeFromString<Map<String, McpStatusDto>>(payload)

        assertEquals("connected", statuses.getValue("local-tools").status)
        assertNull(statuses.getValue("local-tools").error)
        assertEquals("connection refused", statuses.getValue("remote-tools").error)
        assertEquals("needs_client_registration", statuses.getValue("oauth-tools").status)
    }

    @Test
    fun `mcp add request decodes upstream local config shape`() {
        val payload =
            """
            {
              "name": "local-tools",
              "config": {
                "type": "local",
                "command": ["npx", "tool-server"],
                "cwd": "/workspace",
                "environment": { "LOG_LEVEL": "info" },
                "enabled": true,
                "timeout": 5000
              }
            }
            """.trimIndent()

        val request = json.decodeFromString<AddMcpServerRequest>(payload)

        assertEquals("local-tools", request.name)
        assertEquals("local", request.config.type)
        assertEquals(listOf("npx", "tool-server"), request.config.command)
        assertEquals("/workspace", request.config.cwd)
        assertEquals(mapOf("LOG_LEVEL" to "info"), request.config.environment)
    }

    @Test
    fun `session diff decodes current upstream snapshot shape`() {
        val payload =
            """
            [
              {
                "file": "src/Main.kt",
                "patch": "@@ -1 +1 @@\n-old\n+new",
                "additions": 1,
                "deletions": 1,
                "status": "modified"
              },
              {
                "additions": 0.0,
                "deletions": 0.0
              }
            ]
            """.trimIndent()

        val diffs = json.decodeFromString<List<SnapshotFileDiffDto>>(payload)

        assertEquals("src/Main.kt", diffs.first().file)
        assertEquals("@@ -1 +1 @@\n-old\n+new", diffs.first().patch)
        assertEquals(1.0, diffs.first().additions, 0.0)
        assertEquals("modified", diffs.first().status)
        assertNull(diffs.last().file)
        assertNull(diffs.last().patch)
    }

    @Test
    fun `current upstream session preserves workspace model and accounting`() {
        val session = json.decodeFromString<SessionDto>(
            """
            {
              "id":"ses_1","slug":"brisk-fox","projectID":"project-1","workspaceID":"wrk_1",
              "directory":"/repo","path":"/repo","title":"Work","version":"1.18.3",
              "cost":1.5,"tokens":{"input":4,"output":3,"reasoning":2,"cache":{"read":1,"write":0}},
              "agent":"build","model":{"id":"model-1","providerID":"provider-1","variant":"high"},
              "metadata":{"source":"android"},"permission":[],"time":{"created":1,"updated":2}
            }
            """.trimIndent()
        )

        assertEquals("brisk-fox", session.slug)
        assertEquals("wrk_1", session.workspaceID)
        assertEquals("model-1", session.model?.id)
        assertEquals(4, session.tokens?.input)
    }

    @Test
    fun `current upstream project and command fields are retained`() {
        val project = json.decodeFromString<ProjectDto>(
            """{"id":"p1","worktree":"/repo","name":"P4OC","sandboxes":["/tmp/s1"],"time":{"created":1,"updated":2}}"""
        )
        val command = json.decodeFromString<CommandDto>(
            """{"name":"review","template":"Review this","hints":["file"],"source":"command"}"""
        )

        assertEquals(listOf("/tmp/s1"), project.sandboxes)
        assertEquals(2L, project.time.updated)
        assertEquals(listOf("file"), command.hints)
        assertEquals("command", command.source)
    }
}
