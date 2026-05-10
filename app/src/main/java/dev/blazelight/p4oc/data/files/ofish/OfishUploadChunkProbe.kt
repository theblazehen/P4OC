package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64

internal open class CachedOfishUploadChunkBytes(
    private val probe: OfishUploadChunkProbe,
    private val defaultBytes: Int = OFISH_DEFAULT_CHUNK_BYTES,
    private val minBytes: Int = OFISH_MIN_CHUNK_BYTES,
) : UploadChunkBytesProvider {
    init {
        require(defaultBytes > 0) { "defaultBytes must be greater than zero" }
        require(minBytes > 0) { "minBytes must be greater than zero" }
        require(defaultBytes >= minBytes) { "defaultBytes must be greater than or equal to minBytes" }
    }

    private val mutex = Mutex()
    private var cached: Int? = null

    override suspend fun get(capabilities: OfishCapabilities): Int {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            probe.findSupportedChunkBytes(
                capabilities = capabilities,
                startBytes = defaultBytes,
                minBytes = minBytes,
            ).also { cached = it }
        }
    }
}

internal class OfishUploadChunkProbe(
    private val client: OfishWorkspaceClient,
    private val sessionFactory: OfishSessionFactory,
    private val commandBuilder: OfishUploadChunkProbeCommandBuilder = OfishUploadChunkProbeCommandBuilder(),
    private val shellAgent: String = DEFAULT_SHELL_AGENT,
) {
    suspend fun findSupportedChunkBytes(
        capabilities: OfishCapabilities,
        startBytes: Int,
        minBytes: Int,
    ): Int {
        require(startBytes > 0) { "startBytes must be greater than zero" }
        require(minBytes > 0) { "minBytes must be greater than zero" }
        require(startBytes >= minBytes) { "startBytes must be greater than or equal to minBytes" }

        if (probe(startBytes, capabilities)) return startBytes
        if (startBytes != minBytes && probe(minBytes, capabilities)) return minBytes

        throw OfishUploadChunkProbeUnavailableException(
            "OFISH upload chunk probe failed for ${startBytes}B and ${minBytes}B",
        )
    }

    private suspend fun probe(bytes: Int, capabilities: OfishCapabilities): Boolean = runCatching {
        sessionFactory.withSession(OPERATION_NAME) { session ->
            val response = client.executeShellCommand(
                sessionId = session.id,
                request = ShellCommandRequest(
                    agent = shellAgent,
                    model = null,
                    command = commandBuilder.probe(bytes, capabilities),
                ),
            )
            OfishMutationParser.parse(OfishShellOutputExtractor.extract(response)) is OfishMutationStatus.Ok
        }
    }.getOrElse { error ->
        AppLog.w(TAG, "OFISH upload chunk probe failed for ${bytes}B: ${error.message}")
        false
    }

    private companion object {
        const val TAG = "OfishUploadChunkProbe"
        const val OPERATION_NAME = "chunk-probe"
        const val DEFAULT_SHELL_AGENT = "build"
    }
}

internal class OfishUploadChunkProbeUnavailableException(message: String) : Exception(message)

internal class OfishUploadChunkProbeCommandBuilder(
    private val payloadBytes: (Int) -> ByteArray = { size -> deterministicPayload(size) },
) {
    fun probe(bytes: Int, capabilities: OfishCapabilities): String {
        require(bytes > 0) { "bytes must be greater than zero" }
        val bytesPayload = payloadBytes(bytes)
        val payload = Base64.getEncoder().encodeToString(bytesPayload)
        val expectedHash = bytesPayload.digestHex(capabilities.hashCommand)
        val delimiter = OfishCommandBuilder.PAYLOAD_DELIMITER
        val decodeFlag = base64DecodeFlag(capabilities)
        val hashCommand = hashCommand(capabilities)
        val script = buildString {
            appendLine("printf '#OFISH_UPLOAD_CHUNK_PROBE\\n'")
            append(
                """
                hash_file() {
                  if [ -f "${'$'}1" ]; then
                    $hashCommand "${'$'}1" 2>/dev/null | awk '{print ${'$'}1}'
                  else
                    printf ''
                  fi
                }
                TMP=${'$'}(mktemp ".ofish.chunk-probe.XXXXXX") || { printf '### 500 failed reason=mktemp\n'; exit 0; }
                cleanup() { rm -f -- "${'$'}TMP" >/dev/null 2>&1 || true; }
                trap cleanup EXIT INT TERM
                base64 $decodeFlag > "${'$'}TMP" <<'$delimiter'
                """.trimIndent(),
            )
            append('\n')
            append(payload)
            append('\n')
            append(delimiter)
            append('\n')
            append(
                """
                if [ ${'$'}? -ne 0 ]; then printf '### 500 failed reason=decode\n'; exit 0; fi
                HASH=${'$'}(hash_file "${'$'}TMP")
                if [ "${'$'}HASH" != '$expectedHash' ]; then
                  printf '### 500 failed reason=hash actual=%s\n' "${'$'}HASH"
                  exit 0
                fi
                rm -f -- "${'$'}TMP" >/dev/null 2>&1 || { printf '### 500 failed reason=rm\n'; exit 0; }
                trap - EXIT INT TERM
                printf '### 200 ok bytes=$bytes hash=%s\n' "${'$'}HASH"
                exit 0
                """.trimIndent(),
            )
        }
        return OfishShellWrapper.wrap(script)
    }

    private fun base64DecodeFlag(capabilities: OfishCapabilities): String = when (val flag = capabilities.base64DecodeFlag) {
        "-d", "-D", "--decode" -> flag
        else -> throw IllegalArgumentException("Unsupported base64 decode flag: $flag")
    }

    private fun hashCommand(capabilities: OfishCapabilities): String = when (requireNotNull(capabilities.hashCommand) { "OFISH chunk probe requires a hash command" }) {
        HashCommand.SHA256SUM -> "sha256sum"
        HashCommand.SHASUM_256 -> "shasum -a 256"
        HashCommand.MD5SUM -> "md5sum"
    }

    private companion object {
        fun deterministicPayload(size: Int): ByteArray = ByteArray(size) { index -> ((index * 31) and 0xff).toByte() }
    }
}

private fun ByteArray.digestHex(hashCommand: HashCommand?): String {
    val algorithm = when (requireNotNull(hashCommand) { "OFISH chunk probe requires a hash command" }) {
        HashCommand.SHA256SUM,
        HashCommand.SHASUM_256 -> "SHA-256"
        HashCommand.MD5SUM -> "MD5"
    }
    return java.security.MessageDigest.getInstance(algorithm)
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
