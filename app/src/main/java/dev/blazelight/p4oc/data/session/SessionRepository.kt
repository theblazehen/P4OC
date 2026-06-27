package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val state: StateFlow<RepoState>

    suspend fun refresh()

    suspend fun getSession(id: SessionId): WorkspaceSession?

    fun acceptEvent(event: OpenCodeEvent)

    fun messages(sessionId: SessionId): StateFlow<List<MessageWithParts>>

    fun sessionUiState(sessionId: SessionId): StateFlow<SessionUiState>

    fun clearPermission(sessionId: SessionId, permissionId: String)

    fun clearPermissionByRequestId(sessionId: SessionId, requestId: String)

    fun clearQuestion(sessionId: SessionId, requestId: String? = null)

    suspend fun loadMessages(sessionId: SessionId, limit: Int? = null)

    fun sendMessageAsync(sessionId: SessionId, request: SendMessageRequest): Deferred<Result<Unit>>

    fun abortSession(sessionId: SessionId): Deferred<Result<Boolean>>

    fun clearStreamingFlags(sessionId: SessionId)

    fun close()
}
