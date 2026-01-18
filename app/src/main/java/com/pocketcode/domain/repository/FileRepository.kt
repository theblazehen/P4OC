package com.pocketcode.domain.repository

import com.pocketcode.core.network.ApiResult
import com.pocketcode.domain.model.FileContent
import com.pocketcode.domain.model.FileNode
import com.pocketcode.domain.model.FileStatus
import com.pocketcode.domain.model.SearchResult

interface FileRepository {
    suspend fun listFiles(path: String? = null): ApiResult<List<FileNode>>
    suspend fun readFile(path: String): ApiResult<FileContent>
    suspend fun getFileStatus(): ApiResult<List<FileStatus>>
    suspend fun searchText(pattern: String): ApiResult<List<SearchResult>>
    suspend fun searchFiles(query: String, type: String? = null, limit: Int? = null): ApiResult<List<String>>
}
