package dev.blazelight.p4oc.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ServerConnectionRegistry
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.domain.model.OpenCodeEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val projects: List<ProjectDto> = emptyList(),
    val staleProjectCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

@OptIn(FlowPreview::class)
class ProjectsViewModel constructor(
    private val workspaceClient: WorkspaceClient,
    serverConnectionRegistry: ServerConnectionRegistry,
) : ViewModel() {

    companion object {
        private const val EVENT_REFRESH_DEBOUNCE_MS = 150L
    }

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
        viewModelScope.launch {
            serverConnectionRegistry.events(workspaceClient.workspace.server)
                .filter { scopedEvent ->
                    scopedEvent.serverRef == workspaceClient.workspace.server &&
                        scopedEvent.generation == workspaceClient.generation &&
                        scopedEvent.workspaceKey == workspaceClient.workspace.key
                }
                .map { it.event }
                .filter { event ->
                    event is OpenCodeEvent.ProjectUpdated ||
                        event is OpenCodeEvent.ProjectDirectoriesUpdated ||
                        event is OpenCodeEvent.ProjectRefreshRequested
                }
                .debounce(EVENT_REFRESH_DEBOUNCE_MS)
                .collect { refreshProjects() }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            refreshProjects()
        }
    }

    private suspend fun refreshProjects() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val result = safeApiCall { workspaceClient.listProjects() }
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

    private suspend fun filterAccessibleProjects(projects: List<ProjectDto>): List<ProjectDto> {
        val projectDirectories = projects.mapTo(hashSetOf()) { it.worktree }
        return coroutineScope {
            projects.map { project ->
                async {
                    check(project.worktree in projectDirectories)
                    val isAccessible = safeApiCall {
                        workspaceClient.listProjectFiles(project.worktree)
                    } is ApiResult.Success
                    project.takeIf { isAccessible }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
