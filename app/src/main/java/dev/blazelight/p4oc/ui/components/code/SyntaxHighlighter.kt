package dev.blazelight.p4oc.ui.components.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Language-specific syntax highlighting rules
 */
enum class Language(val extensions: List<String>) {
    KOTLIN(listOf("kt", "kts")),
    JAVA(listOf("java")),
    JAVASCRIPT(listOf("js", "jsx", "mjs")),
    TYPESCRIPT(listOf("ts", "tsx")),
    PYTHON(listOf("py", "pyw")),
    RUST(listOf("rs")),
    GO(listOf("go")),
    C(listOf("c", "h")),
    CPP(listOf("cpp", "cc", "cxx", "hpp", "hh")),
    CSHARP(listOf("cs")),
    SWIFT(listOf("swift")),
    RUBY(listOf("rb")),
    PHP(listOf("php")),
    HTML(listOf("html", "htm")),
    CSS(listOf("css", "scss", "sass", "less")),
    JSON(listOf("json")),
    XML(listOf("xml", "xsl", "xslt")),
    YAML(listOf("yaml", "yml")),
    MARKDOWN(listOf("md", "markdown")),
    SQL(listOf("sql")),
    SHELL(listOf("sh", "bash", "zsh", "fish")),
    DOCKERFILE(listOf("dockerfile")),
    GRADLE(listOf("gradle")),
    UNKNOWN(emptyList());

    companion object {
        fun fromFilename(filename: String): Language {
            val ext = filename.substringAfterLast(".", "").lowercase()
            val name = filename.lowercase()
            
            // Special cases for files without extensions
            if (name == "dockerfile") return DOCKERFILE
            if (name == "makefile" || name == "gnumakefile") return SHELL
            if (name.endsWith(".gradle.kts")) return KOTLIN
            
            return entries.find { it.extensions.contains(ext) } ?: UNKNOWN
        }
    }
}

/**
 * Color scheme for syntax highlighting
 */
data class SyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val type: Color,
    val annotation: Color,
    val operator: Color,
    val property: Color,
    val variable: Color,
    val tag: Color,
    val attribute: Color,
    val lineNumber: Color,
    val lineNumberBackground: Color
) {
    companion object {
        @Composable
        fun default(): SyntaxColors = SyntaxColors(
            keyword = Color(0xFFCC7832),        // Orange
            string = Color(0xFF6A8759),          // Green
            number = Color(0xFF6897BB),          // Blue
            comment = Color(0xFF808080),         // Gray
            function = Color(0xFFFFC66D),        // Yellow
            type = Color(0xFFA9B7C6),            // Light gray-blue
            annotation = Color(0xFFBBB529),      // Yellow-green
            operator = Color(0xFFA9B7C6),        // Light gray
            property = Color(0xFF9876AA),        // Purple
            variable = Color(0xFFA9B7C6),        // Light gray-blue
            tag = Color(0xFFE8BF6A),             // Gold
            attribute = Color(0xFFBABABA),       // Light gray
            lineNumber = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            lineNumberBackground = MaterialTheme.colorScheme.surfaceVariant
        )
        
        @Composable
        fun light(): SyntaxColors = SyntaxColors(
            keyword = Color(0xFF0000FF),         // Blue
            string = Color(0xFF008000),          // Green
            number = Color(0xFF098658),          // Teal
            comment = Color(0xFF808080),         // Gray
            function = Color(0xFF795E26),        // Brown
            type = Color(0xFF267F99),            // Cyan
            annotation = Color(0xFF808000),      // Olive
            operator = Color(0xFF000000),        // Black
            property = Color(0xFF001080),        // Dark blue
            variable = Color(0xFF001080),        // Dark blue
            tag = Color(0xFF800000),             // Maroon
            attribute = Color(0xFFFF0000),       // Red
            lineNumber = Color(0xFF999999),
            lineNumberBackground = Color(0xFFF5F5F5)
        )
    }
}

/**
 * Syntax highlighter that converts code text to annotated string with colors
 */
class SyntaxHighlighter(
    private val language: Language,
    private val colors: SyntaxColors
) {
    // Common keywords by language
    private val keywords = when (language) {
        Language.KOTLIN -> setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
            "companion", "const", "constructor", "continue", "crossinline", "data", "do",
            "else", "enum", "expect", "external", "false", "final", "finally", "for",
            "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner",
            "interface", "internal", "is", "lateinit", "noinline", "null", "object",
            "open", "operator", "out", "override", "package", "private", "protected",
            "public", "reified", "return", "sealed", "set", "super", "suspend", "this",
            "throw", "true", "try", "typealias", "typeof", "val", "var", "vararg",
            "when", "where", "while"
        )
        Language.JAVA -> setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "null",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "true", "false", "try", "void", "volatile", "while"
        )
        Language.JAVASCRIPT, Language.TYPESCRIPT -> setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "enum", "export", "extends",
            "false", "finally", "for", "function", "if", "import", "in", "instanceof",
            "let", "new", "null", "of", "return", "static", "super", "switch", "this",
            "throw", "true", "try", "typeof", "undefined", "var", "void", "while",
            "with", "yield", "interface", "type", "implements", "private", "protected",
            "public", "readonly", "abstract", "as", "from", "get", "set"
        )
        Language.PYTHON -> setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
            "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
            "self", "cls"
        )
        Language.RUST -> setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn",
            "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
            "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
            "self", "Self", "static", "struct", "super", "trait", "true", "type",
            "unsafe", "use", "where", "while"
        )
        Language.GO -> setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
            "map", "package", "range", "return", "select", "struct", "switch", "type",
            "var", "true", "false", "nil"
        )
        Language.SWIFT -> setOf(
            "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
            "func", "import", "init", "inout", "internal", "let", "open", "operator",
            "private", "protocol", "public", "rethrows", "static", "struct", "subscript",
            "typealias", "var", "break", "case", "continue", "default", "defer", "do",
            "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return",
            "switch", "where", "while", "as", "catch", "false", "is", "nil", "self",
            "Self", "super", "throw", "throws", "true", "try"
        )
        Language.SQL -> setOf(
            "select", "from", "where", "and", "or", "not", "in", "like", "between",
            "is", "null", "order", "by", "asc", "desc", "limit", "offset", "group",
            "having", "join", "inner", "left", "right", "outer", "on", "as", "insert",
            "into", "values", "update", "set", "delete", "create", "table", "drop",
            "alter", "index", "primary", "key", "foreign", "references", "unique",
            "default", "constraint", "case", "when", "then", "else", "end", "exists",
            "distinct", "union", "all", "count", "sum", "avg", "min", "max"
        )
        Language.SHELL -> setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
            "do", "done", "in", "function", "select", "time", "coproc", "return", "exit",
            "break", "continue", "local", "readonly", "export", "declare", "typeset",
            "unset", "shift", "source", "alias", "true", "false"
        )
        else -> emptySet()
    }

    // Built-in types by language
    private val types = when (language) {
        Language.KOTLIN -> setOf(
            "Any", "Boolean", "Byte", "Char", "Double", "Float", "Int", "Long", "Nothing",
            "Short", "String", "Unit", "Array", "List", "Map", "Set", "Pair", "Triple",
            "Sequence", "Iterable", "Collection", "MutableList", "MutableMap", "MutableSet"
        )
        Language.JAVA -> setOf(
            "String", "Integer", "Boolean", "Double", "Float", "Long", "Short", "Byte",
            "Character", "Object", "Class", "List", "Map", "Set", "ArrayList", "HashMap",
            "HashSet", "LinkedList", "TreeMap", "TreeSet", "Optional", "Stream"
        )
        Language.TYPESCRIPT -> setOf(
            "string", "number", "boolean", "object", "any", "void", "never", "unknown",
            "null", "undefined", "Array", "Promise", "Map", "Set", "Record", "Partial",
            "Required", "Readonly", "Pick", "Omit", "Exclude", "Extract", "NonNullable"
        )
        Language.RUST -> setOf(
            "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128",
            "usize", "f32", "f64", "bool", "char", "str", "String", "Vec", "Box", "Rc",
            "Arc", "Cell", "RefCell", "Option", "Result", "HashMap", "HashSet", "BTreeMap"
        )
        Language.GO -> setOf(
            "bool", "string", "int", "int8", "int16", "int32", "int64", "uint", "uint8",
            "uint16", "uint32", "uint64", "uintptr", "byte", "rune", "float32", "float64",
            "complex64", "complex128", "error"
        )
        else -> emptySet()
    }

    fun highlight(code: String): AnnotatedString = buildAnnotatedString {
        val lines = code.lines()
        
        lines.forEachIndexed { index, line ->
            highlightLine(line)
            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(line: String) {
        var i = 0
        while (i < line.length) {
            when {
                // Comments
                line.startsWith("//", i) || line.startsWith("#", i) && language in listOf(
                    Language.PYTHON, Language.SHELL, Language.YAML, Language.RUBY
                ) -> {
                    val commentStart = if (line.startsWith("//", i)) "//" else "#"
                    withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                        append(line.substring(i))
                    }
                    i = line.length
                }
                line.startsWith("/*", i) -> {
                    val endIndex = line.indexOf("*/", i + 2)
                    val end = if (endIndex >= 0) endIndex + 2 else line.length
                    withStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)) {
                        append(line.substring(i, end))
                    }
                    i = end
                }
                // Strings (double quotes)
                line[i] == '"' -> {
                    val end = findStringEnd(line, i, '"')
                    withStyle(SpanStyle(color = colors.string)) {
                        append(line.substring(i, end))
                    }
                    i = end
                }
                // Strings (single quotes)
                line[i] == '\'' && language !in listOf(Language.RUST) -> {
                    val end = findStringEnd(line, i, '\'')
                    withStyle(SpanStyle(color = colors.string)) {
                        append(line.substring(i, end))
                    }
                    i = end
                }
                // Template strings (backticks)
                line[i] == '`' && language in listOf(Language.JAVASCRIPT, Language.TYPESCRIPT, Language.SHELL) -> {
                    val end = findStringEnd(line, i, '`')
                    withStyle(SpanStyle(color = colors.string)) {
                        append(line.substring(i, end))
                    }
                    i = end
                }
                // Annotations
                line[i] == '@' && language in listOf(Language.KOTLIN, Language.JAVA, Language.TYPESCRIPT) -> {
                    val end = findWordEnd(line, i + 1)
                    withStyle(SpanStyle(color = colors.annotation)) {
                        append(line.substring(i, end))
                    }
                    i = end
                }
                // Numbers
                line[i].isDigit() || (line[i] == '.' && i + 1 < line.length && line[i + 1].isDigit()) -> {
                    val end = findNumberEnd(line, i)
                    withStyle(SpanStyle(color = colors.number)) {
                        append(line.substring(i, end))
                    }
                    i = end
                }
                // Identifiers (keywords, types, functions)
                line[i].isLetter() || line[i] == '_' -> {
                    val end = findWordEnd(line, i)
                    val word = line.substring(i, end)
                    
                    when {
                        keywords.contains(word) || keywords.contains(word.lowercase()) -> {
                            withStyle(SpanStyle(color = colors.keyword, fontWeight = FontWeight.Bold)) {
                                append(word)
                            }
                        }
                        types.contains(word) -> {
                            withStyle(SpanStyle(color = colors.type)) {
                                append(word)
                            }
                        }
                        end < line.length && line[end] == '(' -> {
                            withStyle(SpanStyle(color = colors.function)) {
                                append(word)
                            }
                        }
                        word.firstOrNull()?.isUpperCase() == true -> {
                            withStyle(SpanStyle(color = colors.type)) {
                                append(word)
                            }
                        }
                        else -> {
                            append(word)
                        }
                    }
                    i = end
                }
                // Operators
                line[i] in "+-*/%=<>!&|^~?:" -> {
                    withStyle(SpanStyle(color = colors.operator)) {
                        append(line[i])
                    }
                    i++
                }
                // Default
                else -> {
                    append(line[i])
                    i++
                }
            }
        }
    }

    private fun findStringEnd(line: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < line.length) {
            if (line[i] == '\\' && i + 1 < line.length) {
                i += 2
            } else if (line[i] == quote) {
                return i + 1
            } else {
                i++
            }
        }
        return line.length
    }

    private fun findWordEnd(line: String, start: Int): Int {
        var i = start
        while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
            i++
        }
        return i
    }

    private fun findNumberEnd(line: String, start: Int): Int {
        var i = start
        var hasDecimal = false
        var hasExponent = false
        
        // Handle hex, octal, binary prefixes
        if (i < line.length && line[i] == '0' && i + 1 < line.length) {
            when (line[i + 1].lowercaseChar()) {
                'x' -> {
                    i += 2
                    while (i < line.length && (line[i].isDigit() || line[i] in 'a'..'f' || line[i] in 'A'..'F' || line[i] == '_')) {
                        i++
                    }
                    return i
                }
                'b' -> {
                    i += 2
                    while (i < line.length && (line[i] == '0' || line[i] == '1' || line[i] == '_')) {
                        i++
                    }
                    return i
                }
                'o' -> {
                    i += 2
                    while (i < line.length && (line[i] in '0'..'7' || line[i] == '_')) {
                        i++
                    }
                    return i
                }
            }
        }
        
        while (i < line.length) {
            when {
                line[i].isDigit() || line[i] == '_' -> i++
                line[i] == '.' && !hasDecimal && !hasExponent -> {
                    hasDecimal = true
                    i++
                }
                (line[i] == 'e' || line[i] == 'E') && !hasExponent -> {
                    hasExponent = true
                    i++
                    if (i < line.length && (line[i] == '+' || line[i] == '-')) {
                        i++
                    }
                }
                line[i] in "fFdDlL" -> {
                    i++
                    break
                }
                else -> break
            }
        }
        return i
    }
}

/**
 * Composable that displays syntax-highlighted code with line numbers
 */
@Composable
fun SyntaxHighlightedCode(
    code: String,
    filename: String,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true,
    fontSize: Int = 12
) {
    val language = remember(filename) { Language.fromFilename(filename) }
    val colors = SyntaxColors.default()
    val highlighter = remember(language, colors) { SyntaxHighlighter(language, colors) }
    val highlightedCode = remember(code, highlighter) { highlighter.highlight(code) }
    
    val lines = code.lines()
    val lineNumberWidth = (lines.size.toString().length * 10 + 16).dp
    
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Line numbers
            if (showLineNumbers) {
                Surface(
                    modifier = Modifier
                        .width(lineNumberWidth)
                        .fillMaxHeight()
                        .verticalScroll(verticalScrollState),
                    color = colors.lineNumberBackground
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        lines.forEachIndexed { index, _ ->
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                color = colors.lineNumber,
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            
            // Code content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
                    .padding(8.dp)
            ) {
                Text(
                    text = highlightedCode,
                    fontSize = fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (fontSize * 1.5).sp
                )
            }
        }
    }
}

/**
 * Simple code block without line numbers - useful for inline code snippets
 */
@Composable
fun CodeSnippet(
    code: String,
    language: Language = Language.UNKNOWN,
    modifier: Modifier = Modifier,
    fontSize: Int = 12
) {
    val colors = SyntaxColors.default()
    val highlighter = remember(language, colors) { SyntaxHighlighter(language, colors) }
    val highlightedCode = remember(code, highlighter) { highlighter.highlight(code) }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = highlightedCode,
            modifier = Modifier.padding(8.dp),
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
