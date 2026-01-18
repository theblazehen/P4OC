package com.pocketcode.domain.repository

import com.pocketcode.core.network.ApiResult
import com.pocketcode.domain.model.Session
import com.pocketcode.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getSessions(): Flow<List<Session>>
    fun getSession(id: String): Flow<Session?>
    suspend fun fetchSessions(): ApiResult<List<Session>>
    suspend fun fetchSession(id: String): ApiResult<Session>
    suspend fun createSession(title: String? = null, parentId: String? = null): ApiResult<Session>
    suspend fun updateSession(id: String, title: String? = null, archived: Boolean? = null): ApiResult<Session>
    suspend fun deleteSession(id: String): ApiResult<Boolean>
    suspend fun forkSession(id: String, messageId: String): ApiResult<Session>
    suspend fun abortSession(id: String): ApiResult<Boolean>
    suspend fun getSessionStatuses(): ApiResult<Map<String, SessionStatus>>
}
