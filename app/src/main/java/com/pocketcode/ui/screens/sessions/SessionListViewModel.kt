package com.pocketcode.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.core.network.safeApiCall
import com.pocketcode.data.remote.dto.CreateSessionRequest
import com.pocketcode.data.remote.mapper.SessionMapper
import com.pocketcode.domain.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val api: OpenCodeApi,
    private val sessionMapper: SessionMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    fun refresh() {
        loadSessions()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = safeApiCall { api.listSessions() }

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

    fun createSession(title: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = safeApiCall { 
                api.createSession(CreateSessionRequest(title = title)) 
            }

            when (result) {
                is ApiResult.Success -> {
                    val session = sessionMapper.mapToDomain(result.data)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            sessions = listOf(session) + state.sessions,
                            newSessionId = session.id
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

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val result = safeApiCall { api.deleteSession(sessionId) }

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
        _uiState.update { it.copy(newSessionId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class SessionListUiState(
    val isLoading: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val newSessionId: String? = null,
    val error: String? = null
)
