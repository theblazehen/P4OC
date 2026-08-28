package dev.blazelight.p4oc.ui.screens.files

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.filetype.FileTypeCategory
import dev.blazelight.p4oc.core.filetype.FileTypeClassifier
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.domain.model.Symbol
import dev.blazelight.p4oc.domain.server.WorkspaceKey
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiConfirmDialog
import dev.blazelight.p4oc.ui.components.TuiInputDialog
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.screens.files.upload.ContentResolverUploadSource
import dev.blazelight.p4oc.ui.screens.files.upload.UploadProgressSheet
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: FilesViewModel,
    workspaceKey: WorkspaceKey?,
    onFileClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSwitchWorkspace: () -> Unit = {},
) {
    val theme = LocalOpenCodeTheme.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val changesState by viewModel.changesState.collectAsStateWithLifecycle()
    val fileSearchResults by viewModel.fileSearchResults.collectAsStateWithLifecycle()
    val symbolResults by viewModel.symbolResults.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val searchQuery = uiState.searchQuery
    val isSearchActive = uiState.isSearchActive
    val isSymbolMode = uiState.isSymbolMode
    val symbolQuery = uiState.symbolQuery
    var createDialog by remember { mutableStateOf<FileCreateKind?>(null) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }

    val context = LocalContext.current
    val uploadSource = remember(context) {
        ContentResolverUploadSource(context.applicationContext.contentResolver)
    }
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.uploadFromSources(uploadSource, uris.map { it.toString() })
        }
    }

    val sortedFiles = remember(uiState.files) {
        uiState.files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    BackHandler(enabled = changesState.isActive) {
        viewModel.exitChanges()
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            Column {
                TuiTopBar(
                    title = "",
                    onNavigateBack = {
                        if (changesState.isActive) {
                            viewModel.exitChanges()
                        } else if (isSearchActive || isSymbolMode) {
                            viewModel.clearFilters()
                        } else if (uiState.currentPath.isNotBlank()) {
                            viewModel.navigateUp()
                        } else {
                            onNavigateBack()
                        }
                    },
                    titleContent = {
                        if (changesState.isActive) {
                            Text(
                                text = stringResource(R.string.files_changes_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = theme.text,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                        } else if (isSymbolMode) {
                            OutlinedTextField(
                                value = symbolQuery,
                                onValueChange = viewModel::updateSymbolQuery,
                                placeholder = {
                                    Text(
                                        stringResource(R.string.symbol_search_hint),
                                        color = theme.textMuted
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = theme.accent,
                                    focusedTextColor = theme.text,
                                    unfocusedTextColor = theme.text
                                )
                            )
                        } else if (isSearchActive) {
                            val searchFieldDescription = stringResource(R.string.files_search_placeholder)
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = {
                                    Text(
                                        stringResource(R.string.files_search_placeholder),
                                        color = theme.textMuted
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = searchFieldDescription }
                                    .testTag("files_search_field"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = theme.accent,
                                    focusedTextColor = theme.text,
                                    unfocusedTextColor = theme.text
                                )
                            )
                        } else {
                            Column {
                                Text(
                                    text = uiState.currentPath.substringAfterLast('/').ifBlank { "Files" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = theme.text,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val workspaceLabel = when (workspaceKey) {
                                    WorkspaceKey.Global -> stringResource(R.string.files_workspace_global)
                                    is WorkspaceKey.Directory -> workspaceKey.value.trimEnd('/').substringAfterLast('/')
                                        .ifBlank { workspaceKey.value }
                                    is WorkspaceKey.SessionScoped -> workspaceKey.sessionId.value
                                    null -> stringResource(R.string.files_workspace_global)
                                }
                                Text(
                                    text = workspaceLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = theme.textMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clickable(role = Role.Button, onClick = onSwitchWorkspace)
                                )
                            }
                        }
                    },
                    actions = {
                        if (changesState.isActive) {
                            IconButton(
                                onClick = viewModel::refreshChanges,
                                enabled = !changesState.isRefreshing,
                                modifier = Modifier
                                    .size(Sizing.minTouchTarget)
                                    .testTag("files_changes_refresh"),
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.files_changes_refresh),
                                    tint = theme.textMuted,
                                    modifier = Modifier.size(Sizing.iconAction),
                                )
                            }
                            IconButton(
                                onClick = viewModel::exitChanges,
                                modifier = Modifier
                                    .size(Sizing.minTouchTarget)
                                    .testTag("files_changes_exit"),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.files_changes_exit),
                                    tint = theme.textMuted,
                                    modifier = Modifier.size(Sizing.iconAction),
                                )
                            }
                        } else {
                            if (!isSearchActive && !isSymbolMode) {
                                FileCreateMenu(
                                    canCreateFile = uiState.capabilities.canWrite,
                                    canCreateFolder = uiState.capabilities.canCreateDirectory,
                                    onCreateFile = { createDialog = FileCreateKind.File },
                                    onCreateFolder = { createDialog = FileCreateKind.Folder },
                                )
                            }
                            IconButton(
                                onClick = viewModel::enterChanges,
                                modifier = Modifier
                                    .size(Sizing.minTouchTarget)
                                    .testTag("files_changes_action"),
                            ) {
                                Icon(
                                    Icons.Default.Difference,
                                    contentDescription = stringResource(R.string.files_changes_action),
                                    tint = theme.textMuted,
                                    modifier = Modifier.size(Sizing.iconAction),
                                )
                            }
                            if (!isSearchActive && !isSymbolMode) {
                                IconButton(
                                    onClick = { viewModel.setSearchActive(true) },
                                    modifier = Modifier.size(Sizing.iconButtonMd)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.cd_search),
                                        tint = theme.textMuted,
                                        modifier = Modifier.size(Sizing.iconAction)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.setSymbolMode(true) },
                                    modifier = Modifier.size(Sizing.iconButtonMd)
                                ) {
                                    Icon(
                                        Icons.Default.Code,
                                        contentDescription = stringResource(R.string.cd_symbol_search),
                                        tint = theme.textMuted,
                                        modifier = Modifier.size(Sizing.iconAction)
                                    )
                                }
                            }
                        }
                        if (!changesState.isActive && !isSearchActive && !isSymbolMode) {
                            IconButton(
                                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                                enabled = uiState.capabilities.canUpload,
                                modifier = Modifier
                                    .size(Sizing.iconButtonMd)
                                    .testTag("files_upload_action")
                            ) {
                                Icon(
                                    Icons.Default.Upload,
                                    contentDescription = stringResource(R.string.cd_upload_files),
                                    tint = theme.textMuted,
                                    modifier = Modifier.size(Sizing.iconAction)
                                )
                            }
                        }
                        if (!changesState.isActive) {
                            IconButton(
                                onClick = viewModel::refresh,
                                modifier = Modifier.size(Sizing.iconButtonMd)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.cd_refresh),
                                    tint = theme.textMuted,
                                    modifier = Modifier.size(Sizing.iconAction)
                                )
                            }
                        }
                    }
                )

                if (!changesState.isActive && !isSearchActive && uiState.currentPath.isNotBlank()) {
                    BreadcrumbNavigation(
                        path = uiState.currentPath,
                        onNavigateTo = { viewModel.navigateTo(it) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!changesState.isActive && !isSearchActive && uiState.pathRestoreError != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(Spacing.md),
                    color = theme.error.copy(alpha = 0.12f),
                    shape = RectangleShape,
                ) {
                    Text(
                        text = stringResource(R.string.files_restored_path_unavailable),
                        color = theme.error,
                        modifier = Modifier.padding(Spacing.sm),
                    )
                }
            }

            if (changesState.isActive) {
                workspaceChangesContent(
                    state = changesState,
                    onRefresh = viewModel::refreshChanges,
                    onToggle = viewModel::toggleChange,
                    onRetryPatch = viewModel::retrySelectedPatch,
                )
            } else if (isSymbolMode) {
                // Symbol search results
                if (symbolQuery.isBlank()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = stringResource(R.string.symbol_kind_default),
                            style = MaterialTheme.typography.displayMedium,
                            color = theme.textMuted
                        )
                        Text(
                            text = stringResource(R.string.symbol_search_hint),
                            color = theme.textMuted
                        )
                    }
                } else if (uiState.symbolError != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.displayMedium,
                            color = theme.error
                        )
                        Text(
                            text = stringResource(R.string.files_symbol_search_failed),
                            color = theme.error
                        )
                    }
                } else if (symbolResults.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = "∅",
                            style = MaterialTheme.typography.displayMedium,
                            color = theme.textMuted
                        )
                        Text(
                            text = stringResource(R.string.symbol_search_no_results),
                            color = theme.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                    ) {
                        items(symbolResults, key = { "${it.uri}:${it.range.startLine}:${it.name}" }) { symbol ->
                            SymbolResultItem(
                                symbol = symbol,
                                onClick = {
                                    onFileClick(symbol.path)
                                }
                            )
                        }
                    }
                }
            } else if (isSearchActive) {
                FileSearchContent(
                    state = FileSearchContentState(
                        query = searchQuery,
                        results = fileSearchResults,
                        isLoading = uiState.isFileSearchLoading,
                        hasError = uiState.fileSearchError != null,
                    ),
                    onRetry = viewModel::refresh,
                    onFileClick = onFileClick,
                )
            } else {
                when {
                    uiState.isLoading -> {
                        TuiLoadingScreen(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.error != null && uiState.files.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(Spacing.lg)
                                .testTag("files_load_error"),
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
                                text = stringResource(R.string.files_load_failed),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.error,
                            )
                            Text(
                                text = stringResource(R.string.files_load_failed_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.textMuted,
                            )
                            TuiButton(
                                onClick = viewModel::refresh,
                                modifier = Modifier.testTag("files_load_retry"),
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                    sortedFiles.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Text(
                                text = "□",
                                style = MaterialTheme.typography.displayMedium,
                                color = theme.textMuted
                            )
                            Text(text = stringResource(R.string.files_empty_folder), color = theme.textMuted)
                            EmptyFolderActions(
                                canCreateFile = uiState.capabilities.canWrite,
                                canUpload = uiState.capabilities.canUpload,
                                onCreateFile = { createDialog = FileCreateKind.File },
                                onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                            )
                        }
                    }
                    else -> {
                        Column(Modifier.fillMaxSize()) {
                            if (uiState.error != null) {
                                Surface(
                                    color = theme.error.copy(alpha = 0.12f),
                                    shape = RectangleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                        .testTag("files_refresh_error"),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.files_refresh_failed),
                                            color = theme.error,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TuiTextButton(onClick = viewModel::refresh) {
                                            Text(stringResource(R.string.retry))
                                        }
                                    }
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                            ) {
                                items(sortedFiles, key = { it.path }) { file ->
                                    TuiFileItem(
                                        file = file,
                                        actions = FileItemActions(
                                            canRename = uiState.capabilities.canRename,
                                            canDelete = uiState.capabilities.canDelete,
                                            onRename = { renameTarget = file },
                                            onDelete = { deleteTarget = file },
                                        ),
                                        onClick = {
                                            if (file.isDirectory) {
                                                viewModel.navigateTo(file.path)
                                            } else {
                                                onFileClick(file.path)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!changesState.isActive && !uploadState.isEmpty) {
                UploadProgressSheet(
                    state = uploadState,
                    onCancel = { viewModel.cancelUploads() },
                    onDismiss = { viewModel.dismissUploadResult() },
                    onRetryFailed = { viewModel.retryFailedUploads() },
                )
            }

            if (!changesState.isActive && uiState.isMutating) {
                TuiLoadingScreen(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (!changesState.isActive) {
        createDialog?.let { kind ->
            TuiInputDialog(
                onDismissRequest = { createDialog = null },
                onConfirm = { name ->
                    when (kind) {
                        FileCreateKind.File -> viewModel.createFile(name)
                        FileCreateKind.Folder -> viewModel.createFolder(name)
                    }
                },
                title = when (kind) {
                    FileCreateKind.File -> stringResource(R.string.files_new_file)
                    FileCreateKind.Folder -> stringResource(R.string.files_new_folder)
                },
                placeholder = when (kind) {
                    FileCreateKind.File -> stringResource(R.string.files_new_file_placeholder)
                    FileCreateKind.Folder -> stringResource(R.string.files_new_folder_placeholder)
                },
                confirmText = stringResource(R.string.files_create),
            )
        }
    }

    if (!changesState.isActive) {
        renameTarget?.let { file ->
            TuiInputDialog(
                onDismissRequest = { renameTarget = null },
                onConfirm = { name -> viewModel.renameFile(file, name) },
                title = stringResource(R.string.files_rename_title, file.name),
                initialValue = file.name,
                confirmText = stringResource(R.string.files_rename),
            )
        }
    }

    if (!changesState.isActive) {
        deleteTarget?.let { file ->
            TuiConfirmDialog(
                onDismissRequest = { deleteTarget = null },
                onConfirm = { viewModel.deleteFile(file) },
                title = stringResource(R.string.files_delete_title),
                message = stringResource(R.string.files_delete_confirm, file.name),
                confirmText = stringResource(R.string.files_delete),
                isDestructive = true,
            )
        }
    }

    if (!changesState.isActive) {
        uiState.mutationMessage?.let {
            TuiAlertDialog(
                onDismissRequest = viewModel::clearMutationMessage,
                title = stringResource(R.string.files_operation_failed),
                confirmButton = {
                    TuiButton(onClick = viewModel::clearMutationMessage) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            ) {
                Text(stringResource(R.string.files_operation_failed_hint), color = theme.textMuted)
            }
        }
    }
}

private data class FileSearchContentState(
    val query: String,
    val results: List<FileNode>,
    val isLoading: Boolean,
    val hasError: Boolean,
)

@Composable
@Suppress("FunctionNaming")
private fun FileSearchContent(
    state: FileSearchContentState,
    onRetry: () -> Unit,
    onFileClick: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.query.isBlank() -> FileSearchMessage(
                glyph = "⌕",
                text = stringResource(R.string.files_search_blank_hint),
                modifier = Modifier.align(Alignment.Center),
            )
            state.isLoading -> TuiLoadingScreen(
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("files_search_loading"),
                text = stringResource(R.string.files_search_loading),
            )
            state.hasError -> FileSearchFailure(
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
            state.results.isEmpty() -> FileSearchMessage(
                glyph = "∅",
                text = stringResource(R.string.files_search_no_results),
                modifier = Modifier.align(Alignment.Center),
            )
            else -> FileSearchResults(state.results, onFileClick)
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FileSearchFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current

    Column(
        modifier = modifier
            .padding(Spacing.lg)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("files_search_error"),
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
            text = stringResource(R.string.files_search_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.error,
        )
        TuiButton(
            onClick = onRetry,
            modifier = Modifier.testTag("files_search_retry"),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FileSearchResults(
    results: List<FileNode>,
    onFileClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        items(results, key = { it.path }) { file ->
            FileSearchResultItem(
                file = file,
                onClick = { onFileClick(file.path) },
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FileSearchMessage(
    glyph: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current

    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.displayMedium,
            color = theme.textMuted,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted,
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FileSearchResultItem(
    file: FileNode,
    onClick: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    val parentPath = file.path.substringBeforeLast('/', missingDelimiterValue = "")
    val parentLabel = parentPath.ifBlank { stringResource(R.string.files_breadcrumb_root) }
    val itemDescription = stringResource(
        R.string.files_search_result_description,
        file.name,
        parentLabel,
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = itemDescription
                onClick {
                    onClick()
                    true
                }
            }
            .testTag("files_search_result_${file.path}"),
        color = Color.Transparent,
        shape = RectangleShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "■",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = getFileIcon(file).second,
                modifier = Modifier.width(Sizing.iconMd),
            )
            FileSearchResultLabels(
                fileName = file.name,
                parentLabel = parentLabel,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = theme.border,
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FileSearchResultLabels(
    fileName: String,
    parentLabel: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = theme.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = parentLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = theme.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class FileCreateKind { File, Folder }

@Composable
@Suppress("FunctionNaming")
private fun FileCreateMenu(
    canCreateFile: Boolean,
    canCreateFolder: Boolean,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = canCreateFile || canCreateFolder,
            modifier = Modifier
                .size(Sizing.iconButtonMd)
                .testTag("files_create_action")
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.files_create),
                tint = theme.textMuted,
                modifier = Modifier.size(Sizing.iconAction)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_new_file), color = theme.text) },
                enabled = canCreateFile,
                onClick = {
                    expanded = false
                    onCreateFile()
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = null,
                        tint = theme.textMuted
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_new_folder), color = theme.text) },
                enabled = canCreateFolder,
                onClick = {
                    expanded = false
                    onCreateFolder()
                },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = theme.textMuted) }
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun EmptyFolderActions(
    canCreateFile: Boolean,
    canUpload: Boolean,
    onCreateFile: () -> Unit,
    onUpload: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TuiTextButton(onClick = onCreateFile, enabled = canCreateFile) {
            Text(stringResource(R.string.files_new_file))
        }
        TuiTextButton(onClick = onUpload, enabled = canUpload) {
            Text(stringResource(R.string.files_upload_here))
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
internal fun BreadcrumbNavigation(
    path: String,
    onNavigateTo: (String) -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val rootDescription = stringResource(R.string.files_breadcrumb_root)
    val parts = path.split("/").filter { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.backgroundElement)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Root indicator
        Surface(
            onClick = { onNavigateTo("") },
            modifier = Modifier
                .sizeIn(
                    minWidth = Sizing.minTouchTarget,
                    minHeight = Sizing.minTouchTarget,
                )
                .semantics { contentDescription = rootDescription }
                .testTag("files_breadcrumb_root"),
            color = Color.Transparent,
            shape = RectangleShape
        ) {
            Text(
                text = "~",
                style = MaterialTheme.typography.labelMedium,
                color = theme.accent,
                modifier = Modifier.padding(horizontal = Spacing.xs)
            )
        }

        var currentPath = ""
        parts.forEachIndexed { index, part ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelMedium,
                color = theme.textMuted
            )

            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            val pathToNavigate = currentPath

            Surface(
                onClick = { onNavigateTo(pathToNavigate) },
                modifier = Modifier
                    .sizeIn(
                        minWidth = Sizing.minTouchTarget,
                        minHeight = Sizing.minTouchTarget,
                    )
                    .testTag("files_breadcrumb_segment_$index"),
                color = Color.Transparent,
                shape = RectangleShape
            ) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == parts.lastIndex) theme.text else theme.textMuted,
                    fontWeight = if (index == parts.lastIndex) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("FunctionNaming", "LongMethod")
private fun TuiFileItem(
    file: FileNode,
    actions: FileItemActions,
    onClick: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    val iconColor = getFileIcon(file).second
    val gitStatusColor = getGitStatusColor(file.gitStatus)
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var showContextMenu by remember { mutableStateOf(false) }
    val itemType = stringResource(
        if (file.isDirectory) R.string.files_type_folder else R.string.files_type_file,
    )
    val itemDescription = file.gitStatus?.let { status ->
        stringResource(
            R.string.files_item_description_git,
            file.name,
            itemType,
            file.path,
            status,
        )
    } ?: stringResource(
        R.string.files_item_description,
        file.name,
        itemType,
        file.path,
    )
    val renameLabel = stringResource(R.string.files_rename)
    val deleteLabel = stringResource(R.string.files_delete)
    val accessibilityActions = buildList {
        if (actions.canRename) {
            add(
                CustomAccessibilityAction(renameLabel) {
                    actions.onRename()
                    true
                }
            )
        }
        if (actions.canDelete) {
            add(
                CustomAccessibilityAction(deleteLabel) {
                    actions.onDelete()
                    true
                }
            )
        }
    }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    },
                    role = Role.Button
                )
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = itemDescription
                    onClick {
                        onClick()
                        true
                    }
                    customActions = accessibilityActions
                },
            color = Color.Transparent,
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type glyph (design 07): ▸ folders in secondary, ■ files in type color
                Box(
                    modifier = Modifier.width(Sizing.iconMd),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (file.isDirectory) "▸" else "■",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (file.isDirectory) theme.secondary else (gitStatusColor ?: iconColor),
                    )
                }

                // File name
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    color = gitStatusColor ?: theme.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Git status badge
                file.gitStatus?.let { status ->
                    TuiGitStatusBadge(status)
                }

                // Trailing chevron — files open in the viewer
                if (!file.isDirectory) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.border
                    )
                }
            }
        }

        FileItemContextMenu(
            expanded = showContextMenu,
            actions = actions,
            onDismiss = { showContextMenu = false },
            onCopyPath = { clipboardManager.setText(AnnotatedString(file.path)) },
            onCopyName = { clipboardManager.setText(AnnotatedString(file.name)) },
        )
    }
}

private data class FileItemActions(
    val canRename: Boolean,
    val canDelete: Boolean,
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
@Suppress("FunctionNaming")
private fun FileItemContextMenu(
    expanded: Boolean,
    actions: FileItemActions,
    onDismiss: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyName: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_copy_path), color = theme.text) },
            onClick = {
                onCopyPath()
                onDismiss()
            },
            leadingIcon = { FileMenuIcon(Icons.Default.ContentCopy, R.string.files_copy_path) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_copy_name), color = theme.text) },
            onClick = {
                onCopyName()
                onDismiss()
            },
            leadingIcon = { FileMenuIcon(Icons.Default.TextFields, R.string.files_copy_name) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_rename), color = theme.text) },
            enabled = actions.canRename,
            onClick = {
                onDismiss()
                actions.onRename()
            },
            leadingIcon = { FileMenuIcon(Icons.Default.DriveFileRenameOutline, R.string.files_rename) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_delete), color = theme.error) },
            enabled = actions.canDelete,
            onClick = {
                onDismiss()
                actions.onDelete()
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.files_delete),
                    tint = theme.error
                )
            }
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun FileMenuIcon(icon: ImageVector, contentDescription: Int) {
    Icon(
        icon,
        contentDescription = stringResource(contentDescription),
        tint = LocalOpenCodeTheme.current.textMuted,
    )
}

@Composable
private fun TuiGitStatusBadge(status: String) {
    val theme = LocalOpenCodeTheme.current
    val (text, color) = when (status) {
        "added" -> "A" to theme.success
        "modified" -> "M" to theme.warning
        "deleted" -> "D" to theme.error
        else -> status.take(1).uppercase() to theme.textMuted
    }

    Text(
        text = "[$text]",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun getGitStatusColor(status: String?): Color? {
    val theme = LocalOpenCodeTheme.current
    return when (status) {
        "added" -> theme.success
        "modified" -> theme.warning
        "deleted" -> theme.error
        else -> null
    }
}

@Composable
private fun getFileIcon(file: FileNode): Pair<ImageVector, Color> {
    val theme = LocalOpenCodeTheme.current

    if (file.isDirectory) {
        return Icons.Default.Folder to theme.accent
    }

    return when (FileTypeClassifier.classify(file.name).category) {
        FileTypeCategory.Code ->
            Icons.Default.Code to SemanticColors.FileType.code

        FileTypeCategory.Config ->
            Icons.Default.Settings to SemanticColors.FileType.config

        FileTypeCategory.Document ->
            Icons.Default.Description to SemanticColors.FileType.document

        FileTypeCategory.Image ->
            Icons.Default.Image to SemanticColors.FileType.image

        FileTypeCategory.Video ->
            Icons.Default.VideoFile to SemanticColors.FileType.video

        FileTypeCategory.Audio ->
            Icons.Default.AudioFile to SemanticColors.FileType.audio

        FileTypeCategory.Archive ->
            Icons.Default.FolderZip to SemanticColors.FileType.archive

        FileTypeCategory.Shell ->
            Icons.Default.Terminal to SemanticColors.FileType.shell

        FileTypeCategory.Build ->
            Icons.Default.Build to SemanticColors.FileType.build

        FileTypeCategory.Git ->
            Icons.Default.AccountTree to SemanticColors.FileType.git

        FileTypeCategory.Lock ->
            Icons.Default.Lock to SemanticColors.FileType.lock

        FileTypeCategory.Env ->
            Icons.Default.Security to SemanticColors.FileType.env

        FileTypeCategory.Web ->
            Icons.Default.Web to SemanticColors.FileType.web

        FileTypeCategory.Database ->
            Icons.Default.Storage to SemanticColors.FileType.database

        else -> Icons.AutoMirrored.Filled.InsertDriveFile to theme.textMuted
    }
}

@Composable
private fun SymbolResultItem(
    symbol: Symbol,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val (kindLabel, kindColor) = getSymbolKind(symbol.kind)
    // Extract short filename from URI
    val fileName = symbol.uri.substringAfterLast('/')
    val lineNumber = symbol.range.startLine + 1 // Convert from 0-indexed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        color = Color.Transparent,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kind indicator (monospace, colored)
            Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = kindColor,
                fontWeight = FontWeight.Bold
            )

            // Symbol name
            Text(
                text = symbol.name,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // File:line location
            Text(
                text = "$fileName:$lineNumber",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * LSP SymbolKind values → display indicator and color.
 * See: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#symbolKind
 */
@Composable
private fun getSymbolKind(kind: Int): Pair<String, Color> {
    val theme = LocalOpenCodeTheme.current
    return when (kind) {
        1 -> "F" to theme.accent // File
        2 -> "M" to theme.info // Module
        3 -> "N" to theme.info // Namespace
        4 -> "P" to theme.warning // Package
        5 -> "C" to theme.warning // Class
        6 -> "M" to theme.accent // Method
        7 -> "P" to theme.info // Property
        8 -> "F" to theme.textMuted // Field
        9 -> "C" to theme.warning // Constructor
        10 -> "E" to theme.success // Enum
        11 -> "I" to theme.info // Interface
        12 -> "ƒ" to theme.accent // Function
        13 -> "V" to theme.text // Variable
        14 -> "K" to theme.textMuted // Constant
        15 -> "S" to theme.warning // String
        16 -> "#" to theme.info // Number
        17 -> "B" to theme.info // Boolean
        18 -> "A" to theme.warning // Array
        19 -> "O" to theme.warning // Object
        22 -> "E" to theme.success // EnumMember
        23 -> "S" to theme.warning // Struct
        25 -> "O" to theme.info // Operator
        26 -> "T" to theme.warning // TypeParameter
        else -> "◇" to theme.textMuted // Default/unknown
    }
}

// formatFileSize moved to ui.screens.files.upload.UploadVisuals
