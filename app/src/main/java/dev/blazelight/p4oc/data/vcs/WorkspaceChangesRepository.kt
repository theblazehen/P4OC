package dev.blazelight.p4oc.data.vcs

import dev.blazelight.p4oc.data.remote.dto.WorkspaceVcsDiffDto
import dev.blazelight.p4oc.data.remote.dto.WorkspaceVcsInfoDto
import dev.blazelight.p4oc.data.remote.dto.WorkspaceVcsStatusDto
import dev.blazelight.p4oc.data.server.StaleWorkspaceClientException
import dev.blazelight.p4oc.data.workspace.BoundedResponseTooLargeException
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

private const val MAX_ENTRIES = 10_000
private const val MAX_PATH_BYTES = 4L * 1024
private const val MAX_BRANCH_BYTES = 512L
private const val MAX_PATCH_BYTES = 1024L * 1024
private const val MAX_AGGREGATE_PATCH_BYTES = 8L * 1024 * 1024

enum class VcsDiffMode(private val queryValue: String) {
    Git("git"),
    Branch("branch");

    override fun toString(): String = queryValue
}

enum class WorkspaceChangeStatus {
    Added,
    Modified,
    Deleted,
}

data class WorkspaceChange(
    val file: String,
    val status: WorkspaceChangeStatus,
    val additions: Long,
    val deletions: Long,
)

data class WorkspaceChangesSnapshot(
    val serverLabel: String,
    val workspaceDirectory: String?,
    val branch: String?,
    val defaultBranch: String?,
    val changes: List<WorkspaceChange>,
    val additions: Long,
    val deletions: Long,
)

sealed interface WorkspaceChangesResult<out T> {
    data class Success<T>(val data: T) : WorkspaceChangesResult<T>
    data object Unsupported : WorkspaceChangesResult<Nothing>
    data object TooLarge : WorkspaceChangesResult<Nothing>
    data object Malformed : WorkspaceChangesResult<Nothing>
    data object Stale : WorkspaceChangesResult<Nothing>
    data object AuthorizationFailure : WorkspaceChangesResult<Nothing>
    data object HttpFailure : WorkspaceChangesResult<Nothing>
    data object NetworkFailure : WorkspaceChangesResult<Nothing>
    data object Failure : WorkspaceChangesResult<Nothing>
}

sealed interface WorkspacePatch {
    data class Content(val text: String) : WorkspacePatch
    data object Unavailable : WorkspacePatch
    data object TooLarge : WorkspacePatch
    data object Stale : WorkspacePatch
}

interface WorkspaceChangesRepository {
    suspend fun loadSnapshot(): WorkspaceChangesResult<WorkspaceChangesSnapshot>
    suspend fun loadDiff(): WorkspaceChangesResult<Map<String, WorkspacePatch>>
}

class WorkspaceChangesRepositoryImpl(
    private val workspaceClient: WorkspaceClient,
) : WorkspaceChangesRepository {
    override suspend fun loadSnapshot(): WorkspaceChangesResult<WorkspaceChangesSnapshot> = mapFailures {
        val info = workspaceClient.loadWorkspaceVcsInfo()
        validateInfo(info)
        val status = workspaceClient.loadWorkspaceVcsStatus()
        val changes = validateStatus(status)
        val totals = checkedTotals(changes)
        WorkspaceChangesSnapshot(
            serverLabel = workspaceClient.workspace.server.displayName,
            workspaceDirectory = workspaceClient.workspace.directory,
            branch = info.branch,
            defaultBranch = info.defaultBranch,
            changes = changes,
            additions = totals.first,
            deletions = totals.second,
        )
    }

    override suspend fun loadDiff(): WorkspaceChangesResult<Map<String, WorkspacePatch>> = mapFailures {
        val entries = workspaceClient.loadWorkspaceVcsDiff(
            mode = VcsDiffMode.Git,
            context = 3,
        )
        validateDiff(entries)
    }

    private suspend fun <T> mapFailures(block: suspend () -> T): WorkspaceChangesResult<T> = try {
        WorkspaceChangesResult.Success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (_: BoundedResponseTooLargeException) {
        WorkspaceChangesResult.TooLarge
    } catch (_: OversizedWorkspaceChangesException) {
        WorkspaceChangesResult.TooLarge
    } catch (_: SerializationException) {
        WorkspaceChangesResult.Malformed
    } catch (_: MalformedWorkspaceChangesException) {
        WorkspaceChangesResult.Malformed
    } catch (_: StaleWorkspaceClientException) {
        WorkspaceChangesResult.Stale
    } catch (error: HttpException) {
        when (error.code()) {
            in UNSUPPORTED_HTTP_CODES -> WorkspaceChangesResult.Unsupported
            in AUTHORIZATION_HTTP_CODES -> WorkspaceChangesResult.AuthorizationFailure
            else -> WorkspaceChangesResult.HttpFailure
        }
    } catch (_: IOException) {
        WorkspaceChangesResult.NetworkFailure
    } catch (_: Exception) {
        WorkspaceChangesResult.Failure
    }
}

private fun validateInfo(info: WorkspaceVcsInfoDto) {
    if (info.branch != null && info.branch.utf8Size() > MAX_BRANCH_BYTES) {
        throw OversizedWorkspaceChangesException()
    }
    if (info.defaultBranch != null && info.defaultBranch.utf8Size() > MAX_BRANCH_BYTES) {
        throw OversizedWorkspaceChangesException()
    }
}

private fun validateStatus(entries: List<WorkspaceVcsStatusDto>): List<WorkspaceChange> {
    if (entries.size > MAX_ENTRIES) throw OversizedWorkspaceChangesException()
    val paths = HashSet<String>(entries.size)
    return entries.map { entry ->
        validatePath(entry.file, paths)
        WorkspaceChange(
            file = entry.file,
            status = parseStatus(entry.status),
            additions = validateCount(entry.additions),
            deletions = validateCount(entry.deletions),
        )
    }
}

private fun validateDiff(entries: List<WorkspaceVcsDiffDto>): Map<String, WorkspacePatch> {
    if (entries.size > MAX_ENTRIES) throw OversizedWorkspaceChangesException()
    val paths = HashSet<String>(entries.size)
    var aggregatePatchBytes = 0L
    val patches = LinkedHashMap<String, WorkspacePatch>(entries.size)
    entries.forEach { entry ->
        validatePath(entry.file, paths)
        entry.status?.let(::parseStatus)
        validateCount(entry.additions)
        validateCount(entry.deletions)

        val patch = entry.patch
        if (patch == null) {
            patches[entry.file] = WorkspacePatch.Unavailable
        } else {
            val patchBytes = patch.utf8Size()
            aggregatePatchBytes = checkedAdd(aggregatePatchBytes, patchBytes, oversized = true)
            if (aggregatePatchBytes > MAX_AGGREGATE_PATCH_BYTES) {
                throw OversizedWorkspaceChangesException()
            }
            patches[entry.file] = if (patchBytes > MAX_PATCH_BYTES) {
                WorkspacePatch.TooLarge
            } else {
                WorkspacePatch.Content(patch)
            }
        }
    }
    return patches
}

private fun validatePath(path: String, paths: MutableSet<String>) {
    validateNonBlankPath(path)
    validatePathSize(path)
    validateUniquePath(path, paths)
}

private fun validateNonBlankPath(path: String) {
    if (path.isBlank()) throw MalformedWorkspaceChangesException()
}

private fun validatePathSize(path: String) {
    if (path.utf8Size() > MAX_PATH_BYTES) throw OversizedWorkspaceChangesException()
}

private fun validateUniquePath(path: String, paths: MutableSet<String>) {
    if (!paths.add(path)) throw MalformedWorkspaceChangesException()
}

private fun parseStatus(status: String): WorkspaceChangeStatus = when (status) {
    "added" -> WorkspaceChangeStatus.Added
    "modified" -> WorkspaceChangeStatus.Modified
    "deleted" -> WorkspaceChangeStatus.Deleted
    else -> throw MalformedWorkspaceChangesException()
}

private fun validateCount(value: Long): Long {
    if (value < 0L) throw MalformedWorkspaceChangesException()
    return value
}

private fun checkedTotals(changes: List<WorkspaceChange>): Pair<Long, Long> {
    var additions = 0L
    var deletions = 0L
    changes.forEach { change ->
        additions = checkedAdd(additions, change.additions, oversized = false)
        deletions = checkedAdd(deletions, change.deletions, oversized = false)
    }
    return additions to deletions
}

private fun checkedAdd(left: Long, right: Long, oversized: Boolean): Long = try {
    Math.addExact(left, right)
} catch (_: ArithmeticException) {
    if (oversized) throw OversizedWorkspaceChangesException()
    throw MalformedWorkspaceChangesException()
}

private fun String.utf8Size(): Long {
    var bytes = 0L
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character.code < UTF8_ONE_BYTE_CODE_POINT_LIMIT -> bytes += UTF8_ONE_BYTE_COUNT
            character.code < UTF8_TWO_BYTE_CODE_POINT_LIMIT -> bytes += UTF8_TWO_BYTE_COUNT
            character.code in HIGH_SURROGATE_RANGE &&
                index + UTF16_TRAILING_SURROGATE_OFFSET < length &&
                this[index + UTF16_TRAILING_SURROGATE_OFFSET].code in LOW_SURROGATE_RANGE -> {
                bytes += UTF8_FOUR_BYTE_COUNT
                index++
            }
            character.code in SURROGATE_RANGE -> bytes += UTF8_ONE_BYTE_COUNT
            else -> bytes += UTF8_THREE_BYTE_COUNT
        }
        index++
    }
    return bytes
}

private class MalformedWorkspaceChangesException : IllegalArgumentException()
private class OversizedWorkspaceChangesException : IllegalArgumentException()

private const val HTTP_NOT_FOUND = 404
private const val HTTP_METHOD_NOT_ALLOWED = 405
private const val HTTP_NOT_IMPLEMENTED = 501
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val UTF8_ONE_BYTE_CODE_POINT_LIMIT = 0x80
private const val UTF8_TWO_BYTE_CODE_POINT_LIMIT = 0x800
private const val UTF8_ONE_BYTE_COUNT = 1L
private const val UTF8_TWO_BYTE_COUNT = 2L
private const val UTF8_THREE_BYTE_COUNT = 3L
private const val UTF8_FOUR_BYTE_COUNT = 4L
private const val UTF16_TRAILING_SURROGATE_OFFSET = 1
private const val HIGH_SURROGATE_START = 0xD800
private const val HIGH_SURROGATE_END = 0xDBFF
private const val LOW_SURROGATE_START = 0xDC00
private const val LOW_SURROGATE_END = 0xDFFF

private val UNSUPPORTED_HTTP_CODES = setOf(
    HTTP_NOT_FOUND,
    HTTP_METHOD_NOT_ALLOWED,
    HTTP_NOT_IMPLEMENTED,
)
private val AUTHORIZATION_HTTP_CODES = setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)
private val HIGH_SURROGATE_RANGE = HIGH_SURROGATE_START..HIGH_SURROGATE_END
private val LOW_SURROGATE_RANGE = LOW_SURROGATE_START..LOW_SURROGATE_END
private val SURROGATE_RANGE = HIGH_SURROGATE_START..LOW_SURROGATE_END
