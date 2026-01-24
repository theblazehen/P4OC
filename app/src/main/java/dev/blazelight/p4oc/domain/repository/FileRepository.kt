package dev.blazelight.p4oc.domain.repository

import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.domain.model.FileContent
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.FileStatus
import dev.blazelight.p4oc.domain.model.SearchResult

interface FileRepository {
    suspend fun listFiles(path: String = "."): ApiResult<List<FileNode>>
    suspend fun readFile(path: String): ApiResult<FileContent>
    suspend fun getFileStatus(): ApiResult<List<FileStatus>>
    suspend fun searchText(pattern: String): ApiResult<List<SearchResult>>
    suspend fun searchFiles(query: String, type: String? = null, limit: Int? = null): ApiResult<List<String>>
}
