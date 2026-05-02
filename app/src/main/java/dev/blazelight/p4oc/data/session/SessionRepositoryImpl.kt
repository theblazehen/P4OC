package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionRepositoryImpl(
    private val client: SessionWorkspaceClient,
) : SessionRepository {
    private val _state = MutableStateFlow<RepoState>(RepoState.Hydrating())
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    override suspend fun refresh() {
        val sessions = client.listSessions().associate { dto ->
            val session = SessionMapper.mapToDomain(dto)
            session.id to WorkspaceSession(SessionId(session.id), client.workspace, session)
        }
        _state.value = RepoState.Live(Snapshot(sessions))
    }

    override suspend fun getSession(id: SessionId): WorkspaceSession? {
        val current = state.value.snapshot.sessions[id.value]
        if (current != null) return current

        val session = SessionMapper.mapToDomain(client.getSession(id.value))
        return WorkspaceSession(id, client.workspace, session)
    }
}
