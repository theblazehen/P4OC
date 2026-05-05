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
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import dev.blazelight.p4oc.ui.components.TuiLoadingIndicator
import dev.blazelight.p4oc.ui.diff.ParsedDiffLine
import dev.blazelight.p4oc.ui.diff.ParsedDiffLineType
import dev.blazelight.p4oc.ui.diff.ParsedDiffParser
import dev.blazelight.p4oc.ui.diff.allHunks

@Composable
fun InlineDiffViewer(
    fileName: String,
    diffContent: String,
    additions: Int = 0,
    deletions: Int = 0,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }
    val diffLines = remember(diffContent) {
        ParsedDiffParser.parse(diffContent).allHunks().flatMap { it.lines }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.backgroundElement,
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
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "≡",
                        color = theme.accent,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = theme.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (additions > 0) {
                        Text(
                            text = "+$additions",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticColors.Diff.addedText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (deletions > 0) {
                        Text(
                            text = "-$deletions",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticColors.Diff.removedText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = if (expanded) "▴" else "▾",
                        color = theme.textMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (expanded && diffLines.isNotEmpty()) {
                HorizontalDivider(color = theme.border)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
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
private fun InlineDiffLineRow(line: ParsedDiffLine) {
    val theme = LocalOpenCodeTheme.current
    
    val (bgColor, textColor, prefix) = when (line.type) {
        ParsedDiffLineType.ADDED -> Triple(
            SemanticColors.Diff.addedBackground,
            SemanticColors.Diff.addedText,
            "+"
        )
        ParsedDiffLineType.REMOVED -> Triple(
            SemanticColors.Diff.removedBackground,
            SemanticColors.Diff.removedText,
            "-"
        )
        ParsedDiffLineType.HEADER -> Triple(
            theme.accent.copy(alpha = 0.1f),
            theme.accent,
            ""
        )
        ParsedDiffLineType.CONTEXT -> Triple(
            Color.Transparent,
            theme.text,
            " "
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        if (line.type != ParsedDiffLineType.HEADER) {
            Text(
                text = (line.newLineNumber ?: line.oldLineNumber)?.toString()?.padStart(4) ?: "    ",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = TuiCodeFontSize.md,
                color = theme.textMuted,
                modifier = Modifier.width(Sizing.diffGutterWidth)
            )
        }
        Text(
            text = if (line.type == ParsedDiffLineType.HEADER) line.content else "$prefix ${line.content}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = TuiCodeFontSize.md,
            color = textColor
        )
    }
}


@Composable
fun PatchDiffViewer(
    files: List<String>,
    getDiffContent: suspend (String) -> String?,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
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
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        files.forEach { file ->
            val isExpanded = expandedFile == file
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = theme.backgroundElement,
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
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "≡",
                                color = theme.accent,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = file,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                color = theme.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isLoading && isExpanded) {
                                TuiLoadingIndicator()
                            } else {
                                Text(
                                    text = if (isExpanded) "▴" else "▾",
                                    color = theme.textMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (isExpanded && diffContent != null) {
                        HorizontalDivider(color = theme.border)
                        
                        val currentDiffContent = diffContent
                        val diffLines = remember(currentDiffContent) {
                            currentDiffContent?.let { ParsedDiffParser.parse(it).allHunks().flatMap { hunk -> hunk.lines } }
                                ?: emptyList()
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
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
