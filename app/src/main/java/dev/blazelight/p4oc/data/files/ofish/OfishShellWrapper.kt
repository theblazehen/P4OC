package dev.blazelight.p4oc.data.files.ofish

import java.util.Base64

internal object OfishShellWrapper {
    /**
     * Wrap shell payloads so nested quotes/newlines survive transport through the
     * opencode shell command. Base64 adds ~33% overhead, so upload chunk sizing
     * must leave room for the inflated outer command. Decode tries GNU then BSD.
     */
    fun wrap(script: String): String = "printf %s ${base64(script.trimEnd()).shellSingleQuoted()} | (base64 -d 2>/dev/null || base64 -D) | sh"

    private fun base64(script: String): String = Base64.getEncoder().encodeToString(
        script.plus('\n').toByteArray(Charsets.UTF_8),
    )
}

internal fun String.shellSingleQuoted(): String = "'" + replace("'", "'\\''") + "'"
