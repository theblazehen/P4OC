package dev.blazelight.p4oc.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.session.RepoState
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.session.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionListViewModel constructor(
    private val sessionRepository: SessionRepositoryImpl,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collect { repoState ->
                val snapshot = repoState.snapshot
                _uiState.update { state ->
                    state.copy(
                        isLoading = repoState is RepoState.Hydrating,
                        sessions = snapshot.sessions.values
                            .map { workspaceSession -> workspaceSession.session.toSessionWithProject(snapshot.projects) }
                            .sortedByDescending { it.session.updatedAt },
                        projects = snapshot.projects.map(::toProjectInfo).sortedByDescending { it.worktree },
                        sessionStatuses = snapshot.statuses,
                        error = (repoState as? RepoState.Stale)?.reason ?: state.error,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            sessionRepository.awaitOrFetch().fold(
                onSuccess = { cached ->
                    val snapshot = cached.snapshot
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessions = snapshot.sessions.values
                                .map { workspaceSession -> workspaceSession.session.toSessionWithProject(snapshot.projects) }
                                .sortedByDescending { session -> session.session.updatedAt },
                            projects = snapshot.projects.map(::toProjectInfo).sortedByDescending { project -> project.worktree },
                            sessionStatuses = snapshot.statuses,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load sessions: ${error.message}") }
                },
            )
        }
    }

    fun createSession(title: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val created = sessionRepository.createSession(title)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        newSessionId = created.id.value,
                        newSessionDirectory = created.session.directory,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to create session: ${e.message}") }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionRepository.deleteSession(SessionId(sessionId))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete session: ${e.message}") }
            }
        }
    }

    fun clearNewSession() {
        _uiState.update { it.copy(newSessionId = null, newSessionDirectory = null) }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                sessionRepository.renameSession(SessionId(sessionId), newTitle)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to rename: ${e.message}") }
            }
        }
    }

    fun shareSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val updated = sessionRepository.shareSession(SessionId(sessionId))
                _uiState.update { it.copy(shareUrl = updated.session.shareUrl) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to share session: ${e.message}") }
            }
        }
    }

    fun unshareSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionRepository.unshareSession(SessionId(sessionId))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to unshare: ${e.message}") }
            }
        }
    }

    fun clearShareUrl() {
        _uiState.update { it.copy(shareUrl = null) }
    }

    fun summarizeSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionRepository.summarizeSession(SessionId(sessionId))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to summarize: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun Session.toSessionWithProject(projects: List<ProjectDto>): SessionWithProject {
        val project = projects.find { it.worktree == directory || it.id == projectID }
        return SessionWithProject(
            session = this,
            projectId = project?.id,
            projectName = project?.worktree?.substringAfterLast("/"),
        )
    }

    private fun toProjectInfo(dto: ProjectDto): ProjectInfo = ProjectInfo(
        id = dto.id,
        worktree = dto.worktree,
        name = dto.worktree.substringAfterLast("/"),
    )
}

data class SessionListUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionWithProject> = emptyList(),
    val sessionStatuses: Map<String, SessionStatus> = emptyMap(),
    val projects: List<ProjectInfo> = emptyList(),
    val newSessionId: String? = null,
    val newSessionDirectory: String? = null,
    val shareUrl: String? = null,
    val error: String? = null
)

data class ProjectInfo(
    val id: String,
    val worktree: String,
    val name: String
)

/**
 * Session with optional project metadata for unified sessions view.
 */
data class SessionWithProject(
    val session: Session,
    val projectId: String? = null,
    val projectName: String? = null
)
