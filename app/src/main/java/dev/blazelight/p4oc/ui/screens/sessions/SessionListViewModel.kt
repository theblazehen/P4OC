package dev.blazelight.p4oc.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.session.RepoState
import dev.blazelight.p4oc.data.session.SessionRepositoryImpl
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionPresence
import dev.blazelight.p4oc.domain.model.SessionStatus
import dev.blazelight.p4oc.domain.model.resolveSessionPresence
import dev.blazelight.p4oc.domain.session.SessionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SessionListViewModel constructor(
    private val sessionRepository: SessionRepositoryImpl,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    private companion object {
        const val LOAD_TIMEOUT_MS = 30_000L
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            sessionRepository.state.collect { repoState ->
                val snapshot = repoState.snapshot
                _uiState.update { state ->
                    state.copy(
                        isLoading = repoState is RepoState.Hydrating,
                        loadingText = if (repoState is RepoState.Hydrating) {
                            repoState.currentStep ?: "Loading projects and sessions"
                        } else {
                            null
                        },
                        loadingProgress = if (repoState is RepoState.Hydrating && repoState.totalSteps > 0) {
                            repoState.completedSteps.toFloat() / repoState.totalSteps.toFloat()
                        } else {
                            null
                        },
                        loadingCounts = if (repoState is RepoState.Hydrating && repoState.totalSteps > 0) {
                            "${repoState.completedSteps}/${repoState.totalSteps}"
                        } else {
                            null
                        },
                        sessions = snapshot.sessions.values
                            .map { workspaceSession -> workspaceSession.session.toSessionWithProject(
                                snapshot.projects
                            ) }
                            .sortedByDescending { it.session.updatedAt },
                        projects = snapshot.projects.map(::toProjectInfo).sortedByDescending { it.worktree },
                        sessionStatuses = snapshot.statuses,
                        sessionPresences = snapshot.statuses.mapValues { (_, status) -> resolveSessionPresence(
                            status
                        ) },
                        searchResults = if (state.searchQuery.isBlank()) emptyList() else state.searchResults,
                        error = (repoState as? RepoState.Stale)?.reason ?: state.error,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val activeSearchQuery = _uiState.value.searchQuery
            val activeSearchDirectory = _uiState.value.searchDirectory
            _uiState.update { it.copy(isLoading = true, loadingText = "Loading projects and sessions", error = null) }
            val result = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                sessionRepository.awaitOrFetch()
            } ?: Result.failure(IllegalStateException("Timed out while loading sessions"))
            result.fold(
                onSuccess = { cached ->
                    val snapshot = cached.snapshot
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingText = null,
                            loadingProgress = null,
                            loadingCounts = null,
                            sessions = snapshot.sessions.values
                                .map { workspaceSession -> workspaceSession.session.toSessionWithProject(
                                    snapshot.projects
                                ) }
                                .sortedByDescending { session -> session.session.updatedAt },
                            projects = snapshot.projects.map(
                                ::toProjectInfo
                            ).sortedByDescending { project -> project.worktree },
                            sessionStatuses = snapshot.statuses,
                            sessionPresences = snapshot.statuses.mapValues { (_, status) -> resolveSessionPresence(
                                status
                            ) },
                            error = null,
                        )
                    }
                    if (activeSearchQuery.isNotBlank()) {
                        searchSessions(activeSearchQuery, activeSearchDirectory, debounce = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingText = null,
                            loadingProgress = null,
                            loadingCounts = null,
                            error = "Failed to load sessions: ${error.message}"
                        )
                    }
                },
            )
        }
    }

    fun updateSearchQuery(query: String, directory: String?) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                searchDirectory = directory,
                searchError = null,
                searchResults = if (query.isBlank()) emptyList() else state.searchResults,
            )
        }
        searchSessions(query, directory, debounce = true)
    }

    fun updateSearchDirectory(directory: String?) {
        val query = _uiState.value.searchQuery
        _uiState.update { it.copy(searchDirectory = directory) }
        if (query.isNotBlank()) {
            searchSessions(query, directory, debounce = false)
        }
    }

    private fun searchSessions(query: String, directory: String?, debounce: Boolean) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    serverSearchQuery = null,
                    searchResults = emptyList(),
                    searchError = null,
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            val result = runCatching { sessionRepository.searchSessions(trimmed, directory) }
            result.fold(
                onSuccess = { sessions ->
                    _uiState.update { state ->
                        if (state.searchQuery.trim() == trimmed && state.searchDirectory == directory) {
                            val projects = state.projects
                            state.copy(
                                isSearching = false,
                                serverSearchQuery = trimmed,
                                searchResults = sessions.map { workspaceSession ->
                                    val session = workspaceSession.session
                                    val project = projects.find { it.worktree == session.directory }
                                    SessionWithProject(
                                        session = session,
                                        projectId = project?.id,
                                        projectName = (project?.worktree ?: session.directory).substringAfterLast("/"),
                                    )
                                },
                                searchError = null,
                            )
                        } else {
                            state
                        }
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        if (state.searchQuery.trim() == trimmed && state.searchDirectory == directory) {
                            state.copy(
                                isSearching = false,
                                searchError = "Search failed: ${error.message ?: "Unknown error"}",
                            )
                        } else {
                            state
                        }
                    }
                },
            )
        }
    }

    fun createSession(title: String?, directory: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Creating session", error = null) }
            try {
                if (directory != null && directory != sessionRepository.workspace.directory) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        loadingText = null,
                        loadingProgress = null,
                        loadingCounts = null,
                        error = "Switch to $directory before creating a session"
                    ) }
                    return@launch
                }
                val created = sessionRepository.createSession(title)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingText = null,
                        loadingProgress = null,
                        loadingCounts = null,
                        newSessionId = created.id.value,
                        newSessionDirectory = created.session.directory,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingText = null,
                        loadingProgress = null,
                        loadingCounts = null,
                        error = "Failed to create session: ${e.message}"
                    )
                }
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionRepository.deleteSession(SessionId(sessionId))
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to share session: ${e.message}") }
            }
        }
    }

    fun unshareSession(sessionId: String) {
        viewModelScope.launch {
            try {
                sessionRepository.unshareSession(SessionId(sessionId))
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to summarize: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun Session.toSessionWithProject(projects: List<ProjectDto>): SessionWithProject {
        val project = projects.find { it.worktree == directory }
        return SessionWithProject(
            session = this,
            projectId = project?.id,
            projectName = (project?.worktree ?: directory).substringAfterLast("/"),
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
    val loadingText: String? = null,
    val loadingProgress: Float? = null,
    val loadingCounts: String? = null,
    val sessions: List<SessionWithProject> = emptyList(),
    val sessionStatuses: Map<String, SessionStatus> = emptyMap(),
    val sessionPresences: Map<String, SessionPresence> = emptyMap(),
    val projects: List<ProjectInfo> = emptyList(),
    val searchQuery: String = "",
    val searchDirectory: String? = null,
    val serverSearchQuery: String? = null,
    val searchResults: List<SessionWithProject> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val newSessionId: String? = null,
    val newSessionDirectory: String? = null,
    val shareUrl: String? = null,
    val error: String? = null
) {
    val isSearchActive: Boolean
        get() = searchQuery.isNotBlank()

    val displayedSearchResults: List<SessionWithProject>
        get() {
            val trimmed = searchQuery.trim()
            if (trimmed.isBlank()) return emptyList()
            if (serverSearchQuery == trimmed) return searchResults
            return searchResults.filter { it.session.title.contains(trimmed, ignoreCase = true) }
        }

    val searchStatus: SessionSearchStatus?
        get() = when {
            searchQuery.isBlank() -> null
            searchError != null -> SessionSearchStatus.Failed
            isSearching && serverSearchQuery != searchQuery.trim() -> SessionSearchStatus.Refining
            isSearching -> SessionSearchStatus.Searching
            serverSearchQuery == searchQuery.trim() -> SessionSearchStatus.Current
            else -> SessionSearchStatus.Refining
        }
}

sealed interface SessionSearchStatus {
    data object Searching : SessionSearchStatus
    data object Refining : SessionSearchStatus
    data object Current : SessionSearchStatus
    data object Failed : SessionSearchStatus
}

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
