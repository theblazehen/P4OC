package com.pocketcode.ui.screens.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class DiffViewMode { UNIFIED, SIDE_BY_SIDE }

data class DiffLine(
    val type: LineType,
    val content: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
) {
    enum class LineType { CONTEXT, ADDED, REMOVED, HEADER }
}

data class DiffHunk(
    val header: String,
    val oldStart: Int,
    val newStart: Int,
    val lines: List<DiffLine>
)

fun parseDiff(diffContent: String): Pair<String, List<DiffHunk>> {
    val lines = diffContent.lines()
    val hunks = mutableListOf<DiffHunk>()
    var fileName = ""
    var currentHunkLines = mutableListOf<DiffLine>()
    var currentHeader = ""
    var oldStart = 0
    var newStart = 0
    var oldLine = 0
    var newLine = 0

    for (line in lines) {
        when {
            line.startsWith("---") -> {
                fileName = line.removePrefix("--- ").removePrefix("a/")
            }
            line.startsWith("+++") -> {
                if (fileName.isEmpty()) {
                    fileName = line.removePrefix("+++ ").removePrefix("b/")
                }
            }
            line.startsWith("@@") -> {
                if (currentHunkLines.isNotEmpty()) {
                    hunks.add(DiffHunk(currentHeader, oldStart, newStart, currentHunkLines.toList()))
                    currentHunkLines = mutableListOf()
                }
                currentHeader = line
                val match = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)""").find(line)
                oldStart = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                newStart = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                oldLine = oldStart
                newLine = newStart
                currentHunkLines.add(DiffLine(DiffLine.LineType.HEADER, line, null, null))
            }
            line.startsWith("+") && !line.startsWith("+++") -> {
                currentHunkLines.add(DiffLine(DiffLine.LineType.ADDED, line.drop(1), null, newLine++))
            }
            line.startsWith("-") && !line.startsWith("---") -> {
                currentHunkLines.add(DiffLine(DiffLine.LineType.REMOVED, line.drop(1), oldLine++, null))
            }
            line.startsWith(" ") || (currentHunkLines.isNotEmpty() && !line.startsWith("\\")) -> {
                val content = if (line.startsWith(" ")) line.drop(1) else line
                currentHunkLines.add(DiffLine(DiffLine.LineType.CONTEXT, content, oldLine++, newLine++))
            }
        }
    }

    if (currentHunkLines.isNotEmpty()) {
        hunks.add(DiffHunk(currentHeader, oldStart, newStart, currentHunkLines.toList()))
    }

    return fileName to hunks
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    diffContent: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    fileName: String? = null
) {
    var viewMode by remember { mutableStateOf(DiffViewMode.UNIFIED) }
    val (parsedFileName, hunks) = remember(diffContent) { parseDiff(diffContent) }
    val displayFileName = fileName ?: parsedFileName

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Diff Viewer",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (displayFileName.isNotEmpty()) {
                            Text(
                                text = displayFileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewMode = if (viewMode == DiffViewMode.UNIFIED) 
                                DiffViewMode.SIDE_BY_SIDE else DiffViewMode.UNIFIED
                        }
                    ) {
                        Icon(
                            if (viewMode == DiffViewMode.UNIFIED) Icons.Default.ViewColumn 
                            else Icons.Default.ViewAgenda,
                            contentDescription = if (viewMode == DiffViewMode.UNIFIED) 
                                "Switch to side-by-side" else "Switch to unified"
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        if (hunks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No diff content to display",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            when (viewMode) {
                DiffViewMode.UNIFIED -> UnifiedDiffView(
                    hunks = hunks,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
                DiffViewMode.SIDE_BY_SIDE -> SideBySideDiffView(
                    hunks = hunks,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
        }
    }
}

@Composable
private fun UnifiedDiffView(
    hunks: List<DiffHunk>,
    modifier: Modifier = Modifier
) {
    val addedBgColor = Color(0xFF2E7D32).copy(alpha = 0.15f)
    val removedBgColor = Color(0xFFC62828).copy(alpha = 0.15f)
    val addedTextColor = Color(0xFF4CAF50)
    val removedTextColor = Color(0xFFF44336)
    val headerBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val allLines = remember(hunks) {
        hunks.flatMap { hunk -> hunk.lines }
    }

    LazyColumn(
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        itemsIndexed(allLines) { _, line ->
            when (line.type) {
                DiffLine.LineType.HEADER -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBgColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = line.content,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when (line.type) {
                                    DiffLine.LineType.ADDED -> addedBgColor
                                    DiffLine.LineType.REMOVED -> removedBgColor
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = (line.oldLineNumber?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = (line.newLineNumber?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                val prefix = when (line.type) {
                                    DiffLine.LineType.ADDED -> "+"
                                    DiffLine.LineType.REMOVED -> "-"
                                    else -> " "
                                }
                                withStyle(
                                    SpanStyle(
                                        color = when (line.type) {
                                            DiffLine.LineType.ADDED -> addedTextColor
                                            DiffLine.LineType.REMOVED -> removedTextColor
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (line.type != DiffLine.LineType.CONTEXT) 
                                            FontWeight.Medium else FontWeight.Normal
                                    )
                                ) {
                                    append(prefix)
                                    append(line.content)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBySideDiffView(
    hunks: List<DiffHunk>,
    modifier: Modifier = Modifier
) {
    val addedBgColor = Color(0xFF2E7D32).copy(alpha = 0.15f)
    val removedBgColor = Color(0xFFC62828).copy(alpha = 0.15f)
    val addedTextColor = Color(0xFF4CAF50)
    val removedTextColor = Color(0xFFF44336)
    val headerBgColor = MaterialTheme.colorScheme.surfaceContainerHigh

    data class SideBySideLine(
        val leftLineNum: Int?,
        val leftContent: String?,
        val leftType: DiffLine.LineType?,
        val rightLineNum: Int?,
        val rightContent: String?,
        val rightType: DiffLine.LineType?,
        val isHeader: Boolean = false,
        val headerContent: String = ""
    )

    val sideBySideLines = remember(hunks) {
        val result = mutableListOf<SideBySideLine>()
        
        for (hunk in hunks) {
            result.add(SideBySideLine(
                leftLineNum = null, leftContent = null, leftType = null,
                rightLineNum = null, rightContent = null, rightType = null,
                isHeader = true, headerContent = hunk.header
            ))

            val removed = mutableListOf<DiffLine>()
            val added = mutableListOf<DiffLine>()

            for (line in hunk.lines) {
                when (line.type) {
                    DiffLine.LineType.HEADER -> {}
                    DiffLine.LineType.CONTEXT -> {
                        while (removed.isNotEmpty() || added.isNotEmpty()) {
                            val rem = removed.removeFirstOrNull()
                            val add = added.removeFirstOrNull()
                            result.add(SideBySideLine(
                                leftLineNum = rem?.oldLineNumber,
                                leftContent = rem?.content,
                                leftType = rem?.type,
                                rightLineNum = add?.newLineNumber,
                                rightContent = add?.content,
                                rightType = add?.type
                            ))
                        }
                        result.add(SideBySideLine(
                            leftLineNum = line.oldLineNumber,
                            leftContent = line.content,
                            leftType = DiffLine.LineType.CONTEXT,
                            rightLineNum = line.newLineNumber,
                            rightContent = line.content,
                            rightType = DiffLine.LineType.CONTEXT
                        ))
                    }
                    DiffLine.LineType.REMOVED -> removed.add(line)
                    DiffLine.LineType.ADDED -> added.add(line)
                }
            }

            while (removed.isNotEmpty() || added.isNotEmpty()) {
                val rem = removed.removeFirstOrNull()
                val add = added.removeFirstOrNull()
                result.add(SideBySideLine(
                    leftLineNum = rem?.oldLineNumber,
                    leftContent = rem?.content,
                    leftType = rem?.type,
                    rightLineNum = add?.newLineNumber,
                    rightContent = add?.content,
                    rightType = add?.type
                ))
            }
        }
        result
    }

    LazyColumn(
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        itemsIndexed(sideBySideLines) { _, line ->
            if (line.isHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBgColor)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = line.headerContent,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                when (line.leftType) {
                                    DiffLine.LineType.REMOVED -> removedBgColor
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = (line.leftLineNum?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = line.leftContent ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = if (line.leftType == DiffLine.LineType.REMOVED) 
                                removedTextColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                when (line.rightType) {
                                    DiffLine.LineType.ADDED -> addedBgColor
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = (line.rightLineNum?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = line.rightContent ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = if (line.rightType == DiffLine.LineType.ADDED) 
                                addedTextColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiffStats(added: Int, removed: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (added > 0) {
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "+$added",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (removed > 0) {
            Surface(
                color = Color(0xFFF44336).copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "-$removed",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF44336),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
