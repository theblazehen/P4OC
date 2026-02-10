package dev.blazelight.p4oc.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.mapper.SessionMapper
import dev.blazelight.p4oc.domain.model.Session
import dev.blazelight.p4oc.domain.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope


class SessionListViewModel constructor(
    private val connectionManager: ConnectionManager,
    private val directoryManager: DirectoryManager,
    private val sessionMapper: SessionMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
        loadSessionStatuses()
        loadProjects()
    }

    fun refresh() {
        loadSessions()
        loadSessionStatuses()
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.listProjects() }
            when (result) {
                is ApiResult.Success -> {
                    val projects = result.data.map { dto ->
                        ProjectInfo(
                            id = dto.id,
                            worktree = dto.worktree,
                            name = dto.worktree.substringAfterLast("/")
                        )
                    }.sortedByDescending { it.worktree }
                    _uiState.update { it.copy(projects = projects) }
                }
                is ApiResult.Error -> {}
            }
        }
    }

    private fun loadSessionStatuses() {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.getSessionStatuses(directoryManager.getDirectory()) }
            when (result) {
                is ApiResult.Success -> {
                    val statuses = result.data.mapValues { (_, dto) ->
                        when (dto.type) {
                            "busy" -> SessionStatus.Busy
                            "idle" -> SessionStatus.Idle
                            "retry" -> SessionStatus.Retry(
                                attempt = dto.attempt ?: 0,
                                message = dto.message ?: "",
                                next = dto.next ?: 0L
                            )
                            else -> SessionStatus.Idle
                        }
                    }
                    _uiState.update { it.copy(sessionStatuses = statuses) }
                }
                is ApiResult.Error -> {}
            }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }

            try {
                // First, fetch projects to know what to aggregate
                val projectsResult = safeApiCall { api.listProjects() }
                val projects = when (projectsResult) {
                    is ApiResult.Success -> projectsResult.data.map { dto ->
                        ProjectInfo(
                            id = dto.id,
                            worktree = dto.worktree,
                            name = dto.worktree.substringAfterLast("/")
                        )
                    }
                    is ApiResult.Error -> emptyList()
                }
                
                // Update projects in state
                _uiState.update { it.copy(projects = projects.sortedByDescending { p -> p.worktree }) }

                // Fetch all sessions in parallel: global + each project
                val allSessionsWithProjects = coroutineScope {
                    // Global sessions (no directory filter)
                    val globalDeferred = async {
                        val result = safeApiCall { api.listSessions(directory = null, roots = true, limit = 100) }
                        when (result) {
                            is ApiResult.Success -> result.data.map { dto ->
                                SessionWithProject(
                                    session = sessionMapper.mapToDomain(dto),
                                    projectId = null,
                                    projectName = null
                                )
                            }
                            is ApiResult.Error -> {
                                android.util.Log.e("SessionListVM", "Failed to load global sessions: ${result.message}")
                                emptyList()
                            }
                        }
                    }

                    // Sessions for each project
                    val projectDeferreds = projects.map { project ->
                        async {
                            val result = safeApiCall { api.listSessions(directory = project.worktree, roots = true, limit = 100) }
                            when (result) {
                                is ApiResult.Success -> result.data.map { dto ->
                                    SessionWithProject(
                                        session = sessionMapper.mapToDomain(dto),
                                        projectId = project.id,
                                        projectName = project.name
                                    )
                                }
                                is ApiResult.Error -> {
                                    android.util.Log.e("SessionListVM", "Failed to load sessions for ${project.name}: ${result.message}")
                                    emptyList()
                                }
                            }
                        }
                    }

                    // Await all and merge
                    val globalSessions = globalDeferred.await()
                    val projectSessions = projectDeferreds.awaitAll().flatten()
                    
                    // Deduplicate: project sessions take priority over global (in case of overlap)
                    val projectSessionIds = projectSessions.map { it.session.id }.toSet()
                    val uniqueGlobalSessions = globalSessions.filter { it.session.id !in projectSessionIds }
                    
                    uniqueGlobalSessions + projectSessions
                }

                android.util.Log.d("SessionListVM", "loadSessions: aggregated ${allSessionsWithProjects.size} total sessions")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = allSessionsWithProjects.sortedByDescending { s -> s.session.updatedAt }
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SessionListVM", "loadSessions error", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load sessions: ${e.message}")
                }
            }
        }
    }

    fun createSession(title: String?, directory: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            android.util.Log.d("SessionListVM", "createSession called with title=$title, directory=$directory")

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            android.util.Log.d("SessionListVM", "Calling API createSession with directory=$directory")
            val result = safeApiCall { 
                api.createSession(
                    directory = directory,
                    request = CreateSessionRequest(title = title)
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    val session = sessionMapper.mapToDomain(result.data)
                    
                    // Find project info if directory matches a project
                    val project = _uiState.value.projects.find { it.worktree == directory }
                    val sessionWithProject = SessionWithProject(
                        session = session,
                        projectId = project?.id,
                        projectName = project?.name
                    )
                    
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            sessions = listOf(sessionWithProject) + state.sessions,
                            newSessionId = session.id,
                            newSessionDirectory = session.directory
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(isLoading = false, error = "Failed to create session: ${result.message}") 
                    }
                }
            }
        }
    }

    fun deleteSession(sessionId: String, directory: String? = null) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.deleteSession(sessionId, directory ?: directoryManager.getDirectory()) }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(sessions = state.sessions.filter { it.session.id != sessionId })
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(error = "Failed to delete session: ${result.message}") 
                    }
                }
            }
        }
    }

    fun clearNewSession() {
        _uiState.update { it.copy(newSessionId = null, newSessionDirectory = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class SessionListUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionWithProject> = emptyList(),
    val sessionStatuses: Map<String, SessionStatus> = emptyMap(),
    val projects: List<ProjectInfo> = emptyList(),
    val newSessionId: String? = null,
    val newSessionDirectory: String? = null,
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
