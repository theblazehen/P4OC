package dev.blazelight.p4oc.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectDto> = emptyList(),
    val staleProjectCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class ProjectsViewModel constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val api = connectionManager.getApi() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.listProjects() }
            when (result) {
                is ApiResult.Success -> {
                    val projects = result.data.sortedByDescending { p -> p.time.created }
                    val accessibleProjects = filterAccessibleProjects(projects)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            projects = accessibleProjects,
                            staleProjectCount = projects.size - accessibleProjects.size
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    private suspend fun filterAccessibleProjects(projects: List<ProjectDto>): List<ProjectDto> {
        val api = connectionManager.getApi() ?: return projects
        return coroutineScope {
            projects.map { project ->
                async {
                    val isAccessible = safeApiCall {
                        api.listFiles(path = project.worktree, directory = project.worktree)
                    } is ApiResult.Success
                    project.takeIf { isAccessible }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
