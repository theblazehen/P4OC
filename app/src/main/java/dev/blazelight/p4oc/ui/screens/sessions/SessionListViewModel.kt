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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
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
            val result = safeApiCall { api.listSessions(directoryManager.getDirectory()) }

            when (result) {
                is ApiResult.Success -> {
                    val sessions = result.data.map { sessionMapper.mapToDomain(it) }
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            sessions = sessions.sortedByDescending { s -> s.updatedAt }
                        ) 
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { 
                        it.copy(isLoading = false, error = result.message) 
                    }
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
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            sessions = listOf(session) + state.sessions,
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
                        state.copy(sessions = state.sessions.filter { it.id != sessionId })
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
    val sessions: List<Session> = emptyList(),
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
