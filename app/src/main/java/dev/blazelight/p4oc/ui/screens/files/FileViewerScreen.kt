package dev.blazelight.p4oc.ui.screens.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatListNumberedRtl
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiOutlinedButton
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.chat.InlineDiffViewer
import dev.blazelight.p4oc.ui.components.code.SyntaxHighlightedCode
import dev.blazelight.p4oc.ui.diff.UnifiedDiffBuilder
import dev.blazelight.p4oc.ui.screens.files.editor.SoraCodeEditorView
import dev.blazelight.p4oc.ui.screens.files.editor.SoraLanguageRegistry
import dev.blazelight.p4oc.ui.screens.files.editor.displayLabelForScope
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize

/** Standard editor body font size; matches inline diff/code blocks. */
private val EDITOR_FONT_SP: Float = TuiCodeFontSize.xxl.value

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    path: String,
    viewModel: FilesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    var showLineNumbers by remember { mutableStateOf(true) }
    var editMode by remember { mutableStateOf(false) }
    /** Pending discard prompt; routes to either "exit edit" or "navigate back". */
    var pendingDiscard by remember { mutableStateOf<DiscardIntent?>(null) }

    LaunchedEffect(path) {
        viewModel.loadFileContent(path)
    }

    val filename = path.substringAfterLast("/")
    val languageLabel = remember(filename) {
        displayLabelForScope(SoraLanguageRegistry.scopeFor(filename))
    }
    val theme = LocalOpenCodeTheme.current

    val isDirty = editState.isDirty && editMode

    BackHandler(enabled = isDirty) { pendingDiscard = DiscardIntent.NavigateBack }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = "",
                onNavigateBack = {
                    if (isDirty) {
                        pendingDiscard = DiscardIntent.NavigateBack
                    } else {
                        onNavigateBack()
                    }
                },
                titleContent = {
                    Column {
                        Text(
                            text = (if (isDirty) "● " else "") + filename,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = languageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLineNumbers = !showLineNumbers },
                        modifier = Modifier
                            .size(Sizing.iconButtonMd)
                            .testTag("file_viewer_toggle_line_numbers")
                    ) {
                        Icon(
                            imageVector = if (showLineNumbers)
                                Icons.Default.FormatListNumbered
                            else
                                Icons.Default.FormatListNumberedRtl,
                            contentDescription = stringResource(R.string.cd_toggle_line_numbers),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                    if (!editMode) {
                        IconButton(
                            onClick = { editMode = true },
                            modifier = Modifier
                                .size(Sizing.iconButtonMd)
                                .testTag("file_viewer_enter_edit")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.cd_edit_file),
                                modifier = Modifier.size(Sizing.iconAction)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.requestSave() },
                            enabled = editState.isDirty && !editState.isSaving,
                            modifier = Modifier
                                .size(Sizing.iconButtonMd)
                                .testTag("file_viewer_save")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = stringResource(R.string.cd_save_file),
                                modifier = Modifier.size(Sizing.iconAction)
                            )
                        }
                        IconButton(
                            onClick = {
                                if (editState.isDirty) {
                                    pendingDiscard = DiscardIntent.ExitEdit
                                } else {
                                    editMode = false
                                }
                            },
                            modifier = Modifier
                                .size(Sizing.iconButtonMd)
                                .testTag("file_viewer_exit_edit")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_view_file),
                                modifier = Modifier.size(Sizing.iconAction)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val fileContent = uiState.fileContent
        val error = uiState.error

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    TuiLoadingScreen(modifier = Modifier.align(Alignment.Center))
                }
                fileContent != null && editMode -> {
                    SoraCodeEditorView(
                        initialContent = editState.originalContent,
                        contentGeneration = editState.contentGeneration,
                        showLineNumbers = showLineNumbers,
                        editable = true,
                        textSizeSp = EDITOR_FONT_SP,
                        filename = filename,
                        onTextChange = { viewModel.onEditorTextChange(it) },
                        modifier = Modifier.fillMaxSize(),
                        testTag = "file_viewer_editor"
                    )
                }
                fileContent != null -> {
                    SyntaxHighlightedCode(
                        code = fileContent,
                        filename = filename,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.md),
                        showLineNumbers = showLineNumbers,
                        selectable = true
                    )
                }
                error != null -> {
                    Text(
                        text = error,
                        modifier = Modifier.align(Alignment.Center),
                        color = theme.error
                    )
                }
            }

            editState.saveError?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.md),
                    action = {
                        TuiTextButton(onClick = { viewModel.clearSaveError() }) {
                            Text(stringResource(R.string.button_cancel))
                        }
                    }
                ) {
                    Text(stringResource(R.string.file_editor_save_failed, msg))
                }
            }
        }
    }

    editState.pendingSavePreview?.let { preview ->
        SaveDiffDialog(
            preview = preview,
            isSaving = editState.isSaving,
            onConfirm = { viewModel.confirmSave() },
            onDismiss = { viewModel.dismissSavePreview() }
        )
    }

    editState.conflict?.let { conflict ->
        ConflictDialog(
            message = conflict.message,
            onReload = { viewModel.reloadFromServer() },
            onOverwrite = { viewModel.overwriteAnyway() },
            onDismiss = { viewModel.dismissConflict() }
        )
    }

    pendingDiscard?.let { intent ->
        DiscardChangesDialog(
            onConfirm = {
                pendingDiscard = null
                viewModel.discardEdits()
                when (intent) {
                    DiscardIntent.ExitEdit -> {
                        editMode = false
                    }
                    DiscardIntent.NavigateBack -> {
                        editMode = false
                        onNavigateBack()
                    }
                }
            },
            onDismiss = { pendingDiscard = null }
        )
    }
}

private enum class DiscardIntent { ExitEdit, NavigateBack }

@Composable
private fun SaveDiffDialog(
    preview: SavePreview,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val filename = preview.path.substringAfterLast("/")
    val diffContent = remember(preview) {
        UnifiedDiffBuilder.build(filename, preview.before, preview.after)
    }
    val counts = remember(preview) { UnifiedDiffBuilder.counts(preview.before, preview.after) }

    TuiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.file_editor_save_diff_title),
        modifier = Modifier.testTag("file_editor_save_dialog"),
        confirmButton = {
            TuiButton(onClick = onConfirm, enabled = !isSaving) {
                Text(stringResource(R.string.file_editor_save))
            }
        },
        dismissButton = {
            TuiOutlinedButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.file_editor_cancel))
            }
        }
    ) {
        if (diffContent.isBlank()) {
            Text(
                text = stringResource(R.string.file_editor_save_no_changes),
                color = LocalOpenCodeTheme.current.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Box(modifier = Modifier.heightIn(max = Sizing.embeddedScrollMaxHeight)) {
                InlineDiffViewer(
                    fileName = filename,
                    diffContent = diffContent,
                    additions = counts.additions,
                    deletions = counts.deletions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ConflictDialog(
    message: String,
    onReload: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    TuiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.file_editor_conflict_title),
        modifier = Modifier.testTag("file_editor_conflict_dialog"),
        confirmButton = {
            TuiButton(onClick = onOverwrite) {
                Text(stringResource(R.string.file_editor_overwrite))
            }
        },
        dismissButton = {
            TuiOutlinedButton(onClick = onReload) {
                Text(stringResource(R.string.file_editor_reload))
            }
        }
    ) {
        Text(
            text = stringResource(R.string.file_editor_conflict_body),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted,
        )
        if (message.isNotBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted,
            )
        }
    }
}

@Composable
private fun DiscardChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TuiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.file_editor_unsaved_title),
        modifier = Modifier.testTag("file_editor_discard_dialog"),
        confirmButton = {
            TuiButton(onClick = onConfirm) {
                Text(stringResource(R.string.file_editor_discard))
            }
        },
        dismissButton = {
            TuiOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.file_editor_cancel))
            }
        }
    ) {
        Text(
            text = stringResource(R.string.file_editor_unsaved_body),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalOpenCodeTheme.current.textMuted,
        )
    }
}
