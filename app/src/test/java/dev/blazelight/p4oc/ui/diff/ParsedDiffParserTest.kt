package dev.blazelight.p4oc.ui.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedDiffParserTest {
    @Test
    fun parsesSimpleModifiedFile() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/foo.kt
            +++ b/foo.kt
            @@ -1,3 +1,3 @@ fun main
             fun main() {
            -    println("old")
            +    println("new")
             }
            """.trimIndent()
        )

        assertEquals(1, parsed.files.size)
        val file = parsed.files.single()
        assertEquals("foo.kt", file.oldFileName)
        assertEquals("foo.kt", file.newFileName)
        assertEquals("foo.kt", file.displayFileName)
        assertEquals(1, file.hunks.size)

        val lines = file.hunks.single().lines
        assertEquals(ParsedDiffLineType.HEADER, lines[0].type)
        assertEquals("@@ -1,3 +1,3 @@ fun main", lines[0].content)
        assertEquals(ParsedDiffLineType.CONTEXT, lines[1].type)
        assertEquals(1, lines[1].oldLineNumber)
        assertEquals(1, lines[1].newLineNumber)
        assertEquals(ParsedDiffLineType.REMOVED, lines[2].type)
        assertEquals(2, lines[2].oldLineNumber)
        assertEquals(null, lines[2].newLineNumber)
        assertEquals("    println(\"old\")", lines[2].content)
        assertEquals(ParsedDiffLineType.ADDED, lines[3].type)
        assertEquals(null, lines[3].oldLineNumber)
        assertEquals(2, lines[3].newLineNumber)
        assertEquals("    println(\"new\")", lines[3].content)
    }

    @Test
    fun parsesMultipleHunksWithIndependentLineNumbers() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/foo.kt
            +++ b/foo.kt
            @@ -1,2 +1,2 @@
             one
            -two
            +TWO
            @@ -10,2 +10,2 @@
             ten
            -eleven
            +ELEVEN
            """.trimIndent()
        )

        val hunks = parsed.files.single().hunks
        assertEquals(2, hunks.size)
        assertEquals(1, hunks[0].oldStart)
        assertEquals(1, hunks[0].newStart)
        assertEquals(10, hunks[1].oldStart)
        assertEquals(10, hunks[1].newStart)
        assertEquals(11, hunks[1].lines[2].oldLineNumber)
        assertEquals(11, hunks[1].lines[3].newLineNumber)
    }

    @Test
    fun parsesAddedFileFromDevNull() {
        val parsed = ParsedDiffParser.parse(
            """
            --- /dev/null
            +++ b/new.kt
            @@ -0,0 +1,2 @@
            +first
            +second
            """.trimIndent()
        )

        val file = parsed.files.single()
        assertEquals(null, file.oldFileName)
        assertEquals("new.kt", file.newFileName)
        assertEquals("new.kt", file.displayFileName)
        assertEquals(1, file.hunks.single().lines[1].newLineNumber)
        assertEquals(2, file.hunks.single().lines[2].newLineNumber)
    }

    @Test
    fun parsesDeletedFileToDevNull() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/old.kt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -first
            -second
            """.trimIndent()
        )

        val file = parsed.files.single()
        assertEquals("old.kt", file.oldFileName)
        assertEquals(null, file.newFileName)
        assertEquals("old.kt", file.displayFileName)
        assertEquals(1, file.hunks.single().lines[1].oldLineNumber)
        assertEquals(2, file.hunks.single().lines[2].oldLineNumber)
    }

    @Test
    fun parsesMultiFileDiff() {
        val parsed = ParsedDiffParser.parse(
            """
            diff --git a/one.kt b/one.kt
            --- a/one.kt
            +++ b/one.kt
            @@ -1 +1 @@
            -one
            +ONE
            diff --git a/two.kt b/two.kt
            --- a/two.kt
            +++ b/two.kt
            @@ -2 +2 @@
            -two
            +TWO
            """.trimIndent()
        )

        assertEquals(2, parsed.files.size)
        assertEquals("one.kt", parsed.files[0].displayFileName)
        assertEquals("two.kt", parsed.files[1].displayFileName)
        assertEquals(2, parsed.files[1].hunks.single().oldStart)
    }

    @Test
    fun skipsNoNewlineMarker() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/foo.txt
            +++ b/foo.txt
            @@ -1 +1 @@
            -old
            \ No newline at end of file
            +new
            \ No newline at end of file
            """.trimIndent()
        )

        val nonHeaderLines = parsed.files.single().hunks.single().lines.drop(1)
        assertEquals(2, nonHeaderLines.size)
        assertTrue(nonHeaderLines.none { it.content.contains("No newline") })
    }

    @Test
    fun keepsRemovedAndAddedContentThatLooksLikeFileHeadersInsideHunks() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/doc.md
            +++ b/doc.md
            @@ -1,2 +1,2 @@
            ---- markdown heading
            ++++ markdown heading
             body
            """.trimIndent()
        )

        val lines = parsed.files.single().hunks.single().lines
        assertEquals(ParsedDiffLineType.REMOVED, lines[1].type)
        assertEquals("--- markdown heading", lines[1].content)
        assertEquals(ParsedDiffLineType.ADDED, lines[2].type)
        assertEquals("+++ markdown heading", lines[2].content)
    }

    @Test
    fun parsesHeaderlessHunkWithSyntheticLibraryHeaders() {
        val parsed = ParsedDiffParser.parse(
            """
            @@ -1 +1 @@
            -old
            +new
            """.trimIndent()
        )

        val file = parsed.files.single()
        assertEquals("", file.displayFileName)
        val lines = file.hunks.single().lines
        assertEquals(ParsedDiffLineType.REMOVED, lines[1].type)
        assertEquals(ParsedDiffLineType.ADDED, lines[2].type)
    }

    @Test
    fun preservesContextLeadingWhitespaceAfterDiffPrefix() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/foo.txt
            +++ b/foo.txt
            @@ -1 +1 @@
                 indented context
            """.trimIndent()
        )

        val context = parsed.files.single().hunks.single().lines[1]
        assertEquals(ParsedDiffLineType.CONTEXT, context.type)
        assertEquals("    indented context", context.content)
    }

    @Test
    fun parsesCrLfInputSafely() {
        val diff = "--- a/foo.txt\r\n+++ b/foo.txt\r\n@@ -1 +1 @@\r\n-old\r\n+new\r\n"
        val parsed = ParsedDiffParser.parse(diff)

        val lines = parsed.files.single().hunks.single().lines
        assertEquals("old", lines[1].content)
        assertEquals("new", lines[2].content)
    }

    @Test
    fun truncatedInputDoesNotThrow() {
        val parsed = ParsedDiffParser.parse("--- a/foo.txt\n+++ b/foo.txt\n@@ -1,2 +1,2 @@\n-old")

        assertNotNull(parsed)
    }

    @Test
    fun malformedInputDoesNotThrow() {
        val parsed = ParsedDiffParser.parse("not a diff\n+++ also not enough\n@@ broken")

        assertNotNull(parsed)
        assertEquals(emptyList<ParsedFileDiff>(), parsed.files)
    }

    @Test
    fun supportsCountlessHunkHeaders() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/foo.txt
            +++ b/foo.txt
            @@ -1 +1 @@
            -old
            +new
            """.trimIndent()
        )

        val hunk = parsed.files.single().hunks.single()
        assertEquals(1, hunk.oldStart)
        assertEquals(1, hunk.oldCount)
        assertEquals(1, hunk.newStart)
        assertEquals(1, hunk.newCount)
    }

    @Test
    fun preservesLeadingWhitespaceAfterDiffPrefix() {
        val parsed = ParsedDiffParser.parse(
            """
            --- a/foo.txt
            +++ b/foo.txt
            @@ -1 +1 @@
            -    old
            +    new
            """.trimIndent()
        )

        val lines = parsed.files.single().hunks.single().lines
        assertEquals("    old", lines[1].content)
        assertEquals("    new", lines[2].content)
    }
}
