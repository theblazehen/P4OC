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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.SemanticColors

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
                        contentDescription = stringResource(R.string.cd_add_more_files),
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
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                getFileIcon(attachment.mimeType),
                contentDescription = stringResource(R.string.cd_file_type),
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
                    contentDescription = stringResource(R.string.cd_remove),
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
            contentDescription = stringResource(R.string.cd_attach_files)
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
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = stringResource(R.string.cd_file_type)) },
            onClick = {
                onSelectFiles()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Images") },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = stringResource(R.string.cd_image_type)) },
            onClick = {
                onSelectImages()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("From Project") },
            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.cd_folder_type)) },
            onClick = {
                onSelectFromProject()
                onDismiss()
            }
        )
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
                shape = RectangleShape,
                color = getFileColor(attachment.mimeType).copy(alpha = 0.2f)
            ) {
                Icon(
                    getFileIcon(attachment.mimeType),
                    contentDescription = stringResource(R.string.cd_file_type),
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
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun getFileColor(mimeType: String): Color = SemanticColors.MimeType.forMimeType(mimeType)

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
