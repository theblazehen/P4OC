package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.blazelight.p4oc.domain.model.Permission
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDialogEnhanced(
    permission: Permission,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onAlways: () -> Unit
) {
    var showFullPreview by remember { mutableStateOf(false) }
    
    val codePreview = remember(permission) { extractCodePreview(permission) }
    val filePath = remember(permission) { extractFilePath(permission) }
    val command = remember(permission) { extractCommand(permission) }
    
    Dialog(
        onDismissRequest = onDeny,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                getPermissionIcon(permission.type),
                                contentDescription = null,
                                tint = getPermissionColor(permission.type)
                            )
                            Text("Permission Required")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = permission.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    PermissionTypeCard(permission.type)
                    
                    filePath?.let { path ->
                        FilePathCard(path)
                    }
                    
                    command?.let { cmd ->
                        CommandCard(cmd)
                    }
                    
                    codePreview?.let { code ->
                        CodePreviewCard(
                            code = code,
                            isExpanded = showFullPreview,
                            onToggleExpand = { showFullPreview = !showFullPreview }
                        )
                    }
                }
                
                HorizontalDivider()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = onDeny,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Deny")
                    }
                    
                    TextButton(onClick = onAlways) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Always")
                    }
                    
                    Button(onClick = onAllow) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Allow")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionTypeCard(type: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = getPermissionColor(type).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                getPermissionIcon(type),
                contentDescription = null,
                tint = getPermissionColor(type)
            )
            Column {
                Text(
                    text = formatPermissionType(type),
                    style = MaterialTheme.typography.labelLarge,
                    color = getPermissionColor(type)
                )
                Text(
                    text = getPermissionDescription(type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilePathCard(path: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun CommandCard(command: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Command",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = command,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun CodePreviewCard(
    code: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val lines = code.lines()
    val displayLines = if (isExpanded) lines else lines.take(10)
    val hasMore = lines.size > 10
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Code Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (hasMore) {
                    TextButton(
                        onClick = onToggleExpand,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        Text(if (isExpanded) "Show Less" else "Show All (${lines.size} lines)")
                    }
                }
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Column {
                    displayLines.forEachIndexed { index, line ->
                        Row {
                            Text(
                                text = "${index + 1}".padStart(4),
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = highlightCodeLine(line),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    if (hasMore && !isExpanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "... ${lines.size - 10} more lines",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun highlightCodeLine(line: String): AnnotatedString = buildAnnotatedString {
    val keywords = setOf("fun", "val", "var", "if", "else", "when", "for", "while", "return", 
        "class", "interface", "object", "import", "package", "private", "public", "internal",
        "const", "suspend", "override", "data", "sealed", "enum", "companion")
    
    var i = 0
    while (i < line.length) {
        when {
            line.startsWith("//", i) -> {
                withStyle(SpanStyle(color = Color(0xFF6A9955))) {
                    append(line.substring(i))
                }
                i = line.length
            }
            line[i] == '"' -> {
                val end = line.indexOf('"', i + 1).takeIf { it >= 0 } ?: line.length
                withStyle(SpanStyle(color = Color(0xFFCE9178))) {
                    append(line.substring(i, minOf(end + 1, line.length)))
                }
                i = end + 1
            }
            line[i].isLetter() -> {
                var end = i
                while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) end++
                val word = line.substring(i, end)
                when {
                    keywords.contains(word) -> {
                        withStyle(SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold)) {
                            append(word)
                        }
                    }
                    word.firstOrNull()?.isUpperCase() == true -> {
                        withStyle(SpanStyle(color = Color(0xFF4EC9B0))) {
                            append(word)
                        }
                    }
                    else -> {
                        withStyle(SpanStyle(color = Color(0xFFD4D4D4))) {
                            append(word)
                        }
                    }
                }
                i = end
            }
            line[i].isDigit() -> {
                var end = i
                while (end < line.length && (line[end].isDigit() || line[end] == '.')) end++
                withStyle(SpanStyle(color = Color(0xFFB5CEA8))) {
                    append(line.substring(i, end))
                }
                i = end
            }
            else -> {
                withStyle(SpanStyle(color = Color(0xFFD4D4D4))) {
                    append(line[i])
                }
                i++
            }
        }
    }
}

private fun getPermissionIcon(type: String) = when (type.lowercase()) {
    "file.write", "file.edit" -> Icons.Default.Edit
    "file.read" -> Icons.Default.Visibility
    "bash", "shell", "command" -> Icons.Default.Terminal
    "file.delete" -> Icons.Default.Delete
    else -> Icons.Default.Security
}

private fun getPermissionColor(type: String): Color = when (type.lowercase()) {
    "file.write", "file.edit" -> Color(0xFFFFA726)
    "file.read" -> Color(0xFF66BB6A)
    "bash", "shell", "command" -> Color(0xFFEF5350)
    "file.delete" -> Color(0xFFEF5350)
    else -> Color(0xFF42A5F5)
}

private fun formatPermissionType(type: String): String = when (type.lowercase()) {
    "file.write" -> "File Write"
    "file.read" -> "File Read"
    "file.edit" -> "File Edit"
    "file.delete" -> "File Delete"
    "bash", "shell" -> "Shell Command"
    "command" -> "Command Execution"
    else -> type.replaceFirstChar { it.uppercase() }
}

private fun getPermissionDescription(type: String): String = when (type.lowercase()) {
    "file.write" -> "The assistant wants to create or overwrite a file"
    "file.read" -> "The assistant wants to read file contents"
    "file.edit" -> "The assistant wants to modify an existing file"
    "file.delete" -> "The assistant wants to delete a file"
    "bash", "shell", "command" -> "The assistant wants to execute a shell command"
    else -> "The assistant is requesting permission for this operation"
}

private fun extractCodePreview(permission: Permission): String? {
    return try {
        permission.metadata["content"]?.jsonPrimitive?.content
            ?: permission.metadata["code"]?.jsonPrimitive?.content
            ?: permission.metadata["diff"]?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
}

private fun extractFilePath(permission: Permission): String? {
    return try {
        permission.metadata["path"]?.jsonPrimitive?.content
            ?: permission.metadata["filePath"]?.jsonPrimitive?.content
            ?: permission.metadata["file"]?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
}

private fun extractCommand(permission: Permission): String? {
    return try {
        permission.metadata["command"]?.jsonPrimitive?.content
            ?: permission.metadata["cmd"]?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }
}
