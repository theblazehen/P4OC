package dev.blazelight.p4oc.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.log.AppLog
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.SessionDataCache
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.CreateSessionRequest
import dev.blazelight.p4oc.data.remote.dto.UpdateSessionRequest
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_STATUS_CONCURRENCY = 10

class SessionListViewModel constructor(
    private val connectionManager: ConnectionManager,
    private val directoryManager: DirectoryManager,
    private val sessionDataCache: SessionDataCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        loadFromCacheOrFallback()
    }

    fun refresh() {
        sessionDataCache.invalidate()
        loadFromCacheOrFallback()
    }

    private fun loadFromCacheOrFallback() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            sessionDataCache.awaitOrFetch().fold(
                onSuccess = { cached ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessions = cached.sessions,
                            projects = cached.projects,
                            sessionStatuses = cached.statuses,
                        )
                    }
                },
                onFailure = { error ->
                    AppLog.w("SessionListVM", "SessionDataCache failed, falling back to direct loads: ${error.message}", error)
                    loadSessionsFallback()
                },
            )
        }
    }

    private suspend fun loadProjectsFallback(): List<ProjectInfo> {
        val api = connectionManager.getApi() ?: return emptyList()
        val result = safeApiCall { api.listProjects() }
        return when (result) {
            is ApiResult.Success -> result.data.map { dto ->
                ProjectInfo(
                    id = dto.id,
                    worktree = dto.worktree,
                    name = dto.worktree.substringAfterLast("/"),
                )
            }.sortedByDescending { it.worktree }
            is ApiResult.Error -> emptyList()
        }
    }

    private suspend fun loadSessionStatusesFallback(projects: List<ProjectInfo>): Map<String, SessionStatus> {
        val api = connectionManager.getApi() ?: return emptyMap()
        val semaphore = Semaphore(MAX_STATUS_CONCURRENCY)
        val directories = listOf<String?>(null) + projects.map { it.worktree }

        return coroutineScope {
            directories.map { directory ->
                async {
                    semaphore.withPermit {
                        safeApiCall { api.getSessionStatuses(directory) }
                    }
                }
            }.awaitAll().fold(mutableMapOf<String, SessionStatus>()) { acc, result ->
                when (result) {
                    is ApiResult.Success -> {
                        result.data.forEach { (sessionId, dto) ->
                            acc[sessionId] = SessionMapper.mapStatusToDomain(dto)
                        }
                    }
                    is ApiResult.Error -> Unit
                }
                acc
            }
        }
    }

    private fun loadSessionsFallback() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            try {
                val projects = loadProjectsFallback()
                val allSessionsWithProjects = coroutineScope {
                    val globalDeferred = async {
                        val result = safeApiCall { api.listSessions(directory = null, roots = true, limit = 100) }
                        when (result) {
                            is ApiResult.Success -> result.data.map { dto ->
                                SessionWithProject(
                                    session = SessionMapper.mapToDomain(dto),
                                    projectId = null,
                                    projectName = null,
                                )
                            }
                            is ApiResult.Error -> {
                                AppLog.e("SessionListVM", "Failed to load global sessions: ${result.message}")
                                emptyList()
                            }
                        }
                    }

                    val projectDeferreds = projects.map { project ->
                        async {
                            val result = safeApiCall { api.listSessions(directory = project.worktree, roots = true, limit = 100) }
                            when (result) {
                                is ApiResult.Success -> result.data.map { dto ->
                                    SessionWithProject(
                                        session = SessionMapper.mapToDomain(dto),
                                        projectId = project.id,
                                        projectName = project.name,
                                    )
                                }
                                is ApiResult.Error -> {
                                    AppLog.e("SessionListVM", "Failed to load sessions for ${project.name}: ${result.message}")
                                    emptyList()
                                }
                            }
                        }
                    }

                    val globalSessions = globalDeferred.await()
                    val projectSessions = projectDeferreds.awaitAll().flatten()
                    val projectSessionIds = projectSessions.map { it.session.id }.toSet()
                    val uniqueGlobalSessions = globalSessions.filter { it.session.id !in projectSessionIds }

                    uniqueGlobalSessions + projectSessions
                }

                val statuses = loadSessionStatusesFallback(projects)

                AppLog.d("SessionListVM", "loadSessionsFallback: aggregated ${allSessionsWithProjects.size} total sessions")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = allSessionsWithProjects.sortedByDescending { s -> s.session.updatedAt },
                        projects = projects,
                        sessionStatuses = statuses,
                    )
                }
            } catch (e: Exception) {
                AppLog.e("SessionListVM", "loadSessionsFallback error", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load sessions: ${e.message}")
                }
            }
        }
    }

    fun createSession(title: String?, directory: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            AppLog.d("SessionListVM", "createSession called with title=$title, directory=$directory")

            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            AppLog.d("SessionListVM", "Calling API createSession with directory=$directory")
            val result = safeApiCall { 
                api.createSession(
                    directory = directory,
                    request = CreateSessionRequest(title = title)
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    val session = SessionMapper.mapToDomain(result.data)
                    
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

    fun renameSession(sessionId: String, newTitle: String, directory: String? = null) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall {
                api.updateSession(sessionId, UpdateSessionRequest(title = newTitle), directory ?: directoryManager.getDirectory())
            }
            when (result) {
                is ApiResult.Success -> {
                    val updated = SessionMapper.mapToDomain(result.data)
                    _uiState.update { state ->
                        state.copy(sessions = state.sessions.map { swp ->
                            if (swp.session.id == sessionId) swp.copy(session = updated) else swp
                        })
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Failed to rename: ${result.message}") }
                }
            }
        }
    }

    fun shareSession(sessionId: String, directory: String? = null) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.shareSession(sessionId, directory ?: directoryManager.getDirectory()) }
            when (result) {
                is ApiResult.Success -> {
                    val updated = SessionMapper.mapToDomain(result.data)
                    _uiState.update { state ->
                        state.copy(
                            sessions = state.sessions.map { swp ->
                                if (swp.session.id == sessionId) swp.copy(session = updated) else swp
                            },
                            shareUrl = updated.shareUrl
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Failed to share session: ${result.message}") }
                }
            }
        }
    }

    fun unshareSession(sessionId: String, directory: String? = null) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch
            val result = safeApiCall { api.unshareSession(sessionId, directory ?: directoryManager.getDirectory()) }
            when (result) {
                is ApiResult.Success -> {
                    val updated = SessionMapper.mapToDomain(result.data)
                    _uiState.update { state ->
                        state.copy(sessions = state.sessions.map { swp ->
                            if (swp.session.id == sessionId) swp.copy(session = updated) else swp
                        })
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Failed to unshare: ${result.message}") }
                }
            }
        }
    }

    fun clearShareUrl() {
        _uiState.update { it.copy(shareUrl = null) }
    }

    fun summarizeSession(sessionId: String, directory: String? = null) {
        viewModelScope.launch {
            val api = connectionManager.getApi() ?: return@launch

            // Body is optional per SDK — let server use its own default provider/model
            val result = safeApiCall {
                api.summarizeSession(sessionId, directory ?: directoryManager.getDirectory())
            }
            when (result) {
                is ApiResult.Success -> refresh()
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "Failed to summarize: ${result.message}") }
                }
            }
        }
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
