package com.pocketcode.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pocketcode.domain.model.FileNode

data class SelectedFile(
    val path: String,
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerDialog(
    files: List<FileNode>,
    currentPath: String,
    isLoading: Boolean,
    selectedFiles: List<SelectedFile>,
    onNavigateTo: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onFileSelected: (FileNode) -> Unit,
    onFileDeselected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) {
            files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } else {
            files
                .filter { it.name.contains(searchQuery, ignoreCase = true) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column {
                TopAppBar(
                    title = { 
                        Text(
                            text = "Attach Files",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    actions = {
                        if (selectedFiles.isNotEmpty()) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text("${selectedFiles.size}")
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        TextButton(
                            onClick = onConfirm,
                            enabled = selectedFiles.isNotEmpty()
                        ) {
                            Text("Attach")
                        }
                    }
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search files...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }}
                    } else null,
                    singleLine = true
                )

                if (currentPath.isNotBlank()) {
                    PickerBreadcrumb(
                        path = currentPath,
                        onNavigateTo = onNavigateTo
                    )
                }

                if (selectedFiles.isNotEmpty()) {
                    SelectedFilesChips(
                        selectedFiles = selectedFiles,
                        onRemove = onFileDeselected
                    )
                }

                HorizontalDivider()

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> {
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
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
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
                                if (currentPath.isNotBlank()) {
                                    item(key = "..") {
                                        ListItem(
                                            headlineContent = { Text("..") },
                                            leadingContent = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(onClick = onNavigateUp)
                                        )
                                    }
                                }
                                
                                items(filteredFiles, key = { it.path }) { file ->
                                    val isSelected = selectedFiles.any { it.path == file.path }
                                    PickerFileItem(
                                        file = file,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (file.isDirectory) {
                                                onNavigateTo(file.path)
                                            } else {
                                                if (isSelected) {
                                                    onFileDeselected(file.path)
                                                } else {
                                                    onFileSelected(file)
                                                }
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
    }
}

@Composable
private fun PickerBreadcrumb(
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
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = "Root",
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
                contentDescription = null,
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
                shape = RoundedCornerShape(4.dp)
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

@Composable
private fun SelectedFilesChips(
    selectedFiles: List<SelectedFile>,
    onRemove: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        selectedFiles.forEach { file ->
            InputChip(
                selected = true,
                onClick = { onRemove(file.path) },
                label = { 
                    Text(
                        file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PickerFileItem(
    file: FileNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, iconColor) = getPickerFileIcon(file)
    
    ListItem(
        headlineContent = { 
            Text(
                text = file.name,
                fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal
            ) 
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            if (file.isDirectory) {
                Icon(
                    Icons.Default.ChevronRight, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = if (isSelected && !file.isDirectory) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else {
            ListItemDefaults.colors()
        }
    )
}

@Composable
private fun getPickerFileIcon(file: FileNode): Pair<ImageVector, Color> {
    if (file.isDirectory) {
        return Icons.Default.Folder to MaterialTheme.colorScheme.primary
    }
    
    val extension = file.name.substringAfterLast('.', "").lowercase()
    
    return when (extension) {
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "rs", "go", "rb", "php", "swift", "m" ->
            Icons.Default.Code to Color(0xFF4CAF50)
        "json", "yaml", "yml", "xml", "toml", "ini", "conf", "config", "properties" ->
            Icons.Default.Settings to Color(0xFFFF9800)
        "md", "txt", "rst", "doc", "docx", "pdf" ->
            Icons.Default.Description to Color(0xFF2196F3)
        "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp" ->
            Icons.Default.Image to Color(0xFFE91E63)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
}
