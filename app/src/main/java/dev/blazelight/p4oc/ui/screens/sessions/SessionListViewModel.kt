package dev.blazelight.p4oc.ui.screens.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.log.AppLog
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

@Suppress("TooManyFunctions")
class SessionListViewModel constructor(
    private val sessionRepository: SessionRepositoryImpl,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    companion object {
        internal const val MAX_SEARCH_QUERY_CHARS = 512
        internal const val MAX_SAVED_CONTEXTS = 16
        internal const val MAX_EXPANDED_SESSION_IDS_PER_CONTEXT = 64
        internal const val MAX_CONTEXT_KEY_CHARS = 1_024
        internal const val MAX_SESSION_ID_CHARS = 256

        private const val TAG = "SessionListViewModel"
        private const val LOAD_TIMEOUT_MS = 30_000L
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val KEY_SEARCH_QUERIES = "session_list_search_queries"
        private const val KEY_EXPANDED_SESSIONS = "session_list_expanded_sessions"
        private const val KEY_CONTEXT_RECENCY = "session_list_context_recency"
        private const val GLOBAL_CONTEXT_KEY = "__global__"
    }

    private var searchJob: Job? = null
    private val restoredContextRecency = savedStateHandle.get<ArrayList<String>>(KEY_CONTEXT_RECENCY)
        .orEmpty()
        .filter(::isPersistableContext)
        .distinct()
        .takeLast(MAX_SAVED_CONTEXTS)
    private val contextRecency = LinkedHashSet(restoredContextRecency)
    private val searchQueriesByContext = restoredStringMap(KEY_SEARCH_QUERIES)
    private val expandedSessionIdsByContext = restoredStringSetMap(KEY_EXPANDED_SESSIONS)

    private fun restoredStringMap(key: String): MutableMap<String, String> {
        val restored = savedStateHandle.get<HashMap<String, String>>(key).orEmpty()
            .filterKeys(::isPersistableContext)
            .mapValues { (_, query) -> boundedQuery(query) }
        return retainedContexts(restored.keys)
            .mapNotNull { context -> restored[context]?.let { context to it } }
            .toMap(LinkedHashMap())
    }

    private fun restoredStringSetMap(key: String): MutableMap<String, Set<String>> {
        val restored = savedStateHandle.get<HashMap<String, ArrayList<String>>>(key).orEmpty()
            .filterKeys(::isPersistableContext)
        return retainedContexts(restored.keys)
            .mapNotNull { context ->
                restored[context]?.filter(::isPersistableSessionId)
                    ?.distinct()
                    ?.takeLast(MAX_EXPANDED_SESSION_IDS_PER_CONTEXT)
                    ?.toCollection(LinkedHashSet())
                    ?.let { context to it }
            }
            .toMap(LinkedHashMap())
    }

    private fun retainedContexts(contexts: Set<String>): List<String> {
        val ordered = contextRecency.filter { it in contexts } +
            contexts.filterNot { it in contextRecency }.sorted()
        return ordered.takeLast(MAX_SAVED_CONTEXTS)
    }

    private fun touchContext(key: String) {
        if (!isPersistableContext(key)) return
        contextRecency.remove(key)
        contextRecency.add(key)
        while (contextRecency.size > MAX_SAVED_CONTEXTS) {
            val evicted = contextRecency.first()
            contextRecency.remove(evicted)
            searchQueriesByContext.remove(evicted)
            expandedSessionIdsByContext.remove(evicted)
        }
        savedStateHandle[KEY_CONTEXT_RECENCY] = ArrayList(contextRecency)
    }

    private fun boundedQuery(query: String): String = query.take(MAX_SEARCH_QUERY_CHARS)

    private fun isPersistableContext(key: String): Boolean = key.length <= MAX_CONTEXT_KEY_CHARS

    private fun isPersistableSessionId(id: String): Boolean = id.length <= MAX_SESSION_ID_CHARS

    private fun persistSearchQueries() {
        savedStateHandle[KEY_SEARCH_QUERIES] = HashMap(searchQueriesByContext)
    }

    private fun persistExpandedSessions() {
        savedStateHandle[KEY_EXPANDED_SESSIONS] = HashMap(
            expandedSessionIdsByContext.mapValues { (_, value) -> ArrayList(value) },
        )
    }

    private fun contextKey(directory: String?): String = directory ?: GLOBAL_CONTEXT_KEY

    init {
        val retained = retainedContexts(searchQueriesByContext.keys + expandedSessionIdsByContext.keys).toSet()
        searchQueriesByContext.keys.retainAll(retained)
        expandedSessionIdsByContext.keys.retainAll(retained)
        contextRecency.retainAll(retained)
        retained.forEach { contextRecency.add(it) }
        persistSearchQueries()
        persistExpandedSessions()
        savedStateHandle[KEY_CONTEXT_RECENCY] = ArrayList(contextRecency)

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
                            .map { workspaceSession ->
                                workspaceSession.session.toSessionWithProject(
                                    snapshot.projects
                                )
                            }
                            .sortedByDescending { it.session.updatedAt },
                        projects = snapshot.projects.map(::toProjectInfo).sortedByDescending { it.worktree },
                        sessionStatuses = snapshot.statuses,
                        sessionPresences = snapshot.statuses.mapValues { (_, status) ->
                            resolveSessionPresence(
                                status
                            )
                        },
                        searchResults = if (state.searchQuery.isBlank()) emptyList() else state.searchResults,
                        error = if (repoState is RepoState.Stale) {
                            "Could not refresh sessions. Showing the last loaded sessions."
                        } else {
                            state.error
                        },
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
                                .map { workspaceSession ->
                                    workspaceSession.session.toSessionWithProject(
                                        snapshot.projects
                                    )
                                }
                                .sortedByDescending { session -> session.session.updatedAt },
                            projects = snapshot.projects.map(
                                ::toProjectInfo
                            ).sortedByDescending { project -> project.worktree },
                            sessionStatuses = snapshot.statuses,
                            sessionPresences = snapshot.statuses.mapValues { (_, status) ->
                                resolveSessionPresence(
                                    status
                                )
                            },
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
                            error = "Could not load sessions. Check the connection and try again."
                        )
                    }
                },
            )
        }
    }

    fun updateSearchQuery(query: String, directory: String?) {
        val key = contextKey(directory)
        val boundedQuery = boundedQuery(query)
        touchContext(key)
        if (isPersistableContext(key)) searchQueriesByContext[key] = boundedQuery
        persistSearchQueries()
        persistExpandedSessions()
        _uiState.update { state ->
            state.copy(
                searchQuery = boundedQuery,
                searchDirectory = directory,
                searchError = null,
                searchResults = if (boundedQuery.isBlank()) emptyList() else state.searchResults,
            )
        }
        searchSessions(boundedQuery, directory, debounce = true)
    }

    fun updateSearchDirectory(directory: String?) {
        val key = contextKey(directory)
        touchContext(key)
        persistSearchQueries()
        persistExpandedSessions()
        val restoredQuery = searchQueriesByContext[key].orEmpty()
        val restoredExpanded = expandedSessionIdsByContext[key].orEmpty()
        _uiState.update {
            it.copy(
                searchDirectory = directory,
                searchQuery = restoredQuery,
                searchResults = emptyList(),
                serverSearchQuery = null,
                searchError = null,
                expandedSessionIds = restoredExpanded,
            )
        }
        if (restoredQuery.isNotBlank()) {
            searchSessions(restoredQuery, directory, debounce = false)
        }
    }

    fun toggleSessionExpanded(sessionId: String) {
        val key = contextKey(_uiState.value.searchDirectory)
        touchContext(key)
        val current = _uiState.value.expandedSessionIds
        val next = when {
            sessionId in current -> current - sessionId
            !isPersistableSessionId(sessionId) -> current
            else -> (current.toList() + listOf(sessionId))
                .takeLast(MAX_EXPANDED_SESSION_IDS_PER_CONTEXT)
                .toCollection(LinkedHashSet())
        }
        if (isPersistableContext(key)) expandedSessionIdsByContext[key] = next
        persistSearchQueries()
        persistExpandedSessions()
        _uiState.update { it.copy(expandedSessionIds = next) }
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
            val result = runCatching {
                if (directory == null) {
                    sessionRepository.searchSessionsGlobally(trimmed)
                } else {
                    sessionRepository.searchSessionsInWorkspace(trimmed, directory)
                }
            }
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
                                searchError = "Could not search sessions. Check the connection and try again.",
                            )
                        } else {
                            state
                        }
                    }
                },
            )
        }
    }

    fun createSession(title: String?, directory: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingText = "Creating session", error = null) }
            try {
                if (directory != null && directory != sessionRepository.workspace.directory) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingText = null,
                            loadingProgress = null,
                            loadingCounts = null,
                            error = "Switch to $directory before creating a session"
                        )
                    }
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
                AppLog.e(TAG, "Failed to create session")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingText = null,
                        loadingProgress = null,
                        loadingCounts = null,
                        error = "Could not create the session. Try again."
                    )
                }
            }
        }
    }

    fun forkSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    forkingSessionIds = it.forkingSessionIds + sessionId,
                    error = null,
                )
            }
            try {
                val forked = sessionRepository.forkSession(SessionId(sessionId))
                _uiState.update {
                    it.copy(
                        newSessionId = forked.id.value,
                        newSessionDirectory = forked.session.directory,
                        forkingSessionIds = it.forkingSessionIds - sessionId,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to fork session")
                _uiState.update { it.copy(error = "Could not fork the session. Try again.") }
            } finally {
                _uiState.update {
                    it.copy(forkingSessionIds = it.forkingSessionIds - sessionId)
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
                AppLog.e(TAG, "Failed to delete session")
                _uiState.update { it.copy(error = "Could not delete the session. Try again.") }
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
                AppLog.e(TAG, "Failed to rename session")
                _uiState.update { it.copy(error = "Could not rename the session. Try again.") }
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
                AppLog.e(TAG, "Failed to share session")
                _uiState.update { it.copy(error = "Could not share the session. Try again.") }
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
                AppLog.e(TAG, "Failed to unshare session")
                _uiState.update { it.copy(error = "Could not stop sharing the session. Try again.") }
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
                AppLog.e(TAG, "Failed to summarize session")
                _uiState.update { it.copy(error = "Could not summarize the session. Try again.") }
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
    val expandedSessionIds: Set<String> = emptySet(),
    val forkingSessionIds: Set<String> = emptySet(),
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
