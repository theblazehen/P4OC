package dev.blazelight.p4oc.data.repository

import dev.blazelight.p4oc.core.database.dao.SessionDao
import dev.blazelight.p4oc.core.database.entity.SessionEntity
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ForkSessionRequest
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.SessionSummary
import dev.blazelight.p4oc.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class SessionRepositoryImpl constructor(
    private val connectionManager: ConnectionManager,
    private val directoryManager: DirectoryManager,
    private val sessionDao: SessionDao,
    private val sessionMapper: SessionMapper
) : SessionRepository {

    override fun getSessions(): Flow<List<Session>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSession(id: String): Flow<Session?> {
        return sessionDao.getSessionByIdFlow(id).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun fetchSessions(directory: String?): ApiResult<List<Session>> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dtos = api.listSessions(directory ?: directoryManager.getDirectory())
            val sessions = dtos.map { sessionMapper.mapToDomain(it) }
            sessionDao.insertSessions(sessions.map { it.toEntity() })
            sessions
        }
    }

    override suspend fun fetchSession(id: String, directory: String?): ApiResult<Session> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dto = api.getSession(id, directory ?: directoryManager.getDirectory())
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun createSession(title: String?, directory: String?, parentId: String?): ApiResult<Session> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dto = api.createSession(
                directory = directory ?: directoryManager.getDirectory(),
                request = CreateSessionRequest(parentID = parentId, title = title)
            )
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun updateSession(id: String, title: String?, archived: Boolean?, directory: String?): ApiResult<Session> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dto = api.updateSession(id, UpdateSessionRequest(title = title, archived = archived), directory ?: directoryManager.getDirectory())
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun deleteSession(id: String, directory: String?): ApiResult<Boolean> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val result = api.deleteSession(id, directory ?: directoryManager.getDirectory())
            if (result) {
                sessionDao.deleteSessionById(id)
            }
            result
        }
    }

    override suspend fun forkSession(id: String, messageId: String, directory: String?): ApiResult<Session> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val dto = api.forkSession(id, ForkSessionRequest(messageID = messageId), directory ?: directoryManager.getDirectory())
            val session = sessionMapper.mapToDomain(dto)
            sessionDao.insertSession(session.toEntity())
            session
        }
    }

    override suspend fun abortSession(id: String, directory: String?): ApiResult<Boolean> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            api.abortSession(id, directory ?: directoryManager.getDirectory())
        }
    }

    override suspend fun getSessionStatuses(directory: String?): ApiResult<Map<String, SessionStatus>> {
        val api = connectionManager.getApi() ?: return ApiResult.Error(message = "Not connected")
        return safeApiCall {
            val statusMap = api.getSessionStatuses(directory ?: directoryManager.getDirectory())
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
