package dev.blazelight.p4oc.data.files

import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.FileContentDto
import dev.blazelight.p4oc.data.remote.dto.FileNodeDto
import dev.blazelight.p4oc.data.remote.dto.FileStatusDto
import dev.blazelight.p4oc.data.remote.dto.SymbolDto
import dev.blazelight.p4oc.data.remote.mapper.SymbolMapper
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol

interface FileRepository {
    suspend fun listFiles(path: String): FileOperationResult<FileList>
    suspend fun readFile(path: String): FileOperationResult<FileContent>
    suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>>
    suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult>
    suspend fun deleteFile(path: String): FileOperationResult<Unit>
    suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult>
    suspend fun capabilities(): FileCapabilities
}

sealed interface FileOperationResult<out T> {
    data class Ok<T>(val data: T) : FileOperationResult<T>
    data class Conflict(
        val message: String,
        val currentHash: String? = null,
    ) : FileOperationResult<Nothing>

    data class Failed(
        val message: String,
        val cause: Throwable? = null,
    ) : FileOperationResult<Nothing>
}

data class FileList(
    val path: String,
    val files: List<FileNode>,
)

data class FileCapabilities(
    val canRead: Boolean = true,
    val canList: Boolean = true,
    val canWrite: Boolean = false,
    val canDelete: Boolean = false,
    val canUpload: Boolean = false,
)

data class FileWriteRequest(
    val path: String,
    val content: String,
    val expectedHash: String? = null,
)

data class FileWriteResult(
    val path: String,
    val hash: String? = null,
)

data class FileUploadRequest(
    val path: String,
    val bytes: ByteArray,
    val expectedHash: String? = null,
)

data class FileUploadResult(
    val path: String,
    val hash: String? = null,
)

class WorkspaceFileRepository internal constructor(
    private val client: FileWorkspaceClient,
) : FileRepository {
    constructor(workspaceClient: WorkspaceClient) : this(WorkspaceClientFileAdapter(workspaceClient))

    override suspend fun listFiles(path: String): FileOperationResult<FileList> {
        val normalizedPath = FilePathValidator.normalizeForReadOrList(path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: INVALID_PATH_MESSAGE, error)
        }

        val filesResult = safeApiCall { client.listFiles(normalizedPath.ifBlank { ROOT_API_PATH }) }
        val statusResult = safeApiCall { client.getFileStatus() }
        val statusMap = when (statusResult) {
            is ApiResult.Success -> statusResult.data.associateBy { it.path }
            is ApiResult.Error -> emptyMap()
        }

        return when (filesResult) {
            is ApiResult.Success -> FileOperationResult.Ok(
                FileList(
                    path = normalizedPath,
                    files = filesResult.data.map { dto -> dto.toDomain(statusMap[dto.path]?.status) }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })),
                )
            )
            is ApiResult.Error -> FileOperationResult.Failed(filesResult.message, filesResult.throwable)
        }
    }

    override suspend fun readFile(path: String): FileOperationResult<FileContent> {
        val normalizedPath = FilePathValidator.normalizeForReadOrList(path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: INVALID_PATH_MESSAGE, error)
        }

        return when (val result = safeApiCall { client.readFile(normalizedPath) }) {
            is ApiResult.Success -> FileOperationResult.Ok(result.data.toDomain())
            is ApiResult.Error -> FileOperationResult.Failed(result.message, result.throwable)
        }
    }

    override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return FileOperationResult.Ok(emptyList())

        return when (val result = safeApiCall { client.searchSymbols(normalizedQuery) }) {
            is ApiResult.Success -> FileOperationResult.Ok(result.data.map { SymbolMapper.mapToDomain(it) })
            is ApiResult.Error -> FileOperationResult.Failed(result.message, result.throwable)
        }
    }

    override suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> {
        return unsupportedMutationResult(request.path)
    }

    override suspend fun deleteFile(path: String): FileOperationResult<Unit> {
        return unsupportedMutationResult(path)
    }

    override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> {
        return unsupportedMutationResult(request.path)
    }

    override suspend fun capabilities(): FileCapabilities = FileCapabilities()

    private fun <T> unsupportedMutationResult(path: String): FileOperationResult<T> {
        FilePathValidator.normalizeForMutation(path).getOrElse { error ->
            return FileOperationResult.Failed(error.message ?: INVALID_PATH_MESSAGE, error)
        }
        return FileOperationResult.Failed(UNSUPPORTED_MUTATION_MESSAGE)
    }

    private fun FileNodeDto.toDomain(gitStatus: String?): FileNode = FileNode(
        name = name,
        path = path,
        absolute = absolute,
        type = type,
        ignored = ignored,
        gitStatus = gitStatus,
    )

    private fun FileContentDto.toDomain(): FileContent = FileContent(
        type = type,
        content = content,
        diff = diff,
        mimeType = mimeType,
        hash = null,
        encoding = encoding,
    )

    private companion object {
        const val ROOT_API_PATH = "."
        const val INVALID_PATH_MESSAGE = "Invalid file path"
        const val UNSUPPORTED_MUTATION_MESSAGE = "File mutations are not supported yet"
    }
}

internal interface FileWorkspaceClient {
    suspend fun listFiles(path: String): List<FileNodeDto>
    suspend fun readFile(path: String): FileContentDto
    suspend fun getFileStatus(): List<FileStatusDto>
    suspend fun searchSymbols(query: String): List<SymbolDto>
}

private class WorkspaceClientFileAdapter(
    private val workspaceClient: WorkspaceClient,
) : FileWorkspaceClient {
    override suspend fun listFiles(path: String): List<FileNodeDto> = workspaceClient.listFiles(path)

    override suspend fun readFile(path: String): FileContentDto = workspaceClient.readFile(path)

    override suspend fun getFileStatus(): List<FileStatusDto> = workspaceClient.getFileStatus()

    override suspend fun searchSymbols(query: String): List<SymbolDto> = workspaceClient.searchSymbols(query)
}
