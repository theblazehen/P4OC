package dev.blazelight.p4oc.ui.screens.files

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiShapes

@Composable
internal fun workspaceChangePatch(
    path: String,
    state: WorkspaceChangesPatchState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state.pathForSelection(path) != path) return
    val theme = LocalOpenCodeTheme.current
    Surface(
        color = theme.background,
        shape = TuiShapes.extraSmall,
        border = BorderStroke(Sizing.strokeThin, theme.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("files_changes_patch_$path"),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.files_changes_patch_heading, path),
                style = MaterialTheme.typography.labelMedium,
                color = theme.text,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag("files_changes_patch_heading_$path"),
            )
            workspacePatchBody(state, onRefresh, onRetry)
        }
    }
}

private fun WorkspaceChangesPatchState.pathForSelection(selectedPath: String): String = when (this) {
    WorkspaceChangesPatchState.None,
    WorkspaceChangesPatchState.Loading -> selectedPath
    is WorkspaceChangesPatchState.Content -> path
    is WorkspaceChangesPatchState.Unsupported -> path
    is WorkspaceChangesPatchState.Unavailable -> path
    is WorkspaceChangesPatchState.TooLarge -> path
    is WorkspaceChangesPatchState.Malformed -> path
    is WorkspaceChangesPatchState.Stale -> path
    is WorkspaceChangesPatchState.AuthorizationFailure -> path
    is WorkspaceChangesPatchState.HttpFailure -> path
    is WorkspaceChangesPatchState.NetworkFailure -> path
    is WorkspaceChangesPatchState.Failure -> path
}

@Composable
private fun workspacePatchBody(
    state: WorkspaceChangesPatchState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        WorkspaceChangesPatchState.None,
        WorkspaceChangesPatchState.Loading -> workspacePatchMessage(
            text = stringResource(R.string.files_changes_patch_loading),
            isFailure = false,
        )
        is WorkspaceChangesPatchState.Content -> workspacePatchText(state.text)
        is WorkspaceChangesPatchState.Unsupported -> workspacePatchMessage(
            text = stringResource(R.string.files_changes_patch_unsupported),
            isFailure = true,
        )
        is WorkspaceChangesPatchState.Unavailable -> workspacePatchMessage(
            text = stringResource(R.string.files_changes_patch_unavailable),
            isFailure = true,
        )
        is WorkspaceChangesPatchState.TooLarge -> workspacePatchMessage(
            text = stringResource(R.string.files_changes_patch_too_large),
            isFailure = true,
        )
        is WorkspaceChangesPatchState.Malformed ->
            workspaceRetryablePatchMessage(R.string.files_changes_patch_malformed, onRetry)
        is WorkspaceChangesPatchState.Stale -> workspaceRetryablePatchMessage(
            message = R.string.files_changes_patch_stale,
            onClick = onRefresh,
            actionLabel = R.string.files_changes_refresh,
        )
        is WorkspaceChangesPatchState.AuthorizationFailure ->
            workspaceRetryablePatchMessage(R.string.files_changes_patch_authorization_failed, onRetry)
        is WorkspaceChangesPatchState.HttpFailure ->
            workspaceRetryablePatchMessage(R.string.files_changes_patch_http_failed, onRetry)
        is WorkspaceChangesPatchState.NetworkFailure ->
            workspaceRetryablePatchMessage(R.string.files_changes_patch_network_failed, onRetry)
        is WorkspaceChangesPatchState.Failure ->
            workspaceRetryablePatchMessage(R.string.files_changes_patch_failed, onRetry)
    }
}

@Composable
private fun workspaceRetryablePatchMessage(
    @StringRes message: Int,
    onClick: () -> Unit,
    @StringRes actionLabel: Int = R.string.retry,
) {
    workspacePatchMessage(
        text = stringResource(message),
        isFailure = true,
        action = {
            TuiTextButton(
                onClick = onClick,
                modifier = Modifier.testTag("files_changes_retry"),
            ) {
                Text(stringResource(actionLabel))
            }
        },
    )
}

@Composable
private fun workspacePatchText(text: String) {
    val theme = LocalOpenCodeTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = Sizing.embeddedScrollMaxHeight)
            .background(theme.backgroundElement, TuiShapes.extraSmall)
            .border(Sizing.strokeThin, theme.border, TuiShapes.extraSmall)
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState())
            .padding(Spacing.sm),
    ) {
        SelectionContainer {
            Text(
                text = text,
                color = theme.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun workspacePatchMessage(
    text: String,
    isFailure: Boolean,
    action: @Composable (() -> Unit)? = null,
) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = text,
            color = if (isFailure) theme.error else theme.textMuted,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
internal fun workspaceChangesFailure(
    failure: WorkspaceChangesFailureKind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    val message = when (failure) {
        WorkspaceChangesFailureKind.Unsupported -> stringResource(R.string.files_changes_unsupported)
        WorkspaceChangesFailureKind.TooLarge -> stringResource(R.string.files_changes_too_large)
        WorkspaceChangesFailureKind.Malformed -> stringResource(R.string.files_changes_malformed)
        WorkspaceChangesFailureKind.Stale -> stringResource(R.string.files_changes_stale)
        WorkspaceChangesFailureKind.AuthorizationFailure ->
            stringResource(R.string.files_changes_authorization_failed)
        WorkspaceChangesFailureKind.HttpFailure -> stringResource(R.string.files_changes_http_failed)
        WorkspaceChangesFailureKind.NetworkFailure -> stringResource(R.string.files_changes_network_failed)
        WorkspaceChangesFailureKind.Failure -> stringResource(R.string.files_changes_failed)
    }
    val canRetry = failure != WorkspaceChangesFailureKind.Unsupported &&
        failure != WorkspaceChangesFailureKind.TooLarge
    Column(
        modifier = modifier
            .padding(Spacing.lg)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("files_changes_failure"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = theme.error,
            modifier = Modifier.size(Sizing.iconLg),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.text,
        )
        if (canRetry) {
            TuiButton(
                onClick = onRetry,
                modifier = Modifier.testTag("files_changes_retry"),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
