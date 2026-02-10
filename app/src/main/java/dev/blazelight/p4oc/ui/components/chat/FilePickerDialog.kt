package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.FileNode
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen

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
    val theme = LocalOpenCodeTheme.current

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
                .fillMaxHeight(0.85f)
                .border(1.dp, theme.border, RectangleShape),
            shape = RectangleShape,
            color = theme.background
        ) {
            Column {
                // TUI Header: [ Attach Files ]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.backgroundElement)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "×",
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.textMuted,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable(onClick = onDismiss)
                                    .padding(end = Spacing.md)
                            )
                            Text(
                                text = "[ ${stringResource(R.string.attach_files)} ]",
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.text,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            if (selectedFiles.isNotEmpty()) {
                                Text(
                                    text = "[${selectedFiles.size}]",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = theme.accent,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (selectedFiles.isNotEmpty()) "[${stringResource(R.string.attach)}]" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selectedFiles.isNotEmpty()) theme.accent else theme.textMuted,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable(
                                    enabled = selectedFiles.isNotEmpty(),
                                    onClick = onConfirm
                                )
                            )
                        }
                    }
                }

                // Search field with TUI style
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    placeholder = { 
                        Text(
                            "/ ${stringResource(R.string.search_files)}",
                            fontFamily = FontFamily.Monospace,
                            color = theme.textMuted
                        ) 
                    },
                    leadingIcon = { 
                        Text(
                            "/",
                            fontFamily = FontFamily.Monospace,
                            color = theme.accent
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { 
                            Text(
                                "×",
                                fontFamily = FontFamily.Monospace,
                                color = theme.textMuted,
                                modifier = Modifier.clickable { searchQuery = "" }
                            )
                        }
                    } else null,
                    singleLine = true,
                    shape = RectangleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.accent,
                        unfocusedBorderColor = theme.border,
                        focusedContainerColor = theme.background,
                        unfocusedContainerColor = theme.background
                    )
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

                HorizontalDivider(color = theme.border)

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isLoading -> {
                            TuiLoadingScreen(modifier = Modifier.align(Alignment.Center))
                        }
                        filteredFiles.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "∅" else "◇",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = theme.textMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank()) stringResource(R.string.no_matching_files) else stringResource(R.string.empty_folder),
                                    color = theme.textMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                            ) {
                                if (currentPath.isNotBlank()) {
                                    item(key = "..") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(onClick = onNavigateUp)
                                                .padding(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                        ) {
                                            Text(
                                                text = "←",
                                                color = theme.accent,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "..",
                                                color = theme.text,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
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
    val theme = LocalOpenCodeTheme.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Root indicator: ~
        Text(
            text = "~",
            style = MaterialTheme.typography.labelMedium,
            color = theme.accent,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { onNavigateTo("") }
        )
        
        var currentPath = ""
        parts.forEachIndexed { index, part ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelMedium,
                color = theme.textMuted,
                fontFamily = FontFamily.Monospace
            )
            
            currentPath = if (currentPath.isEmpty()) "/$part" else "$currentPath/$part"
            val pathToNavigate = currentPath
            
            Text(
                text = part,
                style = MaterialTheme.typography.labelMedium,
                color = if (index == parts.lastIndex) theme.accent else theme.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (index == parts.lastIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onNavigateTo(pathToNavigate) },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SelectedFilesChips(
    selectedFiles: List<SelectedFile>,
    onRemove: (String) -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        selectedFiles.forEach { file ->
            Row(
                modifier = Modifier
                    .background(theme.backgroundElement)
                    .border(1.dp, theme.border, RectangleShape)
                    .clickable { onRemove(file.path) }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.text,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun PickerFileItem(
    file: FileNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val (icon, iconColor) = getPickerFileIcon(file)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected && !file.isDirectory) theme.accent.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Selection/folder indicator
        Text(
            text = when {
                isSelected && !file.isDirectory -> "✓"
                file.isDirectory -> "▸"
                else -> " "
            },
            color = when {
                isSelected -> theme.success
                file.isDirectory -> theme.accent
                else -> theme.textMuted
            },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium
        )
        
        // File name
        Text(
            text = file.name + if (file.isDirectory) "/" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (file.isDirectory) theme.accent else theme.text,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Trailing indicator
        if (file.isDirectory) {
            Text(
                text = "→",
                color = theme.textMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun getPickerFileIcon(file: FileNode): Pair<ImageVector, Color> {
    val theme = LocalOpenCodeTheme.current
    
    if (file.isDirectory) {
        return Icons.Default.Folder to theme.accent
    }
    
    val extension = file.name.substringAfterLast('.', "").lowercase()
    
    return when (extension) {
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "rs", "go", "rb", "php", "swift", "m" ->
            Icons.Default.Code to SemanticColors.Status.success
        "json", "yaml", "yml", "xml", "toml", "ini", "conf", "config", "properties" ->
            Icons.Default.Settings to SemanticColors.MimeType.archive
        "md", "txt", "rst", "doc", "docx", "pdf" ->
            Icons.Default.Description to SemanticColors.Reason.info
        "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp" ->
            Icons.Default.Image to SemanticColors.MimeType.video
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to theme.textMuted
    }
}
