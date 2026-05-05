package dev.blazelight.p4oc.ui.diff

import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.Patch

internal data class ParsedDiff(
    val files: List<ParsedFileDiff>
)

internal data class ParsedFileDiff(
    val oldFileName: String?,
    val newFileName: String?,
    val displayFileName: String,
    val hunks: List<ParsedHunk>
)

internal data class ParsedHunk(
    val header: String,
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val lines: List<ParsedDiffLine>
)

internal data class ParsedDiffLine(
    val type: ParsedDiffLineType,
    val content: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?
)

internal enum class ParsedDiffLineType {
    CONTEXT,
    ADDED,
    REMOVED,
    HEADER
}

internal object ParsedDiffParser {
    private val hunkHeaderRegex = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*$""")

    fun parse(diffContent: String): ParsedDiff {
        if (diffContent.isBlank()) return ParsedDiff(emptyList())

        val sections = splitIntoFileSections(diffContent.lines())
        val files = sections.mapNotNull { parseFileSection(it) }
        return ParsedDiff(files)
    }

    private fun splitIntoFileSections(lines: List<String>): List<List<String>> {
        val sections = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var insideHunk = false

        fun flush() {
            if (current.any { it.startsWith("@@") || it.startsWith("--- ") || it.startsWith("+++ ") }) {
                sections.add(current.toList())
            }
            current = mutableListOf()
            insideHunk = false
        }

        for (line in lines) {
            val startsNewGitFile = line.startsWith("diff --git ") && current.isNotEmpty()
            val startsNewPlainFile = line.startsWith("--- ") && current.isNotEmpty() && !insideHunk
            if (startsNewGitFile || startsNewPlainFile) {
                flush()
            }

            current.add(line)

            if (line.startsWith("@@")) {
                insideHunk = true
            }
        }

        flush()
        return sections
    }

    private fun parseFileSection(lines: List<String>): ParsedFileDiff? {
        val headerInfo = extractFileHeaders(lines)
        val hunkHeaderCount = lines.count { it.startsWith("@@") }
        val canonicalPatch = canonicalPatchFor(lines, headerInfo, hunkHeaderCount) ?: return null

        val hunks = parseHunks(lines)
        if (hunks.isEmpty()) return null
        if (canonicalPatch.getDeltas().size != hunks.size) return null

        return ParsedFileDiff(
            oldFileName = headerInfo.oldFileName,
            newFileName = headerInfo.newFileName,
            displayFileName = displayFileName(headerInfo.newFileName, headerInfo.oldFileName),
            hunks = hunks
        )
    }

    private fun canonicalPatchFor(
        lines: List<String>,
        headerInfo: FileHeaderInfo,
        hunkHeaderCount: Int
    ): Patch<String>? {
        if (hunkHeaderCount == 0) return null

        val linesForLibrary = if (headerInfo.hasBothHeaders) {
            lines
        } else {
            // Lenient fallback for headerless unified hunks emitted by some tools. The synthetic
            // file headers are only for java-diff-utils; renderer rows still come from original lines.
            listOf("--- original", "+++ revised") + lines.dropWhile { !it.startsWith("@@") }
        }

        val patch = try {
            UnifiedDiffUtils.parseUnifiedDiff(linesForLibrary)
        } catch (_: RuntimeException) {
            return null
        }

        return if (patch.getDeltas().size == hunkHeaderCount) patch else null
    }

    private fun extractFileHeaders(lines: List<String>): FileHeaderInfo {
        var oldFileName: String? = null
        var newFileName: String? = null
        var sawOldHeader = false
        var sawNewHeader = false
        var insideHunk = false

        for (line in lines) {
            when {
                line.startsWith("@@") -> insideHunk = true
                !insideHunk && line.startsWith("--- ") -> {
                    sawOldHeader = true
                    oldFileName = normalizeFileName(line.removePrefix("--- "))
                }
                !insideHunk && line.startsWith("+++ ") -> {
                    sawNewHeader = true
                    newFileName = normalizeFileName(line.removePrefix("+++ "))
                }
            }
        }

        return FileHeaderInfo(
            oldFileName = oldFileName,
            newFileName = newFileName,
            hasBothHeaders = sawOldHeader && sawNewHeader
        )
    }

    private fun parseHunks(lines: List<String>): List<ParsedHunk> {
        val hunks = mutableListOf<ParsedHunk>()
        var currentHunk: HunkBuilder? = null
        var insideHunk = false

        fun flushHunk() {
            currentHunk?.build()?.let { hunks.add(it) }
            currentHunk = null
        }

        for (line in lines) {
            when {
                line.startsWith("@@") -> {
                    flushHunk()
                    val parsedHeader = parseHunkHeader(line) ?: continue
                    currentHunk = HunkBuilder(
                        header = line,
                        oldStart = parsedHeader.oldStart,
                        oldCount = parsedHeader.oldCount,
                        newStart = parsedHeader.newStart,
                        newCount = parsedHeader.newCount
                    )
                    currentHunk?.addHeaderLine()
                    insideHunk = true
                }
                insideHunk && line.startsWith("\\") -> Unit
                insideHunk -> currentHunk?.addDiffLine(line)
            }
        }

        flushHunk()
        return hunks
    }

    private fun parseHunkHeader(header: String): ParsedHunkHeader? {
        val match = hunkHeaderRegex.find(header) ?: return null
        return ParsedHunkHeader(
            oldStart = match.groupValues[1].toIntOrNull() ?: return null,
            oldCount = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1,
            newStart = match.groupValues[3].toIntOrNull() ?: return null,
            newCount = match.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
        )
    }

    private fun normalizeFileName(rawFileName: String): String? {
        val withoutTimestamp = rawFileName.substringBefore('\t').substringBefore("  ").trim()
        if (withoutTimestamp.isEmpty() || withoutTimestamp == "/dev/null") return null
        return withoutTimestamp.removePrefix("a/").removePrefix("b/")
    }

    private fun displayFileName(newFileName: String?, oldFileName: String?): String =
        newFileName ?: oldFileName.orEmpty()

    private data class FileHeaderInfo(
        val oldFileName: String?,
        val newFileName: String?,
        val hasBothHeaders: Boolean
    )

    private data class ParsedHunkHeader(
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int
    )

    private class HunkBuilder(
        private val header: String,
        private val oldStart: Int,
        private val oldCount: Int,
        private val newStart: Int,
        private val newCount: Int
    ) {
        private val lines = mutableListOf<ParsedDiffLine>()
        private var oldLine = oldStart.coerceAtLeast(1)
        private var newLine = newStart.coerceAtLeast(1)

        fun addHeaderLine() {
            lines.add(
                ParsedDiffLine(
                    type = ParsedDiffLineType.HEADER,
                    content = header,
                    oldLineNumber = null,
                    newLineNumber = null
                )
            )
        }

        fun addDiffLine(line: String) {
            if (line.startsWith("\\")) return

            when {
                line.startsWith("+") -> {
                    lines.add(
                        ParsedDiffLine(
                            type = ParsedDiffLineType.ADDED,
                            content = line.drop(1),
                            oldLineNumber = null,
                            newLineNumber = newLine++
                        )
                    )
                }
                line.startsWith("-") -> {
                    lines.add(
                        ParsedDiffLine(
                            type = ParsedDiffLineType.REMOVED,
                            content = line.drop(1),
                            oldLineNumber = oldLine++,
                            newLineNumber = null
                        )
                    )
                }
                line.startsWith(" ") -> {
                    val content = line.drop(1)
                    lines.add(
                        ParsedDiffLine(
                            type = ParsedDiffLineType.CONTEXT,
                            content = content,
                            oldLineNumber = oldLine++,
                            newLineNumber = newLine++
                        )
                    )
                }
                line.isEmpty() -> {
                    lines.add(
                        ParsedDiffLine(
                            type = ParsedDiffLineType.CONTEXT,
                            content = "",
                            oldLineNumber = oldLine++,
                            newLineNumber = newLine++
                        )
                    )
                }
            }
        }

        fun build(): ParsedHunk = ParsedHunk(
            header = header,
            oldStart = oldStart,
            oldCount = oldCount,
            newStart = newStart,
            newCount = newCount,
            lines = lines.toList()
        )
    }
}

internal fun ParsedDiff.primaryFile(): ParsedFileDiff? = files.firstOrNull()

internal fun ParsedDiff.allHunks(): List<ParsedHunk> = files.flatMap { it.hunks }
