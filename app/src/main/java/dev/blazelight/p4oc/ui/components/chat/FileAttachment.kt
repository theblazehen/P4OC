package dev.blazelight.p4oc.ui.components.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class FileAttachment(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long
)

@Composable
fun FileAttachmentBar(
    attachments: List<FileAttachment>,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (FileAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = attachments.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                attachments.forEach { attachment ->
                    AttachmentChip(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment) }
                    )
                }
                
                IconButton(
                    onClick = onAddAttachment,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add more files",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: FileAttachment,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                getFileIcon(attachment.mimeType),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = getFileColor(attachment.mimeType)
            )
            
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            
            Text(
                text = formatFileSize(attachment.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun AttachmentButton(
    onFilesSelected: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }
    
    IconButton(
        onClick = {
            launcher.launch(arrayOf("*/*"))
        },
        modifier = modifier
    ) {
        Icon(
            Icons.Default.AttachFile,
            contentDescription = "Attach files"
        )
    }
}

@Composable
fun AttachmentMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelectFiles: () -> Unit,
    onSelectImages: () -> Unit,
    onSelectFromProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        DropdownMenuItem(
            text = { Text("Files") },
            leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
            onClick = {
                onSelectFiles()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Images") },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
            onClick = {
                onSelectImages()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("From Project") },
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
            onClick = {
                onSelectFromProject()
                onDismiss()
            }
        )
    }
}

@Composable
fun ChatInputBarWithAttachments(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    attachments: List<FileAttachment>,
    onAddAttachment: (List<Uri>) -> Unit,
    onRemoveAttachment: (FileAttachment) -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onAddAttachment(uris) }
    
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onAddAttachment(uris) }
    
    Column(modifier = modifier) {
        FileAttachmentBar(
            attachments = attachments,
            onAddAttachment = { showAttachmentMenu = true },
            onRemoveAttachment = onRemoveAttachment
        )
        
        Surface(
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    IconButton(
                        onClick = { showAttachmentMenu = true },
                        enabled = enabled
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                    }
                    
                    AttachmentMenu(
                        expanded = showAttachmentMenu,
                        onDismiss = { showAttachmentMenu = false },
                        onSelectFiles = { fileLauncher.launch(arrayOf("*/*")) },
                        onSelectImages = { imageLauncher.launch(arrayOf("image/*")) },
                        onSelectFromProject = { /* TODO: Open project file picker */ }
                    )
                }
                
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    enabled = enabled,
                    maxLines = 5
                )
                
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled && (value.isNotBlank() || attachments.isNotEmpty())
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentPreview(
    attachment: FileAttachment,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = getFileColor(attachment.mimeType).copy(alpha = 0.2f)
            ) {
                Icon(
                    getFileIcon(attachment.mimeType),
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = getFileColor(attachment.mimeType)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${getMimeTypeLabel(attachment.mimeType)} • ${formatFileSize(attachment.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getFileIcon(mimeType: String) = when {
    mimeType.startsWith("image/") -> Icons.Default.Image
    mimeType.startsWith("video/") -> Icons.Default.VideoFile
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    mimeType.startsWith("text/") -> Icons.Default.Description
    mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
    mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("rar") -> Icons.Default.FolderZip
    mimeType.contains("json") || mimeType.contains("xml") -> Icons.Default.DataObject
    else -> Icons.Default.InsertDriveFile
}

private fun getFileColor(mimeType: String): Color = when {
    mimeType.startsWith("image/") -> Color(0xFF66BB6A)
    mimeType.startsWith("video/") -> Color(0xFFEF5350)
    mimeType.startsWith("audio/") -> Color(0xFFAB47BC)
    mimeType.startsWith("text/") -> Color(0xFF42A5F5)
    mimeType.contains("pdf") -> Color(0xFFEF5350)
    mimeType.contains("zip") -> Color(0xFFFFA726)
    mimeType.contains("json") || mimeType.contains("xml") -> Color(0xFF26A69A)
    else -> Color(0xFF78909C)
}

private fun getMimeTypeLabel(mimeType: String): String = when {
    mimeType.startsWith("image/") -> "Image"
    mimeType.startsWith("video/") -> "Video"
    mimeType.startsWith("audio/") -> "Audio"
    mimeType.startsWith("text/plain") -> "Text"
    mimeType.contains("pdf") -> "PDF"
    mimeType.contains("json") -> "JSON"
    mimeType.contains("xml") -> "XML"
    mimeType.contains("zip") -> "ZIP"
    else -> "File"
}

private fun formatFileSize(size: Long): String = when {
    size >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f GB", size / 1_000_000_000.0)
    size >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", size / 1_000_000.0)
    size >= 1_000 -> String.format(java.util.Locale.US, "%.1f KB", size / 1_000.0)
    else -> "$size B"
}
