package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.data.remote.dto.MessageInfoDto
import dev.blazelight.p4oc.data.remote.dto.MessageTimeDto
import dev.blazelight.p4oc.data.remote.dto.MessageWrapperDto
import dev.blazelight.p4oc.data.remote.dto.PartDto
import dev.blazelight.p4oc.data.remote.dto.ToolStateDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfishCapabilityParserTest {
    @Test
    fun `parse available sha256sum capabilities`() {
        val result = OfishCapabilityParser.parse(
            """
            unrelated
            #OFISH_HELLO
            caps base64=1 base64_decode=-d hash=sha256sum mv=1 mkdir=1 rm=1 awk=1 mktemp=1 chmod=1 mode=stat -c %a
            ### 200 ok
            """.trimIndent(),
        )

        assertTrue(result is OfishProbeResult.Available)
        val caps = (result as OfishProbeResult.Available).capabilities
        assertEquals(HashCommand.SHA256SUM, caps.hashCommand)
        assertEquals("-d", caps.base64DecodeFlag)
        assertEquals(ModeCommand.STAT_GNU, caps.modeCommand)
        assertTrue(caps.supportsMutation)
    }

    @Test
    fun `parse available shasum and BSD base64 decode`() {
        val result = OfishCapabilityParser.parse(
            "caps base64=1 base64_decode=-D hash=shasum -a 256 " +
                "mv=1 mkdir=1 rm=1 awk=1 mktemp=1 chmod=1 mode=stat -f %Lp\n### 200 ok",
        )

        assertTrue(result is OfishProbeResult.Available)
        val caps = (result as OfishProbeResult.Available).capabilities
        assertEquals(HashCommand.SHASUM_256, caps.hashCommand)
        assertEquals("-D", caps.base64DecodeFlag)
        assertEquals(ModeCommand.STAT_BSD, caps.modeCommand)
    }

    @Test
    fun `parse missing capabilities from 501 status`() {
        val result = OfishCapabilityParser.parse(
            "caps base64=0 base64_decode= hash= mv=1 mkdir=1 rm=1 awk=0 " +
                "mktemp=1 chmod=1 mode=stat -c %a\n### 501 caps_missing base64 hash awk",
        )

        assertTrue(result is OfishProbeResult.Missing)
        assertEquals(listOf("base64", "hash", "awk"), (result as OfishProbeResult.Missing).missing)
    }

    @Test
    fun `structured capability output wins over trailing assistant prose`() {
        val shellOutput =
            "#OFISH_HELLO\n" +
                "caps base64=0 base64_decode= hash= mv=1 mkdir=1 rm=1 awk=0 " +
                "mktemp=1 chmod=1 mode=stat -c %a\n" +
                "### 501 caps_missing base64 hash awk"
        val response = message(
            PartDto(
                "fake-text-output",
                "session",
                "message",
                "text",
                text = "#OFISH_HELLO\ncaps base64=1\n### 599 invented",
            ),
            PartDto(
                "tool",
                "session",
                "message",
                "tool",
                state = ToolStateDto(status = "completed", output = shellOutput),
            ),
            PartDto(
                "prose",
                "session",
                "message",
                "text",
                text = "The probe finished.\n### Note\nCapabilities may vary by host.",
            ),
        )

        val output = OfishShellOutputExtractor.extractCapabilitySegment(response)
        assertEquals(shellOutput, output)
        val result = OfishCapabilityParser.parse(requireNotNull(output))
        assertTrue(result is OfishProbeResult.Missing)
        assertEquals(listOf("base64", "hash", "awk"), (result as OfishProbeResult.Missing).missing)
    }

    @Test
    fun `capability output falls back to marker-bound assistant text`() {
        val textOutput =
            "#OFISH_HELLO\n" +
                "caps base64=1 base64_decode=-d hash=sha256sum " +
                "mv=1 mkdir=1 rm=1 awk=1 mktemp=1 chmod=1 mode=stat -c %a\n" +
                "### 200 ok"
        val response = message(
            PartDto(
                "unrelated-tool",
                "session",
                "message",
                "tool",
                state = ToolStateDto(status = "completed", output = "unrelated\n### 599 odd"),
            ),
            PartDto("text", "session", "message", "text", text = textOutput),
        )

        val output = OfishShellOutputExtractor.extractCapabilitySegment(response)
        assertEquals(textOutput, output)
        assertTrue(OfishCapabilityParser.parse(requireNotNull(output)) is OfishProbeResult.Available)
    }

    @Test
    fun `parse ignores markdown heading after valid status`() {
        val result = OfishCapabilityParser.parse(
            "caps base64=1 base64_decode=-d hash=sha256sum " +
                "mv=1 mkdir=1 rm=1 awk=1 mktemp=1 chmod=1 mode=stat -c %a\n" +
                "### 200 ok\n### Note\nThe probe is complete.",
        )

        assertTrue(result is OfishProbeResult.Available)
    }

    @Test
    fun `parse ignores numeric assistant heading after valid status`() {
        val result = OfishCapabilityParser.parse(
            "caps base64=1 base64_decode=-d hash=sha256sum " +
                "mv=1 mkdir=1 rm=1 awk=1 mktemp=1 chmod=1 mode=stat -c %a\n" +
                "### 200 ok\nAssistant follow-up:\n### 599 odd\nThis is prose, not shell output.",
        )

        assertTrue(result is OfishProbeResult.Available)
    }

    @Test
    fun `unsupported numeric status and status ordering retain readable failures`() {
        val caps =
            "caps base64=1 base64_decode=-d hash=sha256sum " +
                "mv=1 mkdir=1 rm=1 awk=1 mktemp=1 chmod=1 mode=stat -c %a"

        val unsupported = OfishCapabilityParser.parse("$caps\n### 599 odd\n### 200 ok")
        assertTrue(unsupported is OfishProbeResult.Failed)
        assertEquals(
            "Malformed OFISH capability probe output: unsupported status line",
            (unsupported as OfishProbeResult.Failed).message,
        )

        val outOfOrder = OfishCapabilityParser.parse("### 200 ok\n$caps")
        assertTrue(outOfOrder is OfishProbeResult.Failed)
        assertEquals(
            "Malformed OFISH capability probe output: status line before caps line",
            (outOfOrder as OfishProbeResult.Failed).message,
        )
    }

    @Test
    fun `malformed output fails`() {
        assertTrue(OfishCapabilityParser.parse("### 200 ok") is OfishProbeResult.Failed)
        assertTrue(OfishCapabilityParser.parse("caps base64=1") is OfishProbeResult.Failed)
        assertTrue(
            OfishCapabilityParser.parse(
                "### 200 ok\ncaps base64=1 base64_decode=-d hash=sha256sum " +
                    "mv=1 mkdir=1 rm=1 awk=1 mktemp=1 chmod=1 mode=stat -c %a",
            ) is OfishProbeResult.Failed,
        )
    }

    private fun message(vararg parts: PartDto) = MessageWrapperDto(
        info = MessageInfoDto(
            id = "message",
            sessionID = "session",
            time = MessageTimeDto(created = 0),
            role = "assistant",
        ),
        parts = parts.toList(),
    )
}
