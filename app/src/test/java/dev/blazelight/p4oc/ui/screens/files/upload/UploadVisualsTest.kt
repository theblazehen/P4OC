package dev.blazelight.p4oc.ui.screens.files.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadVisualsTest {
    @Test fun `formatFileSize bytes`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1023 B", formatFileSize(1023))
    }

    @Test fun `formatFileSize kilobytes`() {
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("47 KB", formatFileSize(47L * 1024L))
    }

    @Test fun `formatFileSize megabytes`() {
        assertEquals("1.5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test fun `formatFileSize gigabytes`() {
        assertEquals("2.0 GB", formatFileSize(2L * 1024L * 1024L * 1024L))
    }

    @Test fun `formatFileSize negative coerced to zero`() {
        assertEquals("0 B", formatFileSize(-5))
    }

    @Test fun `getFileSymbol maps known and unknown extensions`() {
        assertEquals("▣", getFileSymbol("logo.png"))
        assertEquals("{}", getFileSymbol("Main.kt"))
        assertEquals("≡", getFileSymbol("README.md"))
        assertEquals("⚙", getFileSymbol("config.yaml"))
        assertEquals("▤", getFileSymbol("backup.zip"))
        assertEquals("◆", getFileSymbol("noext"))
        assertEquals("◆", getFileSymbol("weird.xyz"))
    }

    @Test fun `getMimeTypeLabel handles common cases`() {
        assertEquals("PNG", getMimeTypeLabel("image/png"))
        assertEquals("JSON", getMimeTypeLabel("application/json"))
        assertEquals("PLAIN", getMimeTypeLabel("text/plain; charset=utf-8"))
        assertEquals("BIN", getMimeTypeLabel(null))
        assertEquals("BIN", getMimeTypeLabel(""))
        // x- and vnd. prefixes stripped, + suffix dropped
        assertEquals("TAR", getMimeTypeLabel("application/x-tar"))
        assertEquals("MS-EXC", getMimeTypeLabel("application/vnd.ms-excel"))
        // For "+xml" suffix mimes we keep the primary token (atom) over the suffix
        assertEquals("ATOM", getMimeTypeLabel("application/atom+xml"))
    }

    @Test fun `sanitizeUploadName strips separators and control chars`() {
        assertEquals("evil_payload.bin", sanitizeUploadName("evil/payload.bin", 0L))
        assertEquals("a_b_c.txt", sanitizeUploadName("a/b\\c.txt", 0L))
        assertEquals("app-whatsapp-debug.apk", sanitizeUploadName("app-whatsapp-debug.apk.part", 0L))
        // Leading dots dropped — no hidden files via picker
        assertEquals("env", sanitizeUploadName("..env", 0L))
        // Empty falls back to timestamped placeholder
        assertEquals("upload-1234.bin", sanitizeUploadName("   ", 1234L))
        assertEquals("upload-7.bin", sanitizeUploadName(null, 7L))
    }

    @Test fun `joinDestinationPath handles empty current path`() {
        assertEquals("a.txt", joinDestinationPath("", "a.txt"))
        assertEquals("dir/a.txt", joinDestinationPath("dir", "a.txt"))
        assertEquals("dir/sub/a.txt", joinDestinationPath("/dir/sub/", "a.txt"))
    }

    @Test fun `sanitizeUploadName preserves long names within cap`() {
        val long = "n".repeat(500) + ".bin"
        val sanitized = sanitizeUploadName(long, 0L)
        assertTrue(sanitized.length <= 200)
    }
}
