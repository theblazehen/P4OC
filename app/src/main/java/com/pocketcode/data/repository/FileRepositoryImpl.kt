package com.pocketcode.data.repository

import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.ConnectionManager
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.domain.model.FileContent
import com.pocketcode.domain.model.FileNode
import com.pocketcode.domain.model.FileStatus
import com.pocketcode.domain.model.SearchLine
import com.pocketcode.domain.model.SearchResult
import com.pocketcode.domain.model.Submatch
import com.pocketcode.domain.repository.FileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
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

    private fun com.pocketcode.data.remote.dto.FileNodeDto.toDomain(): FileNode = FileNode(
        name = name,
        path = path,
        absolute = absolute,
        type = type,
        ignored = ignored
    )
}
