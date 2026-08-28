package dev.blazelight.p4oc.ui.components.toolwidgets

import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAttachmentPresentationTest {

    @Test
    fun completedToolAttachments_preservesServerFileOrder() {
        val first = filePart(id = "first", filename = "first.png")
        val second = filePart(id = "second", filename = "second.jpg")

        val result = completedToolAttachments(completed(attachments = listOf(first, second)))

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun completedToolAttachments_excludesNonFileAttachments() {
        val file = filePart(id = "image", filename = "capture.png")
        val text = Part.Text(
            id = "text",
            sessionID = "session",
            messageID = "message",
            text = "not an attachment preview",
        )

        val result = completedToolAttachments(completed(attachments = listOf(text, file)))

        assertEquals(listOf(file), result)
    }

    @Test
    fun completedToolAttachments_returnsEmptyForNonCompletedStates() {
        val input = buildJsonObject { }

        val states = listOf<ToolState>(
            ToolState.Pending(input = input, rawInput = ""),
            ToolState.Running(input = input, title = "Running", startedAt = 1L),
            ToolState.Error(input = input, error = "Failed", startedAt = 1L, endedAt = 2L),
        )

        states.forEach { state ->
            assertTrue(completedToolAttachments(state).isEmpty())
        }
    }

    private fun completed(attachments: List<Part>?): ToolState.Completed = ToolState.Completed(
        input = buildJsonObject { },
        output = "Done",
        title = "Completed",
        startedAt = 1L,
        endedAt = 2L,
        attachments = attachments,
    )

    private fun filePart(id: String, filename: String): Part.File = Part.File(
        id = id,
        sessionID = "session",
        messageID = "message",
        mime = "image/png",
        filename = filename,
        url = "data:image/png;base64,$id",
    )
}
