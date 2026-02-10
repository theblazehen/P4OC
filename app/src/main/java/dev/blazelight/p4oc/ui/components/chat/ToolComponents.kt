package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Part
import dev.blazelight.p4oc.domain.model.ToolState
import dev.blazelight.p4oc.ui.theme.SemanticColors
import kotlinx.serialization.json.*
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import androidx.compose.foundation.BorderStroke

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
    val theme = LocalOpenCodeTheme.current
    val addedBgColor = SemanticColors.Diff.addedBackground
    val removedBgColor = SemanticColors.Diff.removedBackground
    val addedTextColor = SemanticColors.Diff.addedText
    val removedTextColor = SemanticColors.Diff.removedText
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(theme.background)
    ) {
        hunks.forEach { hunk ->
            if (hunk.file.isNotEmpty()) {
                Text(
                    text = "${hunk.file} (line ${hunk.startLine})",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.backgroundElement)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs)
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
                            .padding(horizontal = Spacing.md, vertical = 1.dp)
                    ) {
                        Text(
                            text = line.lineNumber?.toString() ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.sm
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(32.dp)
                        )
                        
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = when (line.type) {
                                            DiffLineType.ADDED -> addedTextColor
                                            DiffLineType.REMOVED -> removedTextColor
                                            else -> theme.text
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
                                fontSize = TuiCodeFontSize.md
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
                    color = theme.textMuted,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(Spacing.md)
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
    val theme = LocalOpenCodeTheme.current
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
            shape = RectangleShape,
            color = theme.background,
            border = BorderStroke(1.dp, theme.border)
        ) {
            Column {
                // TUI Header
                Surface(
                    color = theme.backgroundElement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                getToolIcon(toolName),
                                contentDescription = stringResource(R.string.cd_tool_status),
                                modifier = Modifier.size(Sizing.iconSm),
                                tint = theme.accent
                            )
                            Text(
                                text = "[ $toolName ]",
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.text
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(Sizing.iconButtonSm)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = theme.textMuted,
                                modifier = Modifier.size(Sizing.iconSm)
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = theme.border)
                
                if (hasDiff && diffContent != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md)
                    ) {
                        DiffPreview(
                            diffContent = diffContent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md)
                    ) {
                        Text(
                            text = output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.lg
                            ),
                            color = theme.text
                        )
                    }
                }
                
                // Footer with output stats
                Surface(
                    color = theme.backgroundElement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${output.lines().size} lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    )
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
    val theme = LocalOpenCodeTheme.current
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
        is ToolState.Pending -> theme.warning.copy(alpha = 0.15f) to false
        is ToolState.Running -> theme.accent.copy(alpha = 0.15f) to false
        is ToolState.Completed -> theme.backgroundElement to false
        is ToolState.Error -> theme.error.copy(alpha = 0.15f) to true
    }
    
    Surface(
        color = containerColor,
        shape = RectangleShape,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    toolIcon,
                    contentDescription = stringResource(R.string.cd_tool_status),
                    modifier = Modifier.size(Sizing.iconSm),
                    tint = if (isError) theme.error else theme.textMuted
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = part.toolName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = theme.text
                        )
                        
                        if (diffStats != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    text = "+${diffStats.added}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SemanticColors.Diff.addedText
                                )
                                Text(
                                    text = "-${diffStats.removed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SemanticColors.Diff.removedText
                                )
                            }
                        }
                    }
                    
                    if (description.isNotEmpty()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textMuted,
                            maxLines = 1
                        )
                    }
                }
                
                when (state) {
                    is ToolState.Running -> {
                        TuiLoadingIndicator()
                    }
                    is ToolState.Completed, is ToolState.Error -> {
                        Text(
                            text = if (isExpanded) "▴" else "▾",
                            color = theme.textMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    else -> {}
                }
            }
            
            if (state is ToolState.Pending) {
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedButton(
                        onClick = { onDeny(part.callID) },
                        modifier = Modifier.weight(1f),
                        shape = RectangleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = theme.error
                        )
                    ) {
                        Text(stringResource(R.string.deny))
                    }
                    Button(
                        onClick = { onApprove(part.callID) },
                        modifier = Modifier.weight(1f),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.success,
                            contentColor = theme.background
                        )
                    ) {
                        Text(stringResource(R.string.allow))
                    }
                }
            }
            
            AnimatedVisibility(
                visible = isExpanded && (state is ToolState.Completed || state is ToolState.Error),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
                                    color = theme.background,
                                    shape = RectangleShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.output.take(500) + 
                                               if (state.output.length > 500) "..." else "",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = theme.text,
                                        modifier = Modifier.padding(Spacing.md)
                                    )
                                }
                            }
                            
                            if (state.output.length > 500 || hasDiff) {
                                TextButton(
                                    onClick = { showFullOutput = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "[${stringResource(R.string.view_full_output)}]",
                                        color = theme.accent,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        is ToolState.Error -> {
                            Surface(
                                color = theme.error.copy(alpha = 0.1f),
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.error,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(Spacing.md)
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
