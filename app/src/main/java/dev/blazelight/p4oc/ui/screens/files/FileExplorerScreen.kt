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
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import androidx.compose.ui.graphics.vector.ImageVector
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: FilesViewModel = koinViewModel(),
    onFileClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
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
        containerColor = theme.background,
        topBar = {
            Column {
                TuiTopBar(
                    title = "",
                    onNavigateBack = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else if (uiState.currentPath.isNotBlank()) {
                            viewModel.navigateUp()
                        } else {
                            onNavigateBack()
                        }
                    },
                    titleContent = {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.files_search_placeholder), color = theme.textMuted) },
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
                        } else {
                            Text(
                                text = uiState.currentPath.substringAfterLast('/').ifBlank { "Files" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = theme.text,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    actions = {
                        if (!isSearchActive) {
                            IconButton(
                                onClick = { isSearchActive = true },
                                modifier = Modifier.size(Sizing.iconButtonMd)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), tint = theme.textMuted, modifier = Modifier.size(Sizing.iconAction))
                            }
                        }
                        IconButton(
                            onClick = viewModel::refresh,
                            modifier = Modifier.size(Sizing.iconButtonMd)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh), tint = theme.textMuted, modifier = Modifier.size(Sizing.iconAction))
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
                    TuiLoadingScreen(modifier = Modifier.align(Alignment.Center))
                }
                filteredFiles.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "∅" else "□",
                            style = MaterialTheme.typography.displayMedium,
                            color = theme.textMuted
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "-- no matching files --" else "-- empty folder --",
                            color = theme.textMuted
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                    ) {
                        items(filteredFiles, key = { it.path }) { file ->
                            TuiFileItem(
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
    val theme = LocalOpenCodeTheme.current
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
            
            currentPath = if (currentPath.isEmpty()) "/$part" else "$currentPath/$part"
            val pathToNavigate = currentPath
            
            Surface(
                onClick = { onNavigateTo(pathToNavigate) },
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
private fun TuiFileItem(
    file: FileNode,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val (icon, iconColor) = getFileIcon(file)
    val gitStatusColor = getGitStatusColor(file.gitStatus)
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var showContextMenu by remember { mutableStateOf(false) }
    
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showContextMenu = true
                    }
                ),
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
                // File type indicator
                Text(
                    text = if (file.isDirectory) "▸" else " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.accent
                )
                
                // Icon
                Icon(
                    imageVector = icon,
                    contentDescription = if (file.isDirectory) "Folder" else "File",
                    tint = gitStatusColor ?: iconColor,
                    modifier = Modifier.size(Sizing.iconSm)
                )
                
                // File name
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
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
                
                // Directory indicator
                if (file.isDirectory) {
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textMuted
                    )
                }
            }
        }
        
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_copy_path), color = theme.text) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(file.path))
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.files_copy_path), tint = theme.textMuted)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.files_copy_name), color = theme.text) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(file.name))
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.TextFields, contentDescription = stringResource(R.string.files_copy_name), tint = theme.textMuted)
                }
            )
        }
    }
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
        
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to theme.textMuted
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
