package dev.blazelight.p4oc.data.files.ofish

import java.util.Base64

internal class OfishCommandBuilder {
    /**
     * Builds a small ephemeral-session command that hashes a path on disk
     * using the same `hash_file` shell helper used by the mutation commands,
     * so the digest is byte-for-byte identical to what `write`/`uploadFinish`
     * would compare against. Emits `### 200 ok hash=<hex>` on success or
     * `### 404 missing` if the file does not exist.
     */
    fun hash(path: String, capabilities: OfishCapabilities): String = wrap(buildString {
        appendLine("printf '#OFISH_HASH\\n'")
        appendLine("P=${shellSingleQuote(path)}")
        appendHashFunction(requireHashCommand(capabilities))
        append(
            """
            if [ ! -f "${'$'}P" ]; then printf '### 404 missing\n'; exit 0; fi
            HASH=${'$'}(hash_file "${'$'}P")
            printf '### 200 ok hash=%s\n' "${'$'}HASH"
            exit 0
            """.trimIndent()
        )
    })

    fun write(
        path: String,
        content: String,
        expectedHash: String?,
        capabilities: OfishCapabilities,
    ): String = wrap(buildString {
        val parent = parentDirectory(path)
        val payload = base64(content.toByteArray(Charsets.UTF_8))
        val delimiter = PAYLOAD_DELIMITER
        appendCommonHeader(marker = "#OFISH_WRITE", path = path, parent = parent, expectedHash = expectedHash)
        appendHashFunction(requireHashCommand(capabilities))
        appendExpectedHashGuard()
        append(
            """
            mkdir -p -- "${'$'}D" || { printf '### 500 failed reason=mkdir\n'; exit 0; }
            EXISTED=0
            [ -e "${'$'}P" ] && EXISTED=1
            TMP=${'$'}(mktemp "${'$'}D/.ofish.XXXXXX") || { printf '### 500 failed reason=mktemp\n'; exit 0; }
            cleanup() { rm -f -- "${'$'}TMP" >/dev/null 2>&1 || true; }
            trap cleanup EXIT INT TERM
            base64 ${base64DecodeFlag(capabilities)} > "${'$'}TMP" <<'$delimiter'
            """.trimIndent()
        )
        append('\n')
        append(payload)
        append('\n')
        append(delimiter)
        append('\n')
        append(
            """
            if [ ${'$'}? -ne 0 ]; then printf '### 500 failed reason=decode\n'; exit 0; fi
            mv -f -- "${'$'}TMP" "${'$'}P" || { printf '### 500 failed reason=mv\n'; exit 0; }
            trap - EXIT INT TERM
            HASH=${'$'}(hash_file "${'$'}P")
            if [ "${'$'}EXISTED" = 1 ]; then
              printf '### 200 ok hash=%s\n' "${'$'}HASH"
            else
              printf '### 201 created hash=%s\n' "${'$'}HASH"
            fi
            exit 0
            """.trimIndent()
        )
    })

    fun delete(path: String): String = wrap(buildString {
        appendLine("printf '#OFISH_DELETE\\n'")
        appendLine("P=${shellSingleQuote(path)}")
        append(
            """
            if [ ! -e "${'$'}P" ]; then printf '### 404 missing\n'; exit 0; fi
            if [ -d "${'$'}P" ]; then printf '### 412 precondition reason=directory\n'; exit 0; fi
            rm -f -- "${'$'}P" || { printf '### 500 failed reason=rm\n'; exit 0; }
            printf '### 204 deleted\n'
            exit 0
            """.trimIndent()
        )
    })

    fun uploadInit(
        path: String,
        expectedHash: String?,
        capabilities: OfishCapabilities,
    ): String = wrap(buildString {
        val parent = parentDirectory(path)
        appendCommonHeader(marker = "#OFISH_UPLOAD_INIT", path = path, parent = parent, expectedHash = expectedHash)
        appendHashFunction(requireHashCommand(capabilities))
        appendExpectedHashGuard()
        append(
            """
            mkdir -p -- "${'$'}D" || { printf '### 500 failed reason=mkdir\n'; exit 0; }
            TMP=${'$'}(mktemp "${'$'}D/.ofish.upload.XXXXXX") || { printf '### 500 failed reason=mktemp\n'; exit 0; }
            printf '### 200 ok upload=%s\n' "${'$'}TMP"
            exit 0
            """.trimIndent()
        )
    })

    fun uploadChunk(
        uploadToken: String,
        bytes: ByteArray,
        capabilities: OfishCapabilities,
    ): String = wrap(buildString {
        val payload = base64(bytes)
        val delimiter = PAYLOAD_DELIMITER
        appendLine("printf '#OFISH_UPLOAD_CHUNK\\n'")
        appendLine("TMP=${shellSingleQuote(uploadToken)}")
        append(
            """
            if [ ! -f "${'$'}TMP" ]; then printf '### 412 precondition reason=missing_tmp\n'; exit 0; fi
            base64 ${base64DecodeFlag(capabilities)} >> "${'$'}TMP" <<'$delimiter'
            """.trimIndent()
        )
        append('\n')
        append(payload)
        append('\n')
        append(delimiter)
        append('\n')
        append(
            """
            if [ ${'$'}? -ne 0 ]; then printf '### 500 failed reason=decode\n'; exit 0; fi
            printf '### 200 ok\n'
            exit 0
            """.trimIndent()
        )
    })

    fun uploadFinish(
        path: String,
        uploadToken: String,
        expectedHash: String?,
        capabilities: OfishCapabilities,
    ): String = wrap(buildString {
        appendCommonHeader(marker = "#OFISH_UPLOAD_FINISH", path = path, parent = parentDirectory(path), expectedHash = expectedHash)
        appendLine("TMP=${shellSingleQuote(uploadToken)}")
        appendHashFunction(requireHashCommand(capabilities))
        appendExpectedHashGuard()
        append(
            """
            if [ ! -f "${'$'}TMP" ]; then printf '### 412 precondition reason=missing_tmp\n'; exit 0; fi
            mv -f -- "${'$'}TMP" "${'$'}P" || { printf '### 500 failed reason=mv\n'; exit 0; }
            HASH=${'$'}(hash_file "${'$'}P")
            printf '### 200 ok hash=%s\n' "${'$'}HASH"
            exit 0
            """.trimIndent()
        )
    })

    fun uploadAbort(uploadToken: String): String = wrap(buildString {
        appendLine("printf '#OFISH_UPLOAD_ABORT\\n'")
        appendLine("TMP=${shellSingleQuote(uploadToken)}")
        append(
            """
            rm -f -- "${'$'}TMP" >/dev/null 2>&1 || true
            printf '### 204 deleted\n'
            exit 0
            """.trimIndent()
        )
    })

    private fun wrap(script: String): String = OfishShellWrapper.wrap(script)

    private fun StringBuilder.appendCommonHeader(
        marker: String,
        path: String,
        parent: String,
        expectedHash: String?,
    ) {
        appendLine("printf '${marker}\\n'")
        appendLine("P=${shellSingleQuote(path)}")
        appendLine("D=${shellSingleQuote(parent)}")
        appendLine("EXPECTED=${shellSingleQuote(expectedHash.orEmpty())}")
    }

    private fun StringBuilder.appendHashFunction(hashCommand: HashCommand) {
        val command = when (hashCommand) {
            HashCommand.SHA256SUM -> "sha256sum"
            HashCommand.SHASUM_256 -> "shasum -a 256"
            HashCommand.MD5SUM -> "md5sum"
        }
        append(
            """
            hash_file() {
              if [ -f "${'$'}1" ]; then
                $command "${'$'}1" 2>/dev/null | awk '{print ${'$'}1}'
              else
                printf ''
              fi
            }
            """.trimIndent()
        )
        append('\n')
    }

    private fun StringBuilder.appendExpectedHashGuard() {
        append(
            """
            if [ -n "${'$'}EXPECTED" ]; then
              if [ ! -f "${'$'}P" ]; then printf '### 404 missing\n'; exit 0; fi
              ACTUAL=${'$'}(hash_file "${'$'}P")
              if [ "${'$'}ACTUAL" != "${'$'}EXPECTED" ]; then
                printf '### 409 conflict actual=%s\n' "${'$'}ACTUAL"
                exit 0
              fi
            fi
            """.trimIndent()
        )
        append('\n')
    }

    private fun requireHashCommand(capabilities: OfishCapabilities): HashCommand =
        requireNotNull(capabilities.hashCommand) { "OFISH mutation command requires a hash command" }

    private fun base64DecodeFlag(capabilities: OfishCapabilities): String = when (val flag = capabilities.base64DecodeFlag) {
        "-d", "-D", "--decode" -> flag
        else -> throw IllegalArgumentException("Unsupported base64 decode flag: $flag")
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    internal companion object {
        const val PAYLOAD_DELIMITER = "__OFISH_PAYLOAD__"
        fun shellSingleQuote(value: String): String = value.shellSingleQuoted()

        fun parentDirectory(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = ".").ifBlank { "." }
    }
}
