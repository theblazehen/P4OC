package dev.blazelight.p4oc.ui.components.command

import dev.blazelight.p4oc.domain.model.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteTest {
    private val command = Command(name = "custom")

    @Test
    fun `refused command keeps palette open`() {
        var dismissCount = 0

        val accepted = executePaletteCommand(
            command = command,
            arguments = "arguments",
            onCommandSelected = { selectedCommand, arguments ->
                assertEquals(command, selectedCommand)
                assertEquals("arguments", arguments)
                false
            },
            onDismiss = { dismissCount += 1 },
        )

        assertFalse(accepted)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `accepted command dismisses palette once`() {
        var dismissCount = 0

        val accepted = executePaletteCommand(
            command = command,
            arguments = "arguments",
            onCommandSelected = { _, _ -> true },
            onDismiss = { dismissCount += 1 },
        )

        assertTrue(accepted)
        assertEquals(1, dismissCount)
    }
}
