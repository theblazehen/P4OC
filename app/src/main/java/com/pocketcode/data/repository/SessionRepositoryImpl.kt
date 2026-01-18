package com.pocketcode.data.repository

import com.pocketcode.core.database.dao.SessionDao
import com.pocketcode.core.database.entity.SessionEntity
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.data.remote.dto.CreateSessionRequest
import com.pocketcode.data.remote.dto.ForkSessionRequest
import com.pocketcode.data.remote.dto.UpdateSessionRequest
import com.pocketcode.data.remote.mapper.SessionMapper
import com.pocketcode.domain.model.Session
import com.pocketcode.domain.model.SessionStatus
import com.pocketcode.domain.model.SessionSummary
import com.pocketcode.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val api: OpenCodeApi,
    private val sessionDao: SessionDao,
    private val sessionMapper: SessionMapper
) : SessionRepository {

    override fun getSessions(): Flow<List<Session>> {
        return sessionDao.getActiveSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSession(id: String): Flow<Session?> {
        return sessionDao.getSessionByIdFlow(id).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun fetchSessions(): ApiResult<List<Session>> {
        return safeApiCall {
            val dtos = api.listSessions()
            val sessions = dtos.map { sessionMapper.mapToDomain(it) }
            sessionDao.insertSessions(sessions.map { it.toEntity() })
            sessions
        }
    }

    override suspend fun fetchSession(id: String): ApiResult<Session> {
        return safeApiCall {
            val dto = api.getSession(id)
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun createSession(title: String?, parentId: String?): ApiResult<Session> {
        return safeApiCall {
            val dto = api.createSession(CreateSessionRequest(parentID = parentId, title = title))
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun updateSession(id: String, title: String?, archived: Boolean?): ApiResult<Session> {
        return safeApiCall {
            val dto = api.updateSession(id, UpdateSessionRequest(title = title, archived = archived))
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun deleteSession(id: String): ApiResult<Boolean> {
        return safeApiCall {
            val result = api.deleteSession(id)
            if (result) {
                sessionDao.deleteSessionById(id)
            }
            result
        }
    }

    override suspend fun forkSession(id: String, messageId: String): ApiResult<Session> {
        return safeApiCall {
            val dto = api.forkSession(id, ForkSessionRequest(messageID = messageId))
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun abortSession(id: String): ApiResult<Boolean> {
        return safeApiCall {
            api.abortSession(id)
        }
    }

    override suspend fun getSessionStatuses(): ApiResult<Map<String, SessionStatus>> {
        return safeApiCall {
            val statusMap = api.getSessionStatuses()
            statusMap.mapValues { sessionMapper.mapStatusToDomain(it.value) }
        }
    }

    private fun SessionEntity.toDomain(): Session = Session(
        id = id,
        projectID = projectID,
        directory = directory,
        parentID = parentID,
        title = title,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        summary = if (summaryAdditions != null && summaryDeletions != null && summaryFiles != null) {
            SessionSummary(summaryAdditions, summaryDeletions, summaryFiles)
        } else null,
        shareUrl = shareUrl
    )

    private fun Session.toEntity(): SessionEntity = SessionEntity(
        id = id,
        projectID = projectID,
        directory = directory,
        parentID = parentID,
        title = title,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        summaryAdditions = summary?.additions,
        summaryDeletions = summary?.deletions,
        summaryFiles = summary?.files,
        shareUrl = shareUrl
    )
}
