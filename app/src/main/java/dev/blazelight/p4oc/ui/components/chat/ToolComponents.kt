package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import kotlinx.serialization.json.*

@Composable
fun getToolIcon(toolName: String): ImageVector {
    return when (toolName.lowercase()) {
        "edit", "multiedit", "str_replace", "str_replace_based_edit_tool" -> Icons.Default.Edit
        "write", "create", "file_write" -> Icons.AutoMirrored.Filled.NoteAdd
        "read", "view", "file_read", "cat" -> Icons.Default.Description
        "bash", "shell", "cmd", "terminal", "interactive_bash" -> Icons.Default.Terminal
        "list", "ls", "dir", "list_files" -> Icons.Default.Folder
        "glob" -> Icons.Default.FolderOpen
        "search", "grep", "find", "ripgrep" -> Icons.Default.Search
        "websearch", "web_search", "web-search", "codesearch" -> Icons.Default.TravelExplore
        "fetch", "curl", "wget", "webfetch" -> Icons.Default.Cloud
        "task", "agent" -> Icons.Default.SmartToy
        "todowrite", "todoread" -> Icons.Default.Checklist
        "skill", "slashcommand" -> Icons.AutoMirrored.Filled.MenuBook
        "question" -> Icons.AutoMirrored.Filled.HelpOutline
        else -> if (toolName.lowercase().startsWith("git")) {
            Icons.Default.AccountTree
        } else {
            Icons.Default.Build
        }
    }
}

fun getToolDescription(toolName: String, input: JsonObject): String {
    return when (toolName.lowercase()) {
        "edit", "multiedit", "str_replace", "str_replace_based_edit_tool",
        "read", "view", "file_read", "cat",
        "write", "create", "file_write" -> {
            val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull
                ?: input["file_path"]?.jsonPrimitive?.contentOrNull
                ?: input["path"]?.jsonPrimitive?.contentOrNull
            filePath?.let { extractFileName(it) } ?: ""
        }
        
        "bash", "shell", "cmd", "terminal", "interactive_bash" -> {
            val command = input["command"]?.jsonPrimitive?.contentOrNull
                ?: input["tmux_command"]?.jsonPrimitive?.contentOrNull
            command?.lines()?.firstOrNull()?.take(60) ?: ""
        }
        
        "task" -> input["description"]?.jsonPrimitive?.contentOrNull?.take(50) ?: ""
        
        "skill", "slashcommand" -> {
            input["name"]?.jsonPrimitive?.contentOrNull
                ?: input["command"]?.jsonPrimitive?.contentOrNull
                ?: ""
        }
        
        "grep", "search" -> {
            val pattern = input["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
            val path = input["path"]?.jsonPrimitive?.contentOrNull
            if (path != null) "\"$pattern\" in ${extractFileName(path)}" else "\"$pattern\""
        }
        
        "glob" -> input["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
        
        "webfetch", "fetch" -> input["url"]?.jsonPrimitive?.contentOrNull?.take(50) ?: ""
        
        "question" -> {
            val questions = input["questions"]?.jsonArray
            if (questions != null) "${questions.size} question${if (questions.size != 1) "s" else ""}" else ""
        }
        
        else -> ""
    }
}

private fun extractFileName(path: String): String = path.substringAfterLast('/')

data class DiffStats(val added: Int, val removed: Int)

fun parseDiffStats(metadata: JsonObject?): DiffStats? {
    val diff = metadata?.get("diff")?.jsonPrimitive?.contentOrNull ?: return null
    var added = 0
    var removed = 0
    
    diff.lines().forEach { line ->
        when {
            line.startsWith("+") && !line.startsWith("+++") -> added++
            line.startsWith("-") && !line.startsWith("---") -> removed++
        }
    }
    
    return if (added > 0 || removed > 0) DiffStats(added, removed) else null
}

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val lineNumber: Int?
)

enum class DiffLineType { CONTEXT, ADDED, REMOVED, HEADER }

data class DiffHunk(
    val file: String,
    val startLine: Int,
    val lines: List<DiffLine>
)

fun parseDiffToHunks(diffContent: String): List<DiffHunk> {
    val hunks = mutableListOf<DiffHunk>()
    var currentFile = ""
    var currentLines = mutableListOf<DiffLine>()
    var currentStartLine = 0
    var lineNum = 0
    
    diffContent.lines().forEach { line ->
        when {
            line.startsWith("---") -> {
                currentFile = line.removePrefix("--- ").removePrefix("a/")
            }
            line.startsWith("+++") -> {
                if (currentFile.isEmpty()) {
                    currentFile = line.removePrefix("+++ ").removePrefix("b/")
                }
            }
            line.startsWith("@@") -> {
                if (currentLines.isNotEmpty()) {
                    hunks.add(DiffHunk(currentFile, currentStartLine, currentLines.toList()))
                    currentLines = mutableListOf()
                }
                val match = Regex("""@@ -\d+(?:,\d+)? \+(\d+)""").find(line)
                currentStartLine = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                lineNum = currentStartLine
                currentLines.add(DiffLine(DiffLineType.HEADER, line, null))
            }
            line.startsWith("+") -> {
                currentLines.add(DiffLine(DiffLineType.ADDED, line.drop(1), lineNum++))
            }
            line.startsWith("-") -> {
                currentLines.add(DiffLine(DiffLineType.REMOVED, line.drop(1), null))
            }
            else -> {
                if (line.isNotEmpty() || currentLines.isNotEmpty()) {
                    currentLines.add(DiffLine(DiffLineType.CONTEXT, line.removePrefix(" "), lineNum++))
                }
            }
        }
    }
    
    if (currentLines.isNotEmpty()) {
        hunks.add(DiffHunk(currentFile, currentStartLine, currentLines.toList()))
    }
    
    return hunks
}

@Composable
fun DiffPreview(
    diffContent: String,
    modifier: Modifier = Modifier
) {
    val hunks = remember(diffContent) { parseDiffToHunks(diffContent) }
    val addedBgColor = Color(0xFF2E7D32).copy(alpha = 0.15f)
    val removedBgColor = Color(0xFFC62828).copy(alpha = 0.15f)
    val addedTextColor = Color(0xFF4CAF50)
    val removedTextColor = Color(0xFFF44336)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        hunks.forEach { hunk ->
            if (hunk.file.isNotEmpty()) {
                Text(
                    text = "${hunk.file} (line ${hunk.startLine})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            hunk.lines.take(20).forEach { line ->
                if (line.type != DiffLineType.HEADER) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when (line.type) {
                                    DiffLineType.ADDED -> addedBgColor
                                    DiffLineType.REMOVED -> removedBgColor
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = line.lineNumber?.toString() ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(32.dp)
                        )
                        
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = when (line.type) {
                                            DiffLineType.ADDED -> addedTextColor
                                            DiffLineType.REMOVED -> removedTextColor
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                ) {
                                    append(when (line.type) {
                                        DiffLineType.ADDED -> "+"
                                        DiffLineType.REMOVED -> "-"
                                        else -> " "
                                    })
                                    append(line.content)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            val totalLines = hunk.lines.count { it.type != DiffLineType.HEADER }
            if (totalLines > 20) {
                Text(
                    text = "... ${totalLines - 20} more lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun ToolOutputDialog(
    toolName: String,
    output: String,
    metadata: JsonObject?,
    onDismiss: () -> Unit
) {
    val diffContent = metadata?.get("diff")?.jsonPrimitive?.contentOrNull
    val hasDiff = diffContent != null && (toolName.lowercase() in listOf("edit", "multiedit", "str_replace"))
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            getToolIcon(toolName),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                HorizontalDivider()
                
                if (hasDiff) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        DiffPreview(
                            diffContent = diffContent!!,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedToolPart(
    part: Part.Tool,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = part.state
    var isExpanded by remember { mutableStateOf(false) }
    var showFullOutput by remember { mutableStateOf(false) }
    
    val toolIcon = getToolIcon(part.toolName)
    val description = getToolDescription(part.toolName, state.input)
    
    val metadata = when (state) {
        is ToolState.Completed -> state.metadata
        is ToolState.Running -> state.metadata
        is ToolState.Error -> state.metadata
        is ToolState.Pending -> null
    }
    val diffStats = parseDiffStats(metadata)
    val hasDiff = metadata?.get("diff")?.jsonPrimitive?.contentOrNull != null
    
    val (containerColor, isError) = when (state) {
        is ToolState.Pending -> MaterialTheme.colorScheme.secondaryContainer to false
        is ToolState.Running -> MaterialTheme.colorScheme.primaryContainer to false
        is ToolState.Completed -> MaterialTheme.colorScheme.surfaceVariant to false
        is ToolState.Error -> MaterialTheme.colorScheme.errorContainer to true
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    toolIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error 
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = part.toolName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        
                        if (diffStats != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "+${diffStats.added}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = "-${diffStats.removed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF44336)
                                )
                            }
                        }
                    }
                    
                    if (description.isNotEmpty()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                
                when (state) {
                    is ToolState.Running -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    is ToolState.Completed, is ToolState.Error -> {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {}
                }
            }
            
            if (state is ToolState.Pending) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onDeny(part.callID) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Deny")
                    }
                    Button(
                        onClick = { onApprove(part.callID) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Allow")
                    }
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded && (state is ToolState.Completed || state is ToolState.Error),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasDiff && state is ToolState.Completed) {
                        val diffContent = metadata?.get("diff")?.jsonPrimitive?.contentOrNull
                        if (diffContent != null) {
                            DiffPreview(
                                diffContent = diffContent,
                                modifier = Modifier.heightIn(max = 200.dp)
                            )
                        }
                    }
                    
                    when (state) {
                        is ToolState.Completed -> {
                            if (state.output.isNotBlank() && !hasDiff) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.output.take(500) + 
                                               if (state.output.length > 500) "..." else "",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            
                            if (state.output.length > 500 || hasDiff) {
                                TextButton(
                                    onClick = { showFullOutput = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        Icons.Default.OpenInFull,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("View full output")
                                }
                            }
                        }
                        is ToolState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    
    if (showFullOutput && state is ToolState.Completed) {
        ToolOutputDialog(
            toolName = part.toolName,
            output = state.output,
            metadata = state.metadata,
            onDismiss = { showFullOutput = false }
        )
    }
}
