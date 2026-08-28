package dev.blazelight.p4oc.ui.screens.files

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.vcs.WorkspaceChange
import dev.blazelight.p4oc.data.vcs.WorkspaceChangeStatus
import dev.blazelight.p4oc.data.vcs.WorkspaceChangesSnapshot
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiShapes

@Composable
internal fun workspaceChangesContent(
    state: WorkspaceChangesUiState,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onRetryPatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = state.snapshot
    val failure = state.failure
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && snapshot == null -> TuiLoadingScreen(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("files_changes_loading"),
                text = stringResource(R.string.files_changes_loading),
            )
            snapshot == null && failure != null -> workspaceChangesFailure(
                failure = failure,
                onRetry = onRefresh,
                modifier = Modifier.align(Alignment.Center),
            )
            snapshot != null -> workspaceChangesSnapshotContent(
                state = state,
                snapshot = snapshot,
                actions = WorkspaceChangesActions(onRefresh, onToggle, onRetryPatch),
            )
            else -> workspaceChangesFailure(
                failure = WorkspaceChangesFailureKind.Failure,
                onRetry = onRefresh,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun workspaceChangesSnapshotContent(
    state: WorkspaceChangesUiState,
    snapshot: WorkspaceChangesSnapshot,
    actions: WorkspaceChangesActions,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        workspaceChangesRefreshStatus(state, actions.onRefresh)
        workspaceChangesIdentity(snapshot)
        if (snapshot.changes.isEmpty()) {
            workspaceChangesEmpty(actions.onRefresh)
        } else {
            workspaceChangesList(
                state = state,
                snapshot = snapshot,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun workspaceChangesRefreshStatus(state: WorkspaceChangesUiState, onRefresh: () -> Unit) {
    val refreshFailure = state.refreshFailed
    when {
        state.isRefreshing -> workspaceChangesNotice(
            text = stringResource(R.string.files_changes_refreshing),
            isFailure = false,
            testTag = "files_changes_refreshing",
        )
        refreshFailure != null -> workspaceChangesNotice(
            text = workspaceChangesRefreshFailureText(refreshFailure),
            isFailure = true,
            testTag = "files_changes_refresh_failed",
            action = if (
                refreshFailure != WorkspaceChangesFailureKind.Unsupported &&
                refreshFailure != WorkspaceChangesFailureKind.TooLarge
            ) {
                {
                    TuiTextButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("files_changes_retry"),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun workspaceChangesRefreshFailureText(failure: WorkspaceChangesFailureKind): String =
    when (failure) {
        WorkspaceChangesFailureKind.Unsupported ->
            stringResource(R.string.files_changes_refresh_unsupported)
        WorkspaceChangesFailureKind.TooLarge ->
            stringResource(R.string.files_changes_refresh_too_large)
        WorkspaceChangesFailureKind.Malformed ->
            stringResource(R.string.files_changes_refresh_malformed)
        WorkspaceChangesFailureKind.Stale ->
            stringResource(R.string.files_changes_refresh_stale)
        WorkspaceChangesFailureKind.AuthorizationFailure ->
            stringResource(R.string.files_changes_refresh_authorization_failed)
        WorkspaceChangesFailureKind.HttpFailure ->
            stringResource(R.string.files_changes_refresh_http_failed)
        WorkspaceChangesFailureKind.NetworkFailure ->
            stringResource(R.string.files_changes_refresh_network_failed)
        WorkspaceChangesFailureKind.Failure ->
            stringResource(R.string.files_changes_refresh_failed)
    }

@Composable
private fun workspaceChangesNotice(
    text: String,
    isFailure: Boolean,
    testTag: String,
    action: @Composable (() -> Unit)? = null,
) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(testTag),
        color = if (isFailure) theme.error.copy(alpha = 0.12f) else theme.accent.copy(alpha = 0.1f),
        shape = TuiShapes.extraSmall,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = text,
                color = if (isFailure) theme.error else theme.text,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
    }
}

@Composable
private fun workspaceChangesIdentity(snapshot: WorkspaceChangesSnapshot) {
    val theme = LocalOpenCodeTheme.current
    val workspace = snapshot.workspaceDirectory ?: stringResource(R.string.files_changes_global_workspace)
    val currentBranch = snapshot.branch ?: stringResource(R.string.files_changes_branch_unavailable)
    val defaultBranch = snapshot.defaultBranch ?: stringResource(R.string.files_changes_branch_unavailable)
    val targetIdentity = stringResource(R.string.files_changes_target_identity, snapshot.serverLabel, workspace)
    val branchIdentity = stringResource(R.string.files_changes_branch_identity, currentBranch, defaultBranch)
    val summary = stringResource(
        R.string.files_changes_summary,
        snapshot.changes.size,
        snapshot.additions,
        snapshot.deletions,
    )
    Surface(
        color = theme.backgroundElement,
        shape = TuiShapes.extraSmall,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("files_changes_identity")
            .clearAndSetSemantics {
                contentDescription = "$targetIdentity. $branchIdentity. $summary"
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = targetIdentity,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = theme.text,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = branchIdentity,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = theme.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = summary,
                color = theme.accent,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun workspaceChangesEmpty(onRefresh: () -> Unit) {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .testTag("files_changes_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircleOutline,
            contentDescription = null,
            tint = theme.textMuted,
            modifier = Modifier.size(Sizing.iconLg),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.files_changes_empty),
            style = MaterialTheme.typography.titleMedium,
            color = theme.text,
        )
        Text(
            text = stringResource(R.string.files_changes_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        TuiTextButton(onClick = onRefresh) {
            Text(stringResource(R.string.files_changes_refresh))
        }
    }
}

@Composable
private fun workspaceChangesList(
    state: WorkspaceChangesUiState,
    snapshot: WorkspaceChangesSnapshot,
    actions: WorkspaceChangesActions,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("files_changes_list"),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(snapshot.changes, key = { it.file }) { change ->
            workspaceChangeItem(
                change = change,
                isExpanded = state.selectedPath == change.file,
                patchState = state.patchState,
                actions = WorkspaceChangeActions(
                    onToggle = { actions.onToggle(change.file) },
                    onRefresh = actions.onRefresh,
                    onRetryPatch = actions.onRetryPatch,
                ),
            )
        }
    }
}

private data class WorkspaceChangesActions(
    val onRefresh: () -> Unit,
    val onToggle: (String) -> Unit,
    val onRetryPatch: () -> Unit,
)

private data class WorkspaceChangeActions(
    val onToggle: () -> Unit,
    val onRefresh: () -> Unit,
    val onRetryPatch: () -> Unit,
)

private data class WorkspaceChangeRowPresentation(
    val change: WorkspaceChange,
    val statusLabel: String,
    val statusColor: Color,
    val contentDescription: String,
    val toggleLabel: String,
    val stateDescription: String,
    val isExpanded: Boolean,
)

@Composable
private fun workspaceChangeItem(
    change: WorkspaceChange,
    isExpanded: Boolean,
    patchState: WorkspaceChangesPatchState,
    actions: WorkspaceChangeActions,
) {
    val statusLabel = when (change.status) {
        WorkspaceChangeStatus.Added -> stringResource(R.string.files_changes_status_added)
        WorkspaceChangeStatus.Modified -> stringResource(R.string.files_changes_status_modified)
        WorkspaceChangeStatus.Deleted -> stringResource(R.string.files_changes_status_deleted)
    }
    val statusColor = when (change.status) {
        WorkspaceChangeStatus.Added -> SemanticColors.Git.added
        WorkspaceChangeStatus.Modified -> SemanticColors.Git.modified
        WorkspaceChangeStatus.Deleted -> SemanticColors.Git.deleted
    }
    val presentation = WorkspaceChangeRowPresentation(
        change = change,
        statusLabel = statusLabel,
        statusColor = statusColor,
        contentDescription = stringResource(
            R.string.files_changes_row_description,
            change.file,
            statusLabel,
            change.additions,
            change.deletions,
        ),
        toggleLabel = stringResource(
            if (isExpanded) R.string.files_changes_collapse_patch else R.string.files_changes_expand_patch,
            change.file,
        ),
        stateDescription = stringResource(
            if (isExpanded) R.string.files_changes_state_expanded else R.string.files_changes_state_collapsed,
        ),
        isExpanded = isExpanded,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        workspaceChangeRow(presentation, actions.onToggle)
        if (isExpanded) {
            workspaceChangePatch(
                path = change.file,
                state = patchState,
                onRefresh = actions.onRefresh,
                onRetry = actions.onRetryPatch,
            )
        }
    }
}

@Composable
private fun workspaceChangeRow(presentation: WorkspaceChangeRowPresentation, onToggle: () -> Unit) {
    val theme = LocalOpenCodeTheme.current
    val change = presentation.change
    Surface(
        onClick = onToggle,
        color = if (presentation.isExpanded) theme.backgroundElement else theme.backgroundPanel,
        shape = TuiShapes.extraSmall,
        border = BorderStroke(Sizing.strokeThin, theme.border),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizing.minTouchTarget)
            .testTag("files_changes_row_${change.file}")
            .clearAndSetSemantics {
                contentDescription = presentation.contentDescription
                role = Role.Button
                stateDescription = presentation.stateDescription
                onClick(label = presentation.toggleLabel) {
                    onToggle()
                    true
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = change.file,
                color = theme.text,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = presentation.statusLabel,
                    color = presentation.statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.files_changes_row_stats,
                        change.additions,
                        change.deletions,
                    ),
                    color = theme.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
