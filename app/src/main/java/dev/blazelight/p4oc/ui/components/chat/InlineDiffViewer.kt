package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

@Composable
fun InlineDiffViewer(
    fileName: String,
    diffContent: String,
    additions: Int = 0,
    deletions: Int = 0,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val diffLines = remember(diffContent) { parseInlineDiff(diffContent) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RectangleShape
    ) {
        Column {
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = stringResource(R.string.cd_diff_icon),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Sizing.iconMd)
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (additions > 0) {
                        Text(
                            text = "+$additions",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticColors.Diff.addedText
                        )
                    }
                    if (deletions > 0) {
                        Text(
                            text = "-$deletions",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticColors.Diff.removedText
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(Sizing.iconMd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded && diffLines.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        diffLines.forEach { line ->
                            InlineDiffLineRow(line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineDiffLineRow(line: DiffLine) {
    val (bgColor, textColor, prefix) = when (line.type) {
        DiffLineType.ADDED -> Triple(
            SemanticColors.Diff.addedBackground,
            SemanticColors.Diff.addedText,
            "+"
        )
        DiffLineType.REMOVED -> Triple(
            SemanticColors.Diff.removedBackground,
            SemanticColors.Diff.removedText,
            "-"
        )
        DiffLineType.HEADER -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.primary,
            ""
        )
        DiffLineType.CONTEXT -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.onSurface,
            " "
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (line.type != DiffLineType.HEADER) {
            Text(
                text = line.lineNumber?.toString()?.padStart(4) ?: "    ",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.width(32.dp)
            )
        }
        Text(
            text = if (line.type == DiffLineType.HEADER) line.content else "$prefix ${line.content}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = textColor
        )
    }
}

private fun parseInlineDiff(diffContent: String): List<DiffLine> {
    val lines = mutableListOf<DiffLine>()
    var lineNumber = 0

    diffContent.lines().forEach { line ->
        when {
            line.startsWith("@@") -> {
                val match = Regex("@@ -\\d+(?:,\\d+)? \\+(\\d+)").find(line)
                lineNumber = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                lines.add(DiffLine(DiffLineType.HEADER, line, null))
            }
            line.startsWith("+") && !line.startsWith("+++") -> {
                lines.add(DiffLine(DiffLineType.ADDED, line.drop(1), lineNumber++))
            }
            line.startsWith("-") && !line.startsWith("---") -> {
                lines.add(DiffLine(DiffLineType.REMOVED, line.drop(1), null))
            }
            line.startsWith("+++") || line.startsWith("---") -> {}
            else -> {
                lines.add(DiffLine(DiffLineType.CONTEXT, line.trimStart(), lineNumber++))
            }
        }
    }
    return lines
}

@Composable
fun PatchDiffViewer(
    files: List<String>,
    getDiffContent: suspend (String) -> String?,
    modifier: Modifier = Modifier
) {
    var expandedFile by remember { mutableStateOf<String?>(null) }
    var diffContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(expandedFile) {
        expandedFile?.let { file ->
            isLoading = true
            diffContent = getDiffContent(file)
            isLoading = false
        } ?: run {
            diffContent = null
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        files.forEach { file ->
            val isExpanded = expandedFile == file
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RectangleShape
            ) {
                Column {
                    Surface(
                        onClick = { 
                            expandedFile = if (isExpanded) null else file 
                        },
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = stringResource(R.string.cd_diff_icon),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Sizing.iconMd)
                            )
                            Text(
                                text = file,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isLoading && isExpanded) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Sizing.iconXs),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    modifier = Modifier.size(Sizing.iconMd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isExpanded && diffContent != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        val currentDiffContent = diffContent
                        val diffLines = remember(currentDiffContent) { 
                            currentDiffContent?.let { parseInlineDiff(it) } ?: emptyList()
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                diffLines.forEach { line ->
                                    InlineDiffLineRow(line)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
