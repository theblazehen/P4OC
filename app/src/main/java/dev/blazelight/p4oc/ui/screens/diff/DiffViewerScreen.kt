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
import dev.blazelight.p4oc.ui.diff.ParsedDiffLine
import dev.blazelight.p4oc.ui.diff.ParsedDiffLineType
import dev.blazelight.p4oc.ui.diff.ParsedDiffParser
import dev.blazelight.p4oc.ui.diff.ParsedFileDiff
import dev.blazelight.p4oc.ui.diff.allHunks

enum class DiffViewMode { UNIFIED, SIDE_BY_SIDE }


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
    val parsedDiff = remember(diffContent) { ParsedDiffParser.parse(diffContent) }
    val files = parsedDiff.files
    val hunks = parsedDiff.allHunks()
    val displayFileName = fileName ?: files.singleOrNull()?.displayFileName.orEmpty()

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
                    files = files,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
                DiffViewMode.SIDE_BY_SIDE -> SideBySideDiffView(
                    files = files,
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
    files: List<ParsedFileDiff>,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val addedBgColor = SemanticColors.Diff.addedBackground
    val removedBgColor = SemanticColors.Diff.removedBackground
    val addedTextColor = SemanticColors.Diff.addedText
    val removedTextColor = SemanticColors.Diff.removedText

    val allLines = remember(files) {
        files.flatMap { file ->
            val fileHeader = ParsedDiffLine(
                type = ParsedDiffLineType.HEADER,
                content = file.displayFileName,
                oldLineNumber = null,
                newLineNumber = null
            )
            listOf(fileHeader) + file.hunks.flatMap { hunk -> hunk.lines }
        }
    }

    LazyColumn(
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        itemsIndexed(allLines) { _, line ->
            when (line.type) {
                ParsedDiffLineType.HEADER -> {
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
                                    ParsedDiffLineType.ADDED -> addedBgColor
                                    ParsedDiffLineType.REMOVED -> removedBgColor
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = Spacing.hairline)
                    ) {
                        Text(
                            text = (line.oldLineNumber?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(Sizing.diffGutterWidth)
                        )
                        Text(
                            text = (line.newLineNumber?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(Sizing.diffGutterWidth)
                        )
                        Text(
                            text = buildAnnotatedString {
                                val prefix = when (line.type) {
                                    ParsedDiffLineType.ADDED -> "+"
                                    ParsedDiffLineType.REMOVED -> "-"
                                    else -> " "
                                }
                                withStyle(
                                    SpanStyle(
                                        color = when (line.type) {
                                            ParsedDiffLineType.ADDED -> addedTextColor
                                            ParsedDiffLineType.REMOVED -> removedTextColor
                                            else -> theme.text
                                        },
                                        fontWeight = if (line.type != ParsedDiffLineType.CONTEXT) 
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
    files: List<ParsedFileDiff>,
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
        val leftType: ParsedDiffLineType?,
        val rightLineNum: Int?,
        val rightContent: String?,
        val rightType: ParsedDiffLineType?,
        val isHeader: Boolean = false,
        val headerContent: String = ""
    )

    val sideBySideLines = remember(files) {
        val result = mutableListOf<SideBySideLine>()
        
        for (file in files) {
            result.add(SideBySideLine(
                leftLineNum = null, leftContent = null, leftType = null,
                rightLineNum = null, rightContent = null, rightType = null,
                isHeader = true, headerContent = file.displayFileName
            ))

            for (hunk in file.hunks) {
                result.add(SideBySideLine(
                    leftLineNum = null, leftContent = null, leftType = null,
                    rightLineNum = null, rightContent = null, rightType = null,
                    isHeader = true, headerContent = hunk.header
                ))

                val removed = mutableListOf<ParsedDiffLine>()
                val added = mutableListOf<ParsedDiffLine>()

                for (line in hunk.lines) {
                    when (line.type) {
                        ParsedDiffLineType.HEADER -> {}
                        ParsedDiffLineType.CONTEXT -> {
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
                                leftType = ParsedDiffLineType.CONTEXT,
                                rightLineNum = line.newLineNumber,
                                rightContent = line.content,
                                rightType = ParsedDiffLineType.CONTEXT
                            ))
                        }
                        ParsedDiffLineType.REMOVED -> removed.add(line)
                        ParsedDiffLineType.ADDED -> added.add(line)
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
                                    ParsedDiffLineType.REMOVED -> removedBgColor
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = Spacing.hairline)
                    ) {
                        Text(
                            text = (line.leftLineNum?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(Sizing.diffGutterWidth)
                        )
                        Text(
                            text = line.leftContent ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.lg
                            ),
                            color = if (line.leftType == ParsedDiffLineType.REMOVED) 
                                removedTextColor else theme.text,
                            modifier = Modifier.padding(start = Spacing.xs)
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = Sizing.strokeMd,
                        color = theme.border
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                when (line.rightType) {
                                    ParsedDiffLineType.ADDED -> addedBgColor
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = Spacing.hairline)
                    ) {
                        Text(
                            text = (line.rightLineNum?.toString() ?: "").padStart(4),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.md
                            ),
                            color = theme.textMuted,
                            modifier = Modifier.width(Sizing.diffGutterWidth)
                        )
                        Text(
                            text = line.rightContent ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TuiCodeFontSize.lg
                            ),
                            color = if (line.rightType == ParsedDiffLineType.ADDED) 
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
