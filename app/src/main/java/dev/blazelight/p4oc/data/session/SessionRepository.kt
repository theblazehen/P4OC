package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.model.MessageWithParts
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val state: StateFlow<RepoState>

    suspend fun refresh()

    suspend fun getSession(id: SessionId): WorkspaceSession?

    fun acceptEvent(event: OpenCodeEvent)

    fun messages(sessionId: SessionId): StateFlow<List<MessageWithParts>>

    suspend fun loadMessages(sessionId: SessionId, limit: Int? = null)

    fun clearStreamingFlags(sessionId: SessionId)

    fun close()
}
