package dev.blazelight.p4oc.fakes

import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.remote.dto.ProjectTimeDto
import dev.blazelight.p4oc.data.remote.dto.SessionDto
import dev.blazelight.p4oc.data.remote.dto.SessionShareDto
import dev.blazelight.p4oc.data.remote.dto.SessionStatusDto
import dev.blazelight.p4oc.data.remote.dto.TimeDto
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
import dev.blazelight.p4oc.data.workspace.SessionWorkspaceClient
import dev.blazelight.p4oc.domain.server.ServerRef
import dev.blazelight.p4oc.domain.workspace.Workspace
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger

class FakeWorkspaceClient(
    override val workspace: Workspace = Workspace(
        server = ServerRef.fromEndpointKey("http://fake.test"),
        directory = "/workspace",
    ),
) : SessionWorkspaceClient {
    var listProjectsCalls: Int = 0
        private set
    var listSessionsCalls: Int = 0
        private set
    var getSessionCalls: Int = 0
        private set
    var getSessionStatusesCalls: Int = 0
        private set
    var deleteSessionCalls: Int = 0
        private set

    val listSessionsDirectories = mutableListOf<String?>()
    val getSessionStatusesDirectories = mutableListOf<String?>()

    var projects: List<ProjectDto> = emptyList()
    var listSessionsResult: List<SessionDto> = emptyList()
    var sessionsByDirectory: Map<String?, List<SessionDto>>? = null
    var statusesByDirectory: Map<String?, Map<String, SessionStatusDto>> = emptyMap()
    var listSessionsFailure: Throwable? = null
    var getSessionResults: MutableMap<String, SessionDto> = mutableMapOf()
    var getSessionFailure: Throwable? = null
    var deleteSessionFailure: Throwable? = null
    var statusBlocker: CompletableDeferred<Unit>? = null
    var trackStatusConcurrency: Boolean = false
    private val activeStatusCalls = AtomicInteger(0)
    var maxObservedStatusConcurrency: Int = 0

    override suspend fun listProjects(): List<ProjectDto> {
        listProjectsCalls += 1
        return projects
    }

    override suspend fun listSessions(
        directory: String?,
        roots: Boolean?,
        start: Long?,
        search: String?,
        limit: Int?,
    ): List<SessionDto> {
        listSessionsCalls += 1
        listSessionsDirectories += directory
        listSessionsFailure?.let { throw it }
        return sessionsByDirectory?.get(directory) ?: listSessionsResult
    }

    override suspend fun getSession(id: String): SessionDto {
        getSessionCalls += 1
        getSessionFailure?.let { throw it }
        return getSessionResults[id] ?: error("No fake session configured for id: $id")
    }

    override suspend fun getSessionStatuses(directory: String?): Map<String, SessionStatusDto> {
        getSessionStatusesCalls += 1
        getSessionStatusesDirectories += directory
        statusBlocker?.await()
        if (trackStatusConcurrency) {
            val now = activeStatusCalls.incrementAndGet()
            maxObservedStatusConcurrency = maxOf(maxObservedStatusConcurrency, now)
            try {
                kotlinx.coroutines.delay(1)
            } finally {
                activeStatusCalls.decrementAndGet()
            }
        }
        return statusesByDirectory[directory].orEmpty()
    }

    override suspend fun createSession(request: CreateSessionRequest): SessionDto {
        val session = sessionDto(id = "created", title = request.title ?: "created", directory = workspace.directory.orEmpty())
        setSessions(session, *listSessionsResult.toTypedArray())
        return session
    }

    override suspend fun deleteSession(id: String): Boolean {
        deleteSessionCalls += 1
        deleteSessionFailure?.let { throw it }
        listSessionsResult = listSessionsResult.filterNot { it.id == id }
        return true
    }

    override suspend fun updateSession(id: String, request: UpdateSessionRequest): SessionDto {
        val existing = getSessionResults[id] ?: sessionDto(id = id, directory = workspace.directory.orEmpty())
        val updated = existing.copy(title = request.title ?: existing.title)
        getSessionResults[id] = updated
        return updated
    }

    override suspend fun shareSession(id: String): SessionDto {
        val updated = (getSessionResults[id] ?: sessionDto(id = id, directory = workspace.directory.orEmpty()))
            .copy(share = SessionShareDto(url = "https://share/$id"))
        getSessionResults[id] = updated
        return updated
    }

    override suspend fun unshareSession(id: String): SessionDto {
        val updated = (getSessionResults[id] ?: sessionDto(id = id, directory = workspace.directory.orEmpty()))
            .copy(share = null)
        getSessionResults[id] = updated
        return updated
    }

    override suspend fun summarizeSession(id: String): Boolean = true

    fun setSessions(vararg sessions: SessionDto) {
        listSessionsResult = sessions.toList()
        getSessionResults = sessions.associateBy { it.id }.toMutableMap()
    }

    companion object {
        fun projectDto(id: String, worktree: String): ProjectDto = ProjectDto(
            id = id,
            worktree = worktree,
            time = ProjectTimeDto(created = 1L, initialized = 2L),
        )

        fun sessionDto(
            id: String,
            title: String = id,
            directory: String = "/workspace",
            updatedAt: Long = 1L,
        ): SessionDto = SessionDto(
            id = id,
            projectID = "project-$id",
            directory = directory,
            title = title,
            version = "1",
            time = TimeDto(created = 1L, updated = updatedAt),
        )
    }
}
