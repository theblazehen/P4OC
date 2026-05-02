package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val state: StateFlow<RepoState>

    suspend fun refresh()

    suspend fun getSession(id: SessionId): WorkspaceSession?
}
