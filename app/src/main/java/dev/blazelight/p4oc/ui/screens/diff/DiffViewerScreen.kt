package dev.blazelight.p4oc.ui.screens.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import dev.blazelight.p4oc.ui.components.TuiTopBar

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
    val theme = LocalOpenCodeTheme.current
    var viewMode by remember { mutableStateOf(DiffViewMode.UNIFIED) }
    val (parsedFileName, hunks) = remember(diffContent) { parseDiff(diffContent) }
    val displayFileName = fileName ?: parsedFileName

    Scaffold(
        topBar = {
            TuiTopBar(
                title = "",
                onNavigateBack = onNavigateBack,
                titleContent = {
                    Column {
                        Text(
                            text = "[ ${stringResource(R.string.diff_viewer_title)} ]",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            color = theme.text
                        )
                        if (displayFileName.isNotEmpty()) {
                            Text(
                                text = displayFileName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = theme.textMuted
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewMode = if (viewMode == DiffViewMode.UNIFIED) 
                                DiffViewMode.SIDE_BY_SIDE else DiffViewMode.UNIFIED
                        },
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Text(
                            text = if (viewMode == DiffViewMode.UNIFIED) "⫼" else "≡",
                            color = theme.accent,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )
        },
        containerColor = theme.background,
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
                    text = "∅ ${stringResource(R.string.diff_no_content)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted
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
    val theme = LocalOpenCodeTheme.current
    val addedBgColor = SemanticColors.Diff.addedBackground
    val removedBgColor = SemanticColors.Diff.removedBackground
    val addedTextColor = SemanticColors.Diff.addedText
    val removedTextColor = SemanticColors.Diff.removedText

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
                            .background(theme.backgroundElement)
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    ) {
                        Text(
                            text = line.content,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = theme.accent
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
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = (line.newLineNumber?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
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
                                            else -> theme.text
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
                                fontSize = TuiCodeFontSize.lg
                            ),
                            modifier = Modifier.padding(start = Spacing.md)
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
    val theme = LocalOpenCodeTheme.current
    val addedBgColor = SemanticColors.Diff.addedBackground
    val removedBgColor = SemanticColors.Diff.removedBackground
    val addedTextColor = SemanticColors.Diff.addedText
    val removedTextColor = SemanticColors.Diff.removedText

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
                        .background(theme.backgroundElement)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                ) {
                    Text(
                        text = line.headerContent,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = theme.accent
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
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = line.leftContent ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.lg
                            ),
                            color = if (line.leftType == DiffLine.LineType.REMOVED) 
                                removedTextColor else theme.text,
                            modifier = Modifier.padding(start = Spacing.xs)
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 1.dp,
                        color = theme.border
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
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = line.rightContent ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.lg
                            ),
                            color = if (line.rightType == DiffLine.LineType.ADDED) 
                                addedTextColor else theme.text,
                            modifier = Modifier.padding(start = Spacing.xs)
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (added > 0) {
            Surface(
                color = SemanticColors.Diff.addedBackground,
                shape = RectangleShape
            ) {
                Text(
                    text = "+$added",
                    style = MaterialTheme.typography.labelSmall,
                    color = SemanticColors.Diff.addedText,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
        }
        if (removed > 0) {
            Surface(
                color = SemanticColors.Diff.removedBackground,
                shape = RectangleShape
            ) {
                Text(
                    text = "-$removed",
                    style = MaterialTheme.typography.labelSmall,
                    color = SemanticColors.Diff.removedText,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
        }
    }
}
