package dev.blazelight.p4oc.fakes

import dev.blazelight.p4oc.data.remote.dto.SendMessageRequest
import dev.blazelight.p4oc.data.session.RepoState
import dev.blazelight.p4oc.data.session.SessionRepository
import dev.blazelight.p4oc.data.session.SessionUiState
import dev.blazelight.p4oc.data.session.Snapshot
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import dev.blazelight.p4oc.domain.session.SessionId
import dev.blazelight.p4oc.domain.session.WorkspaceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSessionRepository(
    initialState: RepoState = RepoState.Live(Snapshot()),
) : SessionRepository {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<RepoState> = _state.asStateFlow()

    var refreshCalls: Int = 0
        private set

    var refreshResult: RepoState? = null
    var refreshFailure: Throwable? = null
    var getSessionOverride: (suspend (SessionId) -> WorkspaceSession?)? = null

    fun setState(state: RepoState) {
        _state.value = state
    }

    fun setSessions(sessions: Iterable<WorkspaceSession>) {
        _state.value = RepoState.Live(
            Snapshot(sessions.associateBy { it.id.value }),
        )
    }

    override suspend fun refresh() {
        refreshCalls += 1
        refreshFailure?.let { throw it }
        refreshResult?.let { _state.value = it }
    }

    override suspend fun getSession(id: SessionId): WorkspaceSession? = getSessionOverride?.invoke(id)
        ?: state.value.snapshot.sessions[id.value]

    override fun acceptEvent(event: OpenCodeEvent) = Unit

    override fun messages(sessionId: SessionId): StateFlow<List<MessageWithParts>> = MutableStateFlow(emptyList())

    override fun sessionUiState(sessionId: SessionId): StateFlow<SessionUiState> = MutableStateFlow(SessionUiState())

    override fun clearPermission(sessionId: SessionId, permissionId: String) = Unit

    override fun clearPermissionByRequestId(sessionId: SessionId, requestId: String) = Unit

    override fun clearQuestion(sessionId: SessionId, requestId: String?) = Unit

    override suspend fun loadMessages(sessionId: SessionId, limit: Int?) = Unit

    override fun sendMessageAsync(sessionId: SessionId, request: SendMessageRequest): Deferred<Result<Unit>> =
        CompletableDeferred(Result.success(Unit))

    override fun abortSession(sessionId: SessionId): Deferred<Result<Boolean>> =
        CompletableDeferred(Result.success(true))

    override fun clearStreamingFlags(sessionId: SessionId) = Unit

    override fun close() = Unit
}
