package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val state: StateFlow<RepoState>

    suspend fun refresh()

    suspend fun getSession(id: SessionId): WorkspaceSession?

    fun acceptEvent(event: OpenCodeEvent)
}
