package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FilePathValidator
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileUploadResult
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.files.FileWriteResult
import dev.blazelight.p4oc.data.remote.dto.ShellCommandRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

internal class OfishMutationClient(
    private val client: OfishWorkspaceClient,
    private val sessionFactory: OfishSessionFactory,
    private val capabilityCache: CachedOfishCapabilities,
    private val commandBuilder: OfishCommandBuilder = OfishCommandBuilder(),
    private val shellAgent: String = DEFAULT_SHELL_AGENT,
    private val uploadChunkBytes: UploadChunkBytesProvider = FixedUploadChunkBytesProvider(OFISH_DEFAULT_CHUNK_BYTES),
) {

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
                val status = execute(session.id, commandBuilder.hash(normalizedPath, capabilities))
                if (status is OfishMutationStatus.Ok) status.hash else null
            }
        }.getOrElse { error ->
            AppLog.w(TAG, "OFISH baseline hash failed for $path: ${error.message}")
            null
        }
    }

    suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> {
        val path = normalizeMutationPath(request.path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: INVALID_PATH_MESSAGE, error)
        }
        val capabilities = availableCapabilities().getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: UNAVAILABLE_MESSAGE, error)
        }

        return runCatching {
            sessionFactory.withSession(OPERATION_WRITE) { session ->
                execute(session.id, commandBuilder.write(path, request.content, request.expectedHash, capabilities))
                    .toWriteResult(path)
            }
        }.getOrElse { error -> FileOperationResult.Failed("OFISH write failed", error) }
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
                execute(session.id, commandBuilder.delete(normalizedPath)).toDeleteResult()
            }
        }.getOrElse { error -> FileOperationResult.Failed("OFISH delete failed", error) }
    }

    suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> {
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
        }.getOrElse { error -> FileOperationResult.Failed("OFISH upload failed", error) }
    }

    private suspend fun uploadInSession(
        sessionId: String,
        path: String,
        request: FileUploadRequest,
        capabilities: OfishCapabilities,
    ): FileOperationResult<FileUploadResult> {
        val initStatus = execute(sessionId, commandBuilder.uploadInit(path, request.expectedHash, capabilities))
        val uploadToken = when (initStatus) {
            is OfishMutationStatus.Ok -> initStatus.uploadToken
            else -> return initStatus.toUploadResult(path)
        } ?: return FileOperationResult.Failed("Malformed OFISH upload init response: missing upload token")
        validateUploadToken(uploadToken, path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: "Unsafe OFISH upload token", error)
        }

        var finished = false
        try {
            val chunkBytes = try {
                uploadChunkBytes.get(capabilities)
            } catch (error: OfishUploadChunkProbeUnavailableException) {
                return FileOperationResult.Failed(error.message ?: "OFISH upload chunk probe failed", error)
            }
            require(chunkBytes > 0) { "upload chunk size must be greater than zero" }
            var offset = 0
            while (offset < request.bytes.size) {
                val end = minOf(offset + chunkBytes, request.bytes.size)
                val chunk = request.bytes.copyOfRange(offset, end)
                when (val chunkStatus = execute(sessionId, commandBuilder.uploadChunk(uploadToken, chunk, capabilities))) {
                    is OfishMutationStatus.Ok -> Unit
                    else -> return chunkStatus.toUploadResult(path)
                }
                offset = end
                request.onBytesUploaded?.invoke(offset.toLong())
            }

            val finishStatus = execute(sessionId, commandBuilder.uploadFinish(path, uploadToken, request.expectedHash, capabilities))
            val result = finishStatus.toUploadResult(path)
            if (result is FileOperationResult.Ok) finished = true
            return result
        } finally {
            if (!finished) {
                withContext(NonCancellable) {
                    runCatching { execute(sessionId, commandBuilder.uploadAbort(uploadToken)) }
                        .onFailure { error -> AppLog.w(TAG, "Failed to abort OFISH upload temp file: ${error.message}") }
                }
            }
        }
    }

    private fun validateUploadToken(uploadToken: String, destinationPath: String): Result<String> {
        if (uploadToken.isBlank()) return Result.failure(UnsafeUploadTokenException("Empty OFISH upload token"))
        if (uploadToken.startsWith("/")) return Result.failure(UnsafeUploadTokenException("Absolute OFISH upload token is not allowed"))
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

    private suspend fun execute(sessionId: String, command: String): OfishMutationStatus {
        val response = client.executeShellCommand(
            sessionId = sessionId,
            request = ShellCommandRequest(
                agent = shellAgent,
                model = null,
                command = command,
            ),
        )
        return OfishMutationParser.parse(OfishShellOutputExtractor.extract(response))
    }

    private suspend fun availableCapabilities(): Result<OfishCapabilities> = when (val result = capabilityCache.get()) {
        is OfishProbeResult.Available -> Result.success(result.capabilities)
        is OfishProbeResult.Missing -> Result.failure(OfishUnavailableException("OFISH file mutations unavailable: ${result.missing.joinToString()}", null))
        is OfishProbeResult.Failed -> Result.failure(OfishUnavailableException(result.message, result.cause))
    }

    private fun normalizeMutationPath(path: String): Result<String> = FilePathValidator.normalizeForMutation(path)

    private fun OfishMutationStatus.toWriteResult(path: String): FileOperationResult<FileWriteResult> = when (this) {
        is OfishMutationStatus.Ok -> FileOperationResult.Ok(FileWriteResult(path = path, hash = hash))
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict("File was modified before write", actualHash)
        OfishMutationStatus.Missing -> FileOperationResult.Conflict("File does not exist", currentHash = null)
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed("Write precondition failed${reasonSuffix()}")
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed("OFISH file mutations unavailable: ${missing.joinToString()}")
        is OfishMutationStatus.Failed -> FileOperationResult.Failed(messageWithReason())
        is OfishMutationStatus.Malformed -> FileOperationResult.Failed(message)
        OfishMutationStatus.Deleted -> FileOperationResult.Failed("Unexpected OFISH write delete status")
    }

    private fun OfishMutationStatus.toDeleteResult(): FileOperationResult<Unit> = when (this) {
        OfishMutationStatus.Deleted -> FileOperationResult.Ok(Unit)
        OfishMutationStatus.Missing -> FileOperationResult.Failed("File does not exist")
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed(
            if (reason == "directory") "Directory deletion is not supported" else "Delete precondition failed${reasonSuffix()}"
        )
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed("OFISH file mutations unavailable: ${missing.joinToString()}")
        is OfishMutationStatus.Failed -> FileOperationResult.Failed(messageWithReason())
        is OfishMutationStatus.Malformed -> FileOperationResult.Failed(message)
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict("File was modified before delete", actualHash)
        is OfishMutationStatus.Ok -> FileOperationResult.Failed("Unexpected OFISH delete ok status")
    }

    private fun OfishMutationStatus.toUploadResult(path: String): FileOperationResult<FileUploadResult> = when (this) {
        is OfishMutationStatus.Ok -> FileOperationResult.Ok(FileUploadResult(path = path, hash = hash))
        is OfishMutationStatus.Conflict -> FileOperationResult.Conflict("File was modified before upload", actualHash)
        OfishMutationStatus.Missing -> FileOperationResult.Conflict("File does not exist", currentHash = null)
        is OfishMutationStatus.PreconditionFailed -> FileOperationResult.Failed("Upload precondition failed${reasonSuffix()}")
        is OfishMutationStatus.CapabilitiesMissing -> FileOperationResult.Failed("OFISH file mutations unavailable: ${missing.joinToString()}")
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
        const val OPERATION_UPLOAD = "upload"
        const val OPERATION_HASH = "hash"
        const val UPLOAD_TOKEN_PREFIX = ".ofish.upload."
    }
}

internal open class CachedOfishCapabilities(
    private val probe: OfishCapabilityProbe,
) {
    private val mutex = Mutex()
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
