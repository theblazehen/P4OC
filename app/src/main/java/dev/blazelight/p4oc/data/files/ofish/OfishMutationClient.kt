package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FilePathValidator
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileUploadResult
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.files.FileWriteResult
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream

internal const val MAX_UPLOAD_SOURCE_BYTES = 1L * 1024 * 1024 * 1024
internal const val UPLOAD_TOO_LARGE_MESSAGE = "File is too large to upload (maximum 1 GiB)"

internal fun interface UploadChunkBytesProvider {
    suspend fun get(capabilities: OfishCapabilities): Int
}

internal class FixedUploadChunkBytesProvider(
    private val bytes: Int,
) : UploadChunkBytesProvider {
    init {
        require(bytes > 0) { "bytes must be greater than zero" }
    }

    override suspend fun get(capabilities: OfishCapabilities): Int = bytes
}

@Suppress("LongParameterList")
internal class OfishMutationClient(
    private val client: OfishWorkspaceClient,
    private val sessionFactory: OfishSessionFactory,
    private val capabilityCache: CachedOfishCapabilities,
    private val commandBuilder: OfishCommandBuilder = OfishCommandBuilder(),
    private val shellAgent: String = DEFAULT_SHELL_AGENT,
    private val uploadChunkBytes: UploadChunkBytesProvider = FixedUploadChunkBytesProvider(OFISH_DEFAULT_CHUNK_BYTES),
    private val maxUploadSourceBytes: Long = MAX_UPLOAD_SOURCE_BYTES,
) {

    init {
        require(maxUploadSourceBytes >= 0) { "max upload source bytes must not be negative" }
    }

    suspend fun mutationCapabilities(): OfishProbeResult = capabilityCache.get()

    /**
     * Compute the on-disk hash for [path] using the same shell `hash_file`
     * helper as the mutation commands. Returns null when capabilities are
     * unavailable, the path is invalid, the file does not exist, or the
     * shell invocation fails. We deliberately do not fall back to a
     * client-side digest: a hash that mismatches the server's would defeat
     * stale-write detection.
     */
    suspend fun hashFile(path: String): String? {
        val normalizedPath = normalizeMutationPath(path).getOrNull() ?: return null
        val capabilities = availableCapabilities().getOrNull() ?: return null
        return runCatching {
            sessionFactory.withSession(OPERATION_HASH) { session ->
                val status = execute(session.id, commandBuilder.hash(normalizedPath, capabilities), MARKER_HASH)
                if (status is OfishMutationStatus.Ok) status.hash else null
            }
        }.getOrElse { error ->
            AppLog.w(TAG, "OFISH baseline hash failed: ${error.javaClass.simpleName}")
            null
        }
    }

    suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> {
        val path = normalizeMutationPath(request.path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: INVALID_PATH_MESSAGE, error)
        }
        val contentBytes = request.content.toByteArray(Charsets.UTF_8)
        if (contentBytes.size >= OFISH_CHUNKED_WRITE_THRESHOLD_BYTES) {
            return uploadFile(
                FileUploadRequest(
                    path = path,
                    contentLength = contentBytes.size.toLong(),
                    openStream = { ByteArrayInputStream(contentBytes) },
                    expectedHash = request.expectedHash,
                ),
            ).toWriteResult()
        }
        val capabilities = availableCapabilities().getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: UNAVAILABLE_MESSAGE, error)
        }

        return runCatching {
            sessionFactory.withSession(OPERATION_WRITE) { session ->
                execute(
                    session.id,
                    commandBuilder.write(path, request.content, request.expectedHash, capabilities),
                    MARKER_WRITE,
                )
                    .toWriteResult(path)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                if (error is CancellationException) throw error
                FileOperationResult.Failed("OFISH write failed", error)
            },
        )
    }

    suspend fun deleteFile(path: String): FileOperationResult<Unit> {
        val normalizedPath = normalizeMutationPath(path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: INVALID_PATH_MESSAGE, error)
        }
        availableCapabilities().getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: UNAVAILABLE_MESSAGE, error)
        }

        return runCatching {
            sessionFactory.withSession(OPERATION_DELETE) { session ->
                execute(session.id, commandBuilder.delete(normalizedPath), MARKER_DELETE).toDeleteResult()
            }
        }.getOrElse { error -> FileOperationResult.Failed("OFISH delete failed", error) }
    }

    @Suppress("ReturnCount")
    suspend fun createDirectory(path: String): FileOperationResult<Unit> {
        val normalizedPath = normalizedPathOrFailure(path) ?: return mutationPathFailure(path)
        availableCapabilitiesOrFailure()?.let { return it }

        return runCatching {
            sessionFactory.withSession(OPERATION_MKDIR) { session ->
                execute(session.id, commandBuilder.mkdir(normalizedPath), MARKER_MKDIR).toCreateDirectoryResult()
            }
        }.getOrElse { error -> FileOperationResult.Failed("OFISH folder creation failed", error) }
    }

    @Suppress("ReturnCount")
    suspend fun renameFile(fromPath: String, toPath: String): FileOperationResult<Unit> {
        val normalizedFromPath = normalizedPathOrFailure(fromPath) ?: return mutationPathFailure(fromPath)
        val normalizedToPath = normalizedPathOrFailure(toPath) ?: return mutationPathFailure(toPath)
        availableCapabilitiesOrFailure()?.let { return it }

        return runCatching {
            sessionFactory.withSession(OPERATION_RENAME) { session ->
                execute(
                    session.id,
                    commandBuilder.rename(normalizedFromPath, normalizedToPath),
                    MARKER_RENAME,
                ).toRenameResult()
            }
        }.getOrElse { error -> FileOperationResult.Failed("OFISH rename failed", error) }
    }

    suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> {
        if (request.contentLength > maxUploadSourceBytes) {
            return FileOperationResult.Failed(UPLOAD_TOO_LARGE_MESSAGE)
        }
        val path = normalizeMutationPath(request.path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: INVALID_PATH_MESSAGE, error)
        }
        val capabilities = availableCapabilities().getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: UNAVAILABLE_MESSAGE, error)
        }

        return runCatching {
            sessionFactory.withSession(OPERATION_UPLOAD) { session ->
                uploadInSession(session.id, path, request, capabilities)
            }
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                if (error is CancellationException) throw error
                FileOperationResult.Failed("OFISH upload failed", error)
            },
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "ReturnCount")
    private suspend fun uploadInSession(
        sessionId: String,
        path: String,
        request: FileUploadRequest,
        capabilities: OfishCapabilities,
    ): FileOperationResult<FileUploadResult> {
        val initStatus = execute(
            sessionId,
            commandBuilder.uploadInit(path, request.expectedHash, capabilities, request.createOnly),
            MARKER_UPLOAD_INIT,
        )
        val uploadToken = when (initStatus) {
            is OfishMutationStatus.Ok -> initStatus.uploadToken
            else -> return initStatus.toUploadResult(path)
        } ?: return FileOperationResult.Failed("Malformed OFISH upload init response: missing upload token")
        var finished = false
        try {
            validateUploadToken(uploadToken, path).getOrElse { error ->
                return FileOperationResult.Failed(error.message ?: "Unsafe OFISH upload token", error)
            }
            val chunkBytes = uploadChunkBytes.get(capabilities)
            require(chunkBytes > 0) { "upload chunk size must be greater than zero" }
            var uploaded = 0L
            request.openStream().use { stream ->
                while (true) {
                    val remaining = maxUploadSourceBytes - uploaded
                    val readLimit = minOf(chunkBytes.toLong(), remaining + 1L).toInt()
                    val chunk = stream.readChunk(readLimit)
                    if (chunk.isEmpty()) break
                    if (chunk.size.toLong() > remaining) {
                        return FileOperationResult.Failed(UPLOAD_TOO_LARGE_MESSAGE)
                    }
                    uploaded += chunk.size
                    when (
                        val chunkStatus = execute(
                            sessionId,
                            commandBuilder.uploadChunk(uploadToken, chunk, capabilities),
                            MARKER_UPLOAD_CHUNK,
                        )
                    ) {
                        is OfishMutationStatus.Ok -> Unit
                        else -> return chunkStatus.toUploadResult(path)
                    }
                    request.onBytesUploaded?.invoke(uploaded)
                }
            }
            if (request.contentLength >= 0 && uploaded != request.contentLength) {
                val mismatchMessage = "OFISH upload length mismatch: " +
                    "expected ${request.contentLength} bytes, streamed $uploaded bytes"
                return FileOperationResult.Failed(
                    mismatchMessage
                )
            }

            val finishStatus =
                execute(
                    sessionId,
                    commandBuilder.uploadFinish(
                        path,
                        uploadToken,
                        request.expectedHash,
                        capabilities,
                        request.createOnly
                    ),
                    MARKER_UPLOAD_FINISH,
                )
            val result = finishStatus.toUploadResult(path)
            if (result is FileOperationResult.Ok) finished = true
            return result
        } finally {
            if (!finished) {
                withContext(NonCancellable) {
                    runCatching { execute(sessionId, commandBuilder.uploadAbort(uploadToken), MARKER_UPLOAD_ABORT) }
                        .onFailure { error ->
                            AppLog.w(
                                TAG,
                                "Failed to abort OFISH upload temp file: ${error.javaClass.simpleName}"
                            )
                        }
                }
            }
        }
    }

    private fun normalizedPathOrFailure(path: String): String? = normalizeMutationPath(path).getOrNull()

    private fun mutationPathFailure(path: String): FileOperationResult.Failed {
        val error = normalizeMutationPath(path).exceptionOrNull()
        return FileOperationResult.Failed(error?.message ?: INVALID_PATH_MESSAGE, error)
    }

    private suspend fun availableCapabilitiesOrFailure(): FileOperationResult.Failed? {
        val error = availableCapabilities().exceptionOrNull() ?: return null
        return FileOperationResult.Failed(error.message ?: UNAVAILABLE_MESSAGE, error)
    }

    private fun validateUploadToken(uploadToken: String, destinationPath: String): Result<String> {
        if (uploadToken.isBlank()) return Result.failure(UnsafeUploadTokenException("Empty OFISH upload token"))
        if (uploadToken.startsWith("/")) {
            return Result.failure(UnsafeUploadTokenException("Absolute OFISH upload token is not allowed"))
        }
        val normalized = FilePathValidator.normalizeForMutation(uploadToken).getOrElse { error ->
            return Result.failure(UnsafeUploadTokenException(error.message ?: "Unsafe OFISH upload token", error))
        }

        val expectedParent = OfishCommandBuilder.parentDirectory(destinationPath)
        val expectedSegments = if (expectedParent == ".") emptyList() else expectedParent.split('/')
        val tokenSegments = normalized.split('/')
        if (tokenSegments.size != expectedSegments.size + 1) {
            return Result.failure(UnsafeUploadTokenException("OFISH upload token must be a direct spool file"))
        }
        if (tokenSegments.take(expectedSegments.size) != expectedSegments) {
            return Result.failure(UnsafeUploadTokenException("OFISH upload token is outside expected spool path"))
        }

        val filename = tokenSegments.last()
        val suffix = filename.removePrefix(UPLOAD_TOKEN_PREFIX)
        if (filename == suffix || suffix.isEmpty()) {
            return Result.failure(UnsafeUploadTokenException("OFISH upload token must use the expected spool filename"))
        }
        return Result.success(normalized)
    }

    private suspend fun execute(
        sessionId: String,
        command: String,
        expectedMarker: String,
    ): OfishMutationStatus {
        val output = client.executeShellOutput(
            sessionId = sessionId,
            request = ShellCommandRequest(
                agent = shellAgent,
                model = null,
                command = command,
            ),
            marker = expectedMarker,
        ) ?: return OfishMutationStatus.Malformed(
            "Malformed OFISH mutation output: missing $expectedMarker output segment"
        )
        return OfishMutationParser.parse(output, expectedMarker)
    }

    private suspend fun availableCapabilities(): Result<OfishCapabilities> = when (val result = capabilityCache.get()) {
        is OfishProbeResult.Available -> Result.success(result.capabilities)
        is OfishProbeResult.Missing -> Result.failure(
            OfishUnavailableException("OFISH file mutations unavailable: ${result.missing.joinToString()}", null)
        )
        is OfishProbeResult.Failed -> Result.failure(OfishUnavailableException(result.message, result.cause))
    }

    private fun normalizeMutationPath(path: String): Result<String> = FilePathValidator.normalizeForMutation(path)

    private fun InputStream.readChunk(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            val read = read(buffer, offset, maxBytes - offset)
            if (read < 0) break
            if (read == 0) {
                val nextByte = read()
                if (nextByte < 0) break
                buffer[offset++] = nextByte.toByte()
            } else {
                offset += read
            }
        }
        return if (offset == buffer.size) buffer else buffer.copyOf(offset)
    }

    private fun OfishMutationStatus.toWriteResult(path: String): FileOperationResult<FileWriteResult> = when (this) {
        is OfishMutationStatus.Ok -> FileOperationResult.Ok(FileWriteResult(path = path, hash = hash))
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict("File was modified before write", actualHash)
        OfishMutationStatus.Missing -> FileOperationResult.Conflict("File does not exist", currentHash = null)
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed(
            "Write precondition failed${reasonSuffix()}"
        )
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed(
            "OFISH file mutations unavailable: ${missing.joinToString()}"
        )
        is OfishMutationStatus.Failed -> FileOperationResult.Failed(messageWithReason())
        is OfishMutationStatus.Malformed -> FileOperationResult.Failed(message)
        OfishMutationStatus.Deleted -> FileOperationResult.Failed("Unexpected OFISH write delete status")
    }

    private fun FileOperationResult<FileUploadResult>.toWriteResult(): FileOperationResult<FileWriteResult> =
        when (this) {
            is FileOperationResult.Ok -> FileOperationResult.Ok(FileWriteResult(path = data.path, hash = data.hash))
            is FileOperationResult.Conflict -> this
            is FileOperationResult.Failed -> this
        }

    private fun OfishMutationStatus.toDeleteResult(): FileOperationResult<Unit> = when (this) {
        OfishMutationStatus.Deleted -> FileOperationResult.Ok(Unit)
        OfishMutationStatus.Missing -> FileOperationResult.Failed("File does not exist")
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed(
            "Delete precondition failed${reasonSuffix()}"
        )
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed(
            "OFISH file mutations unavailable: ${missing.joinToString()}"
        )
        is OfishMutationStatus.Failed -> FileOperationResult.Failed(messageWithReason())
        is OfishMutationStatus.Malformed -> FileOperationResult.Failed(message)
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict("File was modified before delete", actualHash)
        is OfishMutationStatus.Ok -> FileOperationResult.Failed("Unexpected OFISH delete ok status")
    }

    private fun OfishMutationStatus.toCreateDirectoryResult(): FileOperationResult<Unit> = when (this) {
        is OfishMutationStatus.Ok -> FileOperationResult.Ok(Unit)
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict(
            "A file or folder already exists at that path",
            actualHash,
        )
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed(
            "Folder creation precondition failed${reasonSuffix()}"
        )
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed(
            "OFISH file mutations unavailable: ${missing.joinToString()}"
        )
        is OfishMutationStatus.Failed -> FileOperationResult.Failed(messageWithReason())
        is OfishMutationStatus.Malformed -> FileOperationResult.Failed(message)
        OfishMutationStatus.Missing -> FileOperationResult.Failed("Unexpected OFISH folder creation missing status")
        OfishMutationStatus.Deleted -> FileOperationResult.Failed("Unexpected OFISH folder creation delete status")
    }

    private fun OfishMutationStatus.toRenameResult(): FileOperationResult<Unit> = when (this) {
        is OfishMutationStatus.Ok -> FileOperationResult.Ok(Unit)
        OfishMutationStatus.Missing -> FileOperationResult.Failed("File does not exist")
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict(
            "A file or folder already exists at the destination",
            actualHash,
        )
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed(
            "Rename precondition failed${reasonSuffix()}"
        )
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed(
            "OFISH file mutations unavailable: ${missing.joinToString()}"
        )
        is OfishMutationStatus.Failed -> FileOperationResult.Failed(messageWithReason())
        is OfishMutationStatus.Malformed -> FileOperationResult.Failed(message)
        OfishMutationStatus.Deleted -> FileOperationResult.Failed("Unexpected OFISH rename delete status")
    }

    private fun OfishMutationStatus.toUploadResult(path: String): FileOperationResult<FileUploadResult> = when (this) {
        is OfishMutationStatus.Ok -> FileOperationResult.Ok(FileUploadResult(path = path, hash = hash))
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict("File was modified before upload", actualHash)
        OfishMutationStatus.Missing -> FileOperationResult.Conflict("File does not exist", currentHash = null)
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed(
            "Upload precondition failed${reasonSuffix()}"
        )
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed(
            "OFISH file mutations unavailable: ${missing.joinToString()}"
        )
        is OfishMutationStatus.Failed -> FileOperationResult.Failed(messageWithReason())
        is OfishMutationStatus.Malformed -> FileOperationResult.Failed(message)
        OfishMutationStatus.Deleted -> FileOperationResult.Failed("Unexpected OFISH upload delete status")
    }

    private fun OfishMutationStatus.PreconditionFailed.reasonSuffix(): String = reason?.let { ": $it" }.orEmpty()

    private fun OfishMutationStatus.Failed.messageWithReason(): String = reason?.let { "$message: $it" } ?: message

    private companion object {
        const val TAG = "OfishMutationClient"
        const val DEFAULT_SHELL_AGENT = "build"
        const val INVALID_PATH_MESSAGE = "Invalid file path"
        const val UNAVAILABLE_MESSAGE = "OFISH file mutations unavailable"
        const val OPERATION_WRITE = "write"
        const val OPERATION_DELETE = "delete"
        const val OPERATION_MKDIR = "mkdir"
        const val OPERATION_RENAME = "rename"
        const val OPERATION_UPLOAD = "upload"
        const val OPERATION_HASH = "hash"
        const val MARKER_HASH = "#OFISH_HASH"
        const val MARKER_WRITE = "#OFISH_WRITE"
        const val MARKER_DELETE = "#OFISH_DELETE"
        const val MARKER_MKDIR = "#OFISH_MKDIR"
        const val MARKER_RENAME = "#OFISH_RENAME"
        const val MARKER_UPLOAD_INIT = "#OFISH_UPLOAD_INIT"
        const val MARKER_UPLOAD_CHUNK = "#OFISH_UPLOAD_CHUNK"
        const val MARKER_UPLOAD_FINISH = "#OFISH_UPLOAD_FINISH"
        const val MARKER_UPLOAD_ABORT = "#OFISH_UPLOAD_ABORT"
        const val UPLOAD_TOKEN_PREFIX = ".ofish.upload."
        const val OFISH_CHUNKED_WRITE_THRESHOLD_BYTES = 32 * 1024
    }
}

internal open class CachedOfishCapabilities(
    private val probe: OfishCapabilityProbe,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: OfishProbeResult? = null

    open suspend fun get(): OfishProbeResult {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            probe.probe().also { cached = it }
        }
    }
}

private class OfishUnavailableException(message: String, cause: Throwable?) : Exception(message, cause)

private class UnsafeUploadTokenException(message: String, cause: Throwable? = null) : Exception(message, cause)
