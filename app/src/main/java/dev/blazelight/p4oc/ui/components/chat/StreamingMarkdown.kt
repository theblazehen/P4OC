package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes

/**
 * Chat-oriented markdown renderer.
 *
 * Agent output is not article markdown: code, tables, logs, and partial streaming
 * content need bounded layout and stable recomposition. This renderer keeps parsing
 * line-oriented and cheap, owns scroll containers for wide blocks, and memoizes the
 * expensive syntax-highlight conversion by code/language/theme.
 */
@Composable
fun StreamingMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    useTertiaryColors: Boolean = false,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val theme = LocalOpenCodeTheme.current
    val colors = MarkdownRenderColors(
        text = if (useTertiaryColors) theme.success else theme.markdownText,
        muted = theme.textMuted,
        border = theme.border,
        codeBackground = theme.backgroundElement,
        inlineCodeBackground = theme.backgroundPanel,
        link = theme.info,
    )
    val highlighter = remember(isDarkTheme) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = isDarkTheme))
    }
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            blocks.forEach { block ->
                key(block.key) {
                    when (block) {
                        is MarkdownBlock.Paragraph -> MarkdownParagraph(block.lines.joinToString("\n"), colors)
                        is MarkdownBlock.Heading -> MarkdownText(
                            text = inlineMarkdown(block.text, colors),
                            style = headingStyle(block.level),
                            colors = colors,
                        )
                        is MarkdownBlock.ListBlock -> MarkdownList(block.items, colors)
                        is MarkdownBlock.Quote -> MarkdownQuote(block.lines, colors)
                        is MarkdownBlock.CodeFence -> CodeFenceBlock(block.code, block.language, highlighter, colors)
                        is MarkdownBlock.Table -> MarkdownTableBlock(block.rows, colors)
                        is MarkdownBlock.Rule -> HorizontalRule(colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(text: String, colors: MarkdownRenderColors) {
    MarkdownText(
        text = inlineMarkdown(text.trim(), colors),
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
        colors = colors,
    )
}

@Composable
private fun MarkdownText(
    text: AnnotatedString,
    style: TextStyle,
    colors: MarkdownRenderColors,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = style,
        color = colors.text,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun MarkdownList(items: List<MarkdownListItem>, colors: MarkdownRenderColors) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = item.marker,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = colors.muted,
                )
                Text(
                    text = inlineMarkdown(item.text, colors),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MarkdownQuote(lines: List<String>, colors: MarkdownRenderColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .width(Sizing.strokeThick)
                .background(colors.border)
                .padding(vertical = Spacing.xs)
        )
        Text(
            text = inlineMarkdown(lines.joinToString("\n"), colors),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = colors.muted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CodeFenceBlock(
    code: String,
    language: String?,
    highlighter: Highlights.Builder,
    colors: MarkdownRenderColors,
) {
    val languageName = language?.takeIf { it.isNotBlank() }
    val highlighted = remember(code, languageName, highlighter) {
        highlightCode(code, languageName, highlighter, colors.text)
    }

    BoundedHorizontalBlock(colors = colors) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = highlighted,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MarkdownTableBlock(rows: List<List<String>>, colors: MarkdownRenderColors) {
    val columnWidths = remember(rows) {
        val columnCount = rows.maxOfOrNull { it.size } ?: 0
        List(columnCount) { columnIndex ->
            tableCellWidth(rows.maxOfOrNull { row -> row.getOrNull(columnIndex)?.length ?: 0 } ?: 0)
        }
    }

    BoundedHorizontalBlock(colors = colors) {
        Column {
            rows.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    columnWidths.forEachIndexed { columnIndex, width ->
                        val cell = row.getOrNull(columnIndex).orEmpty()
                        Text(
                            text = inlineMarkdown(cell, colors),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = colors.text,
                            modifier = Modifier
                                .width(width)
                                .fillMaxHeight()
                                .border(Sizing.strokeThin, colors.border)
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        )
                    }
                }
                if (rowIndex == 0 && rows.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.border)
                            .padding(top = Sizing.strokeThin)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoundedHorizontalBlock(
    colors: MarkdownRenderColors,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .width(maxWidth)
                .background(colors.codeBackground)
                .horizontalScroll(scrollState)
                .padding(Spacing.sm)
        ) {
            content()
        }
    }
}

@Composable
private fun HorizontalRule(colors: MarkdownRenderColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .background(colors.border)
            .padding(top = Sizing.dividerThickness)
    )
}

private fun highlightCode(
    code: String,
    language: String?,
    highlighter: Highlights.Builder,
    fallbackColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val syntax = language?.let { SyntaxLanguage.getByName(it) } ?: SyntaxLanguage.DEFAULT
    val highlights = runCatching {
        Highlights.Builder()
            .theme(highlighter.theme)
            .language(syntax)
            .code(code)
            .build()
            .getHighlights()
    }.getOrDefault(emptyList())
        .filterIsInstance<ColorHighlight>()
        .sortedBy { it.location.start }

    return buildAnnotatedString {
        var cursor = 0
        highlights.forEach { highlight ->
            val start = highlight.location.start.coerceIn(0, code.length)
            val end = highlight.location.end.coerceIn(start, code.length)
            if (cursor < start) append(code.substring(cursor, start))
            withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(highlight.rgb).copy(alpha = 1f))) {
                append(code.substring(start, end))
            }
            cursor = end
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = fallbackColor)) {
                append(code.substring(cursor))
            }
        }
    }
}

private fun inlineMarkdown(text: String, colors: MarkdownRenderColors): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val codeStart = text.indexOf('`', index)
        val linkStart = text.indexOf('[', index)
        val boldStart = text.indexOf("**", index)
        val next = listOf(codeStart, linkStart, boldStart).filter { it >= 0 }.minOrNull() ?: -1
        if (next == -1) {
            appendInline(text.substring(index), colors)
            break
        }
        if (next > index) appendInline(text.substring(index, next), colors)
        when (next) {
            codeStart -> {
                val end = text.indexOf('`', codeStart + 1)
                if (end == -1) {
                    appendInline(text.substring(codeStart), colors)
                    break
                }
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = colors.inlineCodeBackground,
                    )
                ) {
                    append(text.substring(codeStart + 1, end))
                }
                index = end + 1
            }
            boldStart -> {
                val end = text.indexOf("**", boldStart + 2)
                if (end == -1) {
                    appendInline(text.substring(boldStart), colors)
                    break
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(boldStart + 2, end))
                }
                index = end + 2
            }
            else -> {
                val close = text.indexOf(']', linkStart + 1)
                val openUrl = if (close != -1) text.indexOf('(', close + 1) else -1
                val closeUrl = if (openUrl != -1 && openUrl == close + 1) text.indexOf(')', openUrl + 1) else -1
                if (closeUrl == -1) {
                    appendInline(text.substring(linkStart, linkStart + 1), colors)
                    index = linkStart + 1
                } else {
                    val label = text.substring(linkStart + 1, close)
                    val url = text.substring(openUrl + 1, closeUrl)
                    if (url.isHttpUrl()) {
                        withLink(LinkAnnotation.Url(url, linkStyles(colors))) {
                            append(label)
                        }
                    } else {
                        withStyle(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)) {
                            append(label)
                        }
                    }
                    index = closeUrl + 1
                }
            }
        }
    }
}

private val URL_REGEX = Regex("""https?://[^\s<>()\[\]{}]+""")
private const val URL_TRAILING_TRIM = ".,;:!?\"'”’"

private fun linkStyles(colors: MarkdownRenderColors) = TextLinkStyles(
    style = SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline),
)

private fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")

private fun AnnotatedString.Builder.appendInline(text: String, colors: MarkdownRenderColors) {
    if (text.isEmpty()) return
    var cursor = 0
    for (match in URL_REGEX.findAll(text)) {
        val start = match.range.first
        var end = match.range.last + 1
        while (end > start && text[end - 1] in URL_TRAILING_TRIM) end--
        if (start > cursor) append(text.substring(cursor, start))
        val url = text.substring(start, end)
        withLink(LinkAnnotation.Url(url, linkStyles(colors))) {
            append(url)
        }
        cursor = end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    val typography = MaterialTheme.typography
    return when (level) {
        1 -> typography.titleLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 28.sp)
        2 -> typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 24.sp)
        3 -> typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
        else -> typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    }
}

private fun tableCellWidth(contentLength: Int) = when {
    contentLength <= 8 -> 80.dp
    contentLength <= 18 -> 140.dp
    contentLength <= 36 -> 220.dp
    else -> 320.dp
}

private data class MarkdownRenderColors(
    val text: androidx.compose.ui.graphics.Color,
    val muted: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
    val codeBackground: androidx.compose.ui.graphics.Color,
    val inlineCodeBackground: androidx.compose.ui.graphics.Color,
    val link: androidx.compose.ui.graphics.Color,
)

internal sealed interface MarkdownBlock {
    val key: String

    data class Paragraph(val startLine: Int, val lines: List<String>) : MarkdownBlock {
        override val key = "p:$startLine"
    }

    data class Heading(val startLine: Int, val level: Int, val text: String) : MarkdownBlock {
        override val key = "h:$startLine"
    }

    data class ListBlock(val startLine: Int, val ordered: Boolean, val items: List<MarkdownListItem>) : MarkdownBlock {
        override val key = "l:$startLine"
    }

    data class Quote(val startLine: Int, val lines: List<String>) : MarkdownBlock {
        override val key = "q:$startLine"
    }

    data class CodeFence(val startLine: Int, val language: String?, val code: String) : MarkdownBlock {
        override val key = "c:$startLine"
    }

    data class Table(val startLine: Int, val rows: List<List<String>>) : MarkdownBlock {
        override val key = "t:$startLine"
    }

    data class Rule(val startLine: Int) : MarkdownBlock {
        override val key = "r:$startLine"
    }
}

internal fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()

    val lines = text.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index++
            continue
        }

        val trimmed = line.trim()
        val startLine = index

        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim().substringBefore(' ').ifBlank { null }
            index++
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                codeLines += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += MarkdownBlock.CodeFence(startLine, language, codeLines.joinToString("\n"))
            continue
        }

        parseHeading(trimmed)?.let { (level, headingText) ->
            blocks += MarkdownBlock.Heading(startLine, level, headingText)
            index++
            continue
        }

        if (isHorizontalRule(trimmed)) {
            blocks += MarkdownBlock.Rule(startLine)
            index++
            continue
        }

        parseTable(lines, index)?.let { table ->
            blocks += MarkdownBlock.Table(startLine, table.rows)
            index = table.nextIndex
            continue
        }

        parseListItem(line)?.let { firstItem ->
            val ordered = firstItem.ordered
            val items = mutableListOf(firstItem.toMarkdownListItem())
            index++
            while (index < lines.size) {
                val next = parseListItem(lines[index])
                if (next == null || next.ordered != ordered) break
                items += next.toMarkdownListItem()
                index++
            }
            blocks += MarkdownBlock.ListBlock(startLine, ordered, items)
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf(trimmed.removePrefix(">").trimStart())
            index++
            while (index < lines.size && lines[index].trim().startsWith(">")) {
                quoteLines += lines[index].trim().removePrefix(">").trimStart()
                index++
            }
            blocks += MarkdownBlock.Quote(startLine, quoteLines)
            continue
        }

        val paragraph = mutableListOf(line)
        index++
        while (index < lines.size && lines[index].isNotBlank() && !startsSpecialBlock(lines, index)) {
            paragraph += lines[index]
            index++
        }
        blocks += MarkdownBlock.Paragraph(startLine, paragraph)
    }

    return blocks
}

private fun startsSpecialBlock(lines: List<String>, index: Int): Boolean {
    val trimmed = lines[index].trim()
    return trimmed.startsWith("```") ||
        parseHeading(trimmed) != null ||
        isHorizontalRule(trimmed) ||
        parseListItem(lines[index]) != null ||
        trimmed.startsWith(">") ||
        parseTable(lines, index) != null
}

private fun parseHeading(trimmed: String): Pair<Int, String>? {
    val hashes = trimmed.takeWhile { it == '#' }.length
    if (hashes !in 1..6 || trimmed.getOrNull(hashes) != ' ') return null
    return hashes to trimmed.drop(hashes).trim()
}

private fun isHorizontalRule(trimmed: String): Boolean {
    if (trimmed.length < 3) return false
    return trimmed.all { it == '-' } || trimmed.all { it == '*' } || trimmed.all { it == '_' }
}

internal data class MarkdownListItem(val marker: String, val text: String)

private data class ListItem(val ordered: Boolean, val marker: String, val text: String) {
    fun toMarkdownListItem(): MarkdownListItem = MarkdownListItem(marker = marker, text = text)
}

private fun parseListItem(line: String): ListItem? {
    val trimmed = line.trimStart()
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) return ListItem(false, "•", trimmed.drop(2).trim())
    val dot = trimmed.indexOf(". ")
    if (dot > 0 && trimmed.take(dot).all { it.isDigit() }) {
        return ListItem(true, trimmed.take(dot + 1), trimmed.drop(dot + 2).trim())
    }
    return null
}

private data class ParsedTable(val rows: List<List<String>>, val nextIndex: Int)

private fun parseTable(lines: List<String>, start: Int): ParsedTable? {
    if (start + 1 >= lines.size) return null
    val header = parseTableRow(lines[start]) ?: return null
    val delimiter = parseTableDelimiter(lines[start + 1], header.size) ?: return null
    val rows = mutableListOf(header)
    var index = start + 2
    while (index < lines.size) {
        val row = parseTableRow(lines[index]) ?: break
        if (row.size == delimiter) rows += row else rows += row.padTo(delimiter)
        index++
    }
    return ParsedTable(rows, index)
}

private fun parseTableRow(line: String): List<String>? {
    val trimmed = line.trim()
    if (!trimmed.contains('|')) return null
    val body = trimmed.trim('|')
    val cells = body.split('|').map { it.trim() }
    return cells.takeIf { it.size >= 2 }
}

private fun parseTableDelimiter(line: String, expectedCells: Int): Int? {
    val cells = parseTableRow(line) ?: return null
    if (cells.size != expectedCells) return null
    return cells.size.takeIf {
        cells.all { cell ->
            cell.trim().trim(':').all { it == '-' } && cell.count { it == '-' } >= 3
        }
    }
}

private fun List<String>.padTo(size: Int): List<String> = when {
    this.size == size -> this
    this.size > size -> this.take(size)
    else -> this + List(size - this.size) { "" }
}

/**
 * Convenience overload for tertiary-styled markdown (e.g., reasoning blocks).
 */
@Composable
fun TertiaryStreamingMarkdown(
    text: String,
    modifier: Modifier = Modifier,
) {
    StreamingMarkdown(
        text = text,
        modifier = modifier,
        useTertiaryColors = true,
    )
}
