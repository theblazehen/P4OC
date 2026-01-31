package dev.blazelight.p4oc.ui.screens.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.blazelight.p4oc.ui.theme.SemanticColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.ui.theme.Sizing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: FilesViewModel = hiltViewModel(),
    onFileClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredFiles = remember(uiState.files, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else {
            uiState.files
                .filter { it.name.contains(searchQuery, ignoreCase = true) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.files_search_placeholder)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                        } else {
                            Text(
                                text = uiState.currentPath.substringAfterLast('/').ifBlank { "Files" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else if (uiState.currentPath.isNotBlank()) {
                                viewModel.navigateUp()
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        if (!isSearchActive) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                            }
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                        }
                    }
                )
                
                if (uiState.currentPath.isNotBlank()) {
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
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                filteredFiles.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.FolderOpen,
                            contentDescription = if (searchQuery.isNotBlank()) "No matching files" else "Empty folder",
                            modifier = Modifier.size(Sizing.iconHero),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching files" else "Empty folder",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredFiles, key = { it.path }) { file ->
                            EnhancedFileItem(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        viewModel.navigateTo(file.path)
                                    } else {
                                        onFileClick(file.path)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbNavigation(
    path: String,
    onNavigateTo: (String) -> Unit
) {
    val parts = path.split("/").filter { it.isNotEmpty() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = { onNavigateTo("") },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RectangleShape
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = stringResource(R.string.cd_root),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        var currentPath = ""
        parts.forEachIndexed { index, part ->
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.cd_path_separator),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            
            currentPath = if (currentPath.isEmpty()) "/$part" else "$currentPath/$part"
            val pathToNavigate = currentPath
            
            Surface(
                onClick = { onNavigateTo(pathToNavigate) },
                color = if (index == parts.lastIndex) 
                    MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RectangleShape
            ) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == parts.lastIndex) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedFileItem(
    file: FileNode,
    onClick: () -> Unit
) {
    val (icon, iconColor) = getFileIcon(file)
    val gitStatusColor = getGitStatusColor(file.gitStatus)
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var showContextMenu by remember { mutableStateOf(false) }
    
    Box {
        ListItem(
            headlineContent = { 
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = file.name,
                        fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                        color = gitStatusColor ?: LocalContentColor.current
                    )
                    file.gitStatus?.let { status ->
                        GitStatusBadge(status)
                    }
                }
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = if (file.isDirectory) "Folder" else "File",
                    tint = gitStatusColor ?: iconColor,
                    modifier = Modifier.size(Sizing.iconLg)
                )
            },
            trailingContent = {
                if (file.isDirectory) {
                    Icon(
                        Icons.Default.ChevronRight, 
                        contentDescription = stringResource(R.string.cd_open_folder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier
                .clip(RectangleShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    }
                )
        )
        
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_copy_path)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(file.path))
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.files_copy_path))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_copy_name)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(file.name))
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.TextFields, contentDescription = stringResource(R.string.files_copy_name))
                }
            )
        }
    }
}

@Composable
private fun GitStatusBadge(status: String) {
    val (text, color) = when (status) {
        "added" -> "A" to SemanticColors.Git.added
        "modified" -> "M" to SemanticColors.Git.modified
        "deleted" -> "D" to SemanticColors.Git.deleted
        else -> status.take(1).uppercase() to MaterialTheme.colorScheme.outline
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RectangleShape
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun getGitStatusColor(status: String?): androidx.compose.ui.graphics.Color? {
    return when (status) {
        "added" -> SemanticColors.Git.added
        "modified" -> SemanticColors.Git.modified
        "deleted" -> SemanticColors.Git.deleted
        else -> null
    }
}

@Composable
private fun getFileIcon(file: FileNode): Pair<ImageVector, androidx.compose.ui.graphics.Color> {
    if (file.isDirectory) {
        return Icons.Default.Folder to MaterialTheme.colorScheme.primary
    }
    
    val extension = file.name.substringAfterLast('.', "").lowercase()
    
    return when (extension) {
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "rs", "go", "rb", "php", "swift", "m" ->
            Icons.Default.Code to SemanticColors.FileType.code
        
        "json", "yaml", "yml", "xml", "toml", "ini", "conf", "config", "properties" ->
            Icons.Default.Settings to SemanticColors.FileType.config
        
        "md", "txt", "rst", "doc", "docx", "pdf" ->
            Icons.Default.Description to SemanticColors.FileType.document
        
        "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp" ->
            Icons.Default.Image to SemanticColors.FileType.image
        
        "mp4", "avi", "mov", "mkv", "webm" ->
            Icons.Default.VideoFile to SemanticColors.FileType.video
        
        "mp3", "wav", "ogg", "flac", "m4a" ->
            Icons.Default.AudioFile to SemanticColors.FileType.audio
        
        "zip", "tar", "gz", "rar", "7z" ->
            Icons.Default.FolderZip to SemanticColors.FileType.archive
        
        "sh", "bash", "zsh", "fish" ->
            Icons.Default.Terminal to SemanticColors.FileType.shell
        
        "gradle", "gradlew" ->
            Icons.Default.Build to SemanticColors.FileType.build
        
        "gitignore", "gitattributes" ->
            Icons.Default.AccountTree to SemanticColors.FileType.git
        
        "lock" ->
            Icons.Default.Lock to SemanticColors.FileType.lock
        
        "env", "envrc" ->
            Icons.Default.Security to SemanticColors.FileType.env
        
        "html", "htm", "css", "scss", "sass", "less" ->
            Icons.Default.Web to SemanticColors.FileType.web
        
        "sql", "db", "sqlite" ->
            Icons.Default.Storage to SemanticColors.FileType.database
        
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
