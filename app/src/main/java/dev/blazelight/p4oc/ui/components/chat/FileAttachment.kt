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
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

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
    val theme = LocalOpenCodeTheme.current
    
    AnimatedVisibility(
        visible = attachments.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = theme.backgroundElement,
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                attachments.forEach { attachment ->
                    AttachmentChip(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment) }
                    )
                }
                
                Text(
                    text = "+",
                    color = theme.accent,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onAddAttachment)
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: FileAttachment,
    onRemove: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        shape = RectangleShape,
        color = theme.accent.copy(alpha = 0.1f),
        modifier = Modifier.border(1.dp, theme.border, RectangleShape)
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = getFileSymbol(attachment.mimeType),
                color = getFileColor(attachment.mimeType),
                fontFamily = FontFamily.Monospace
            )
            
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = theme.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            
            Text(
                text = formatFileSize(attachment.size),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = theme.textMuted
            )
            
            Text(
                text = "×",
                color = theme.textMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clickable(onClick = onRemove)
            )
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
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        modifier = modifier,
        color = theme.backgroundElement,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Surface(
                shape = RectangleShape,
                color = getFileColor(attachment.mimeType).copy(alpha = 0.2f)
            ) {
                Text(
                    text = getFileSymbol(attachment.mimeType),
                    modifier = Modifier.padding(Spacing.lg),
                    color = getFileColor(attachment.mimeType),
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${getMimeTypeLabel(attachment.mimeType)} • ${formatFileSize(attachment.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted
                )
            }
        }
    }
}

private fun getFileSymbol(mimeType: String) = when {
    mimeType.startsWith("image/") -> "◫"
    mimeType.startsWith("video/") -> "▶"
    mimeType.startsWith("audio/") -> "♪"
    mimeType.startsWith("text/") -> "≡"
    mimeType.contains("pdf") -> "◰"
    mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("rar") -> "▤"
    mimeType.contains("json") || mimeType.contains("xml") -> "◇"
    else -> "□"
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

@Composable
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
