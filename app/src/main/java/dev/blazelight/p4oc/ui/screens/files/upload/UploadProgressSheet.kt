package dev.blazelight.p4oc.ui.screens.files.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadProgressSheet(
    state: UploadQueueState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetryFailed: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current

    ModalBottomSheet(
        onDismissRequest = if (state.isActive) onCancel else onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = theme.background,
        shape = RectangleShape,
        dragHandle = null,
        modifier = Modifier.testTag("upload_progress_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Surface(color = theme.backgroundElement, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        if (state.isActive) R.string.upload_sheet_title else R.string.upload_sheet_done_title
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }

            val current = state.current
            if (state.isActive && current != null) {
                val sent = if (current.phase is UploadPhase.Done) current.bytesTotal else 0L
                Text(
                    text = stringResource(
                        R.string.upload_sheet_progress,
                        state.currentIndex + 1,
                        state.total,
                        current.displayName,
                        formatFileSize(sent),
                        formatFileSize(current.bytesTotal),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = theme.text,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { ((state.currentIndex).toFloat() / state.total.coerceAtLeast(1)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    color = theme.accent,
                    trackColor = theme.backgroundElement,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.upload_sheet_summary,
                        state.successes.size,
                        state.failures.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                items(state.items, key = { it.sourceId }) { item ->
                    UploadItemRow(item)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isActive) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("upload_sheet_cancel"),
                    ) {
                        Text(
                            text = stringResource(R.string.upload_sheet_cancel),
                            color = theme.text,
                        )
                    }
                } else {
                    if (state.failures.isNotEmpty()) {
                        TextButton(
                            onClick = onRetryFailed,
                            modifier = Modifier.testTag("upload_sheet_retry"),
                        ) {
                            Text(
                                text = stringResource(R.string.upload_sheet_retry_failed),
                                color = theme.warning,
                            )
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("upload_sheet_dismiss"),
                    ) {
                        Text(
                            text = stringResource(R.string.upload_sheet_dismiss),
                            color = theme.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadItemRow(item: UploadItem) {
    val theme = LocalOpenCodeTheme.current
    val (label, color) = when (val phase = item.phase) {
        UploadPhase.Pending -> stringResource(R.string.upload_phase_pending) to theme.textMuted
        UploadPhase.Reading -> stringResource(R.string.upload_phase_reading) to theme.info
        UploadPhase.Uploading -> stringResource(R.string.upload_phase_uploading) to theme.accent
        UploadPhase.Done -> stringResource(R.string.upload_phase_done) to theme.success
        is UploadPhase.Failed -> (phase.message.ifBlank { stringResource(R.string.upload_phase_failed) }) to theme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = getFileSymbol(item.displayName),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = theme.textMuted,
        )
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = getMimeTypeLabel(item.mimeType),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = theme.textMuted,
        )
        Text(
            text = formatFileSize(item.bytesTotal),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted,
        )
        Text(
            text = "[$label]",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
