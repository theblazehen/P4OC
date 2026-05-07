package dev.blazelight.p4oc.ui.components.code

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import dev.blazelight.p4oc.ui.theme.opencode.createFallbackTheme
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.Registry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * JVM-only correctness tests for [TextMateAnnotatedString]. Loads the
 * shipped TextMate grammars directly off the filesystem (mirroring
 * `SoraLanguagesManifestDriftTest`) so the test bypasses Android Context
 * entirely — no Robolectric required.
 */
class TextMateAnnotatedStringTest {

    @Test
    fun kotlin_keywords_strings_and_line_comments_are_styled() {
        val src = "fun greet() {\n    val msg = \"hi\"\n    // greet politely\n}\n"
        val out = TextMateAnnotatedString.highlightWithGrammar(src, kotlin, theme)
        assertSpan(out, "fun", theme.syntaxKeyword)
        assertSpan(out, "val", theme.syntaxKeyword)
        // Strings are emitted as several scope segments (open quote, body,
        // close quote); the body always carries the string scope. Match the
        // body slice so this test is robust across grammar revisions.
        assertSpan(out, "hi", theme.syntaxString)
        assertSpan(out, "// greet politely", theme.syntaxComment)
    }

    @Test
    fun python_def_strings_and_hash_comment_are_styled() {
        val src = "def greet():\n    msg = \"hi\"\n    # comment\n"
        val out = TextMateAnnotatedString.highlightWithGrammar(src, python, theme)
        assertSpan(out, "def", theme.syntaxKeyword)
        assertSpan(out, "hi", theme.syntaxString)
        assertSpan(out, "# comment", theme.syntaxComment)
    }

    @Test
    fun typescript_const_string_and_number_are_styled() {
        val src = "const x = \"hi\"\nconst n = 42\n"
        val out = TextMateAnnotatedString.highlightWithGrammar(src, typescript, theme)
        assertSpan(out, "const", theme.syntaxKeyword)
        assertSpan(out, "hi", theme.syntaxString)
        assertSpan(out, "42", theme.syntaxNumber)
    }

    @Test
    fun markdown_heading_and_fenced_block_tokens_apply_color() {
        val src = "# Title\n\n```\ncode\n```\n"
        val out = TextMateAnnotatedString.highlightWithGrammar(src, markdown, theme)
        // Asserts the heading line picks up at least one styled span. The
        // markdown grammar threads cross-line state through the fenced block,
        // which is what makes a single Registry per grammar viable here.
        val titleStart = out.text.indexOf("# Title")
        val titleEnd = titleStart + "# Title".length
        val hasHeadingSpan = out.spanStyles.any { it.start < titleEnd && it.end > titleStart }
        assertTrue("expected at least one styled span overlapping '# Title'", hasHeadingSpan)
    }

    @Test
    fun null_grammar_returns_plain_annotated_string() {
        val out = TextMateAnnotatedString.highlightWithGrammar("anything", null, theme)
        assertTrue(out.spanStyles.isEmpty())
    }

    private fun assertSpan(annotated: AnnotatedString, find: String, expected: Color) {
        val text = annotated.text
        val start = text.indexOf(find)
        assertTrue("substring '$find' not found in highlighted output", start >= 0)
        val end = start + find.length
        // Every character in [start,end) must be covered by at least one span
        // of the expected color. TM4E often emits adjacent spans for adjacent
        // tokens (e.g. `#` punctuation + ` comment` body), so we allow union
        // coverage rather than requiring a single span.
        for (pos in start until end) {
            val covered = annotated.spanStyles.any { r ->
                r.start <= pos && r.end > pos && r.item.color == expected
            }
            if (!covered) {
                val overlapping = annotated.spanStyles.filter { it.start < end && it.end > start }
                error("char at $pos in '$find' not covered by SpanStyle($expected); overlapping spans: $overlapping")
            }
        }
    }

    private companion object {
        val theme = createFallbackTheme(isDark = true)

        // Resolved & loaded once via @BeforeClass; held as nullable lateinits
        // because @BeforeClass requires @JvmStatic on a static-init path.
        lateinit var kotlin: IGrammar
        lateinit var python: IGrammar
        lateinit var typescript: IGrammar
        lateinit var markdown: IGrammar

        private fun resolveAssets(): File {
            val candidates = listOf(
                "src/main/assets/textmate",
                "app/src/main/assets/textmate",
            )
            for (c in candidates) {
                val f = File(c)
                if (f.isDirectory) return f
            }
            var dir: File? = File("").absoluteFile
            repeat(5) {
                val probe = File(dir, "app/src/main/assets/textmate")
                if (probe.isDirectory) return probe
                dir = dir?.parentFile
            }
            error("textmate assets not found (cwd=" + File("").absolutePath + ")")
        }

        private fun load(registry: Registry, file: File): IGrammar {
            val content = file.readText(StandardCharsets.UTF_8)
            return registry.addGrammar(IGrammarSource.fromString(IGrammarSource.ContentType.JSON, content))
        }

        @JvmStatic
        @BeforeClass
        fun loadGrammars() {
            val root = resolveAssets()
            // Each grammar gets its own Registry. Markdown's fenced-code
            // patterns include cross-grammar scopes such as `source.python`
            // and `source.ts`; those will not resolve in this isolated
            // Registry, but our assertions only cover heading/fenced-block
            // tokens that the markdown grammar handles internally, so a
            // hermetic per-grammar Registry is sufficient.
            kotlin = load(Registry(), File(root, "kotlin/kotlin.tmLanguage.json"))
            python = load(Registry(), File(root, "python/python.tmLanguage.json"))
            typescript = load(Registry(), File(root, "typescript/typescript.tmLanguage.json"))
            markdown = load(Registry(), File(root, "markdown/markdown.tmLanguage.json"))
            assertNotNull(kotlin); assertNotNull(python); assertNotNull(typescript); assertNotNull(markdown)
        }
    }
}
