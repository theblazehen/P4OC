package dev.blazelight.p4oc.domain.repository

import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun getSessions(): Flow<List<Session>>
    fun getSession(id: String): Flow<Session?>
    suspend fun fetchSessions(directory: String? = null): ApiResult<List<Session>>
    suspend fun fetchSession(id: String, directory: String? = null): ApiResult<Session>
    suspend fun createSession(title: String? = null, directory: String? = null, parentId: String? = null): ApiResult<Session>
    suspend fun updateSession(id: String, title: String? = null, archived: Boolean? = null, directory: String? = null): ApiResult<Session>
    suspend fun deleteSession(id: String, directory: String? = null): ApiResult<Boolean>
    suspend fun forkSession(id: String, messageId: String, directory: String? = null): ApiResult<Session>
    suspend fun abortSession(id: String, directory: String? = null): ApiResult<Boolean>
    suspend fun getSessionStatuses(directory: String? = null): ApiResult<Map<String, SessionStatus>>
}
