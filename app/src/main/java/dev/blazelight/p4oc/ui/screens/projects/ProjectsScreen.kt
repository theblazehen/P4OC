package dev.blazelight.p4oc.ui.screens.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.remote.dto.ProjectDto
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

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
                onNavigateBack = onNavigateBack
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
                        TuiButton(onClick = { viewModel.loadProjects() }) {
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
                    EmptyProjectsMessage(staleProjectCount = uiState.staleProjectCount)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(uiState.projects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            onClick = {
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
private fun EmptyProjectsMessage(staleProjectCount: Int) {
    val theme = LocalOpenCodeTheme.current
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
            text = if (staleProjectCount > 0) {
                "$staleProjectCount stale project entries were hidden automatically."
            } else {
                stringResource(R.string.projects_appear_here)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    project: ProjectDto,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val projectName = project.worktree.substringAfterLast("/")
    val projectPath = project.worktree
    val createdDate = remember(project.time.created) {
        val instant = Instant.fromEpochMilliseconds(project.time.created)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${localDateTime.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} " +
            localDateTime.dayOfMonth.toString().padStart(2, '0')
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                role = Role.Button
            )
            .testTag("project_card_${project.id}"),
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
            ProjectDetails(
                name = projectName,
                path = projectPath,
                createdDate = createdDate,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
                color = theme.textMuted
            )
        }
    }
}

@Composable
private fun ProjectDetails(
    name: String,
    path: String,
    createdDate: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    Column(modifier = modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = theme.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text(
                text = path,
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
}
