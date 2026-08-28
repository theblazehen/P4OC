package dev.blazelight.p4oc.data.files.ofish

import dev.blazelight.p4oc.data.files.FileCapabilities
import dev.blazelight.p4oc.data.files.FileList
import dev.blazelight.p4oc.data.files.FileOperationResult
import dev.blazelight.p4oc.data.files.FileRepository
import dev.blazelight.p4oc.data.files.FileUploadRequest
import dev.blazelight.p4oc.data.files.FileUploadResult
import dev.blazelight.p4oc.data.files.FileWriteRequest
import dev.blazelight.p4oc.data.files.FileWriteResult
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol

internal class OfishFileRepository(
    private val delegate: FileRepository,
    private val mutationClient: OfishMutationClient,
) : FileRepository {
    override suspend fun listFiles(path: String): FileOperationResult<FileList> = delegate.listFiles(path)

    override suspend fun readFile(path: String): FileOperationResult<FileContent> {
        val result = delegate.readFile(path)
        if (result !is FileOperationResult.Ok) return result

        // Only hash on disk when mutations are available, content is text, and
        // it is not a binary/base64-encoded payload. Anything else: leave the
        // server-supplied hash (if any) untouched.
        val content = result.data
        if (content.type != "text" || content.encoding != null) return result

        val capabilities = mutationClient.mutationCapabilities()
        if (capabilities !is OfishProbeResult.Available) return result

        // Honest fallback: if shell hashing fails, return null hash so the
        // caller treats this as "no baseline, no conflict detection" rather
        // than a digest of the in-memory string that may not match on-disk
        // bytes (CRLF/BOM/encoding quirks would yield false 409s).
        val hash = mutationClient.hashFile(path)
        return FileOperationResult.Ok(content.copy(hash = hash))
    }

    override suspend fun searchFiles(query: String): FileOperationResult<List<FileNode>> = delegate.searchFiles(query)

    override suspend fun searchSymbols(query: String): FileOperationResult<List<Symbol>> = delegate.searchSymbols(query)

    override suspend fun writeFile(request: FileWriteRequest): FileOperationResult<FileWriteResult> =
        mutationClient.writeFile(request)

    override suspend fun createDirectory(path: String): FileOperationResult<Unit> = mutationClient.createDirectory(path)

    override suspend fun renameFile(fromPath: String, toPath: String): FileOperationResult<Unit> =
        mutationClient.renameFile(fromPath, toPath)

    override suspend fun deleteFile(path: String): FileOperationResult<Unit> = mutationClient.deleteFile(path)

    override suspend fun uploadFile(request: FileUploadRequest): FileOperationResult<FileUploadResult> =
        mutationClient.uploadFile(request)

    override suspend fun capabilities(): FileCapabilities {
        val delegateCapabilities = delegate.capabilities()
        val mutationsAvailable = mutationClient.mutationCapabilities() is OfishProbeResult.Available
        return delegateCapabilities.copy(
            canWrite = mutationsAvailable,
            canCreateDirectory = mutationsAvailable,
            canRename = mutationsAvailable,
            canDelete = mutationsAvailable,
            canUpload = mutationsAvailable,
        )
    }
}
