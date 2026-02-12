package dev.blazelight.p4oc.ui.screens.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.DirectoryManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.ui.graphics.RectangleShape
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiTopBar

data class ProjectsUiState(
    val projects: List<ProjectDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)


class ProjectsViewModel constructor(
    private val connectionManager: ConnectionManager,
    private val directoryManager: DirectoryManager
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
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            projects = result.data.sortedByDescending { p -> p.time.created }
                        ) 
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    /**
     * Select a project and persist the directory.
     */
    fun selectProject(worktree: String) {
        viewModelScope.launch {
            directoryManager.setDirectoryAndPersist(worktree)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel = koinViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    onProjectClick: (projectId: String, worktree: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.projects_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.loadProjects() },
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), modifier = Modifier.size(Sizing.iconAction))
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                TuiLoadingScreen(
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = "✗",
                            style = MaterialTheme.typography.displayMedium,
                            color = theme.error
                        )
                        Text(
                            text = uiState.error ?: "Unknown error",
                            color = theme.error
                        )
                        Button(onClick = { viewModel.loadProjects() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            uiState.projects.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = "◇",
                            style = MaterialTheme.typography.displayMedium,
                            color = theme.textMuted
                        )
                        Text(
                            text = stringResource(R.string.projects_no_projects),
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.text
                        )
                        Text(
                            text = stringResource(R.string.projects_appear_here),
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textMuted
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(uiState.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { 
                                viewModel.selectProject(project.worktree)
                                onProjectClick(project.id, project.worktree) 
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectDto,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val projectName = project.worktree.substringAfterLast("/")
    val projectPath = project.worktree
    val createdDate = remember(project.time.created) {
        val instant = Instant.fromEpochMilliseconds(project.time.created)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${localDateTime.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.mdLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text(
                text = "▤",
                style = MaterialTheme.typography.titleLarge,
                color = theme.accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Text(
                        text = projectPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = createdDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted
                    )
                }
            }
            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
                color = theme.textMuted
            )
        }
    }
}
