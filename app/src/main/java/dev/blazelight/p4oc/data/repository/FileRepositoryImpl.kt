package dev.blazelight.p4oc.data.repository

import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.FileStatus
import dev.blazelight.p4oc.domain.model.SearchLine
import dev.blazelight.p4oc.domain.model.SearchResult
import dev.blazelight.p4oc.domain.model.Submatch
import dev.blazelight.p4oc.domain.repository.FileRepository


class FileRepositoryImpl constructor(
    private val connectionManager: ConnectionManager
) : FileRepository {

    override suspend fun listFiles(path: String): ApiResult<List<FileNode>> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dtos = api.listFiles(path)
            dtos.map { it.toDomain() }
        }
    }

    override suspend fun readFile(path: String): ApiResult<FileContent> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dto = api.readFile(path)
            FileContent(
                type = dto.type,
                content = dto.content,
                diff = dto.diff,
                mimeType = dto.mimeType
            )
        }
    }

    override suspend fun getFileStatus(): ApiResult<List<FileStatus>> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dtos = api.getFileStatus()
            dtos.map { dto ->
                FileStatus(
                    path = dto.path,
                    status = dto.status,
                    added = dto.added,
                    removed = dto.removed
                )
            }
        }
    }

    override suspend fun searchText(pattern: String): ApiResult<List<SearchResult>> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dtos = api.searchText(pattern)
            dtos.map { dto ->
                SearchResult(
                    path = dto.path,
                    lineNumber = dto.lineNumber,
                    lines = dto.lines?.map { SearchLine(it.text) } ?: emptyList(),
                    absoluteOffset = dto.absoluteOffset,
                    submatches = dto.submatches?.map { Submatch(it.match, it.start, it.end) }
                )
            }
        }
    }

    override suspend fun searchFiles(query: String, type: String?, limit: Int?): ApiResult<List<String>> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            api.searchFiles(query, type, limit)
        }
    }

    private fun dev.blazelight.p4oc.data.remote.dto.FileNodeDto.toDomain(): FileNode = FileNode(
        name = name,
        path = path,
        absolute = absolute,
        type = type,
        ignored = ignored
    )
}
