package dev.blazelight.p4oc.core.filetype

enum class FileTypeCategory {
    Code,
    Config,
    Document,
    Image,
    Video,
    Audio,
    Archive,
    Shell,
    Build,
    Git,
    Lock,
    Env,
    Web,
    Database,
    Unknown,
}

data class FileTypeMetadata(
    val category: FileTypeCategory,
    val textMateScope: String? = null,
    val uploadSymbol: String = "◆",
)

/**
 * Shared filename classifier for file identity. UI layers map [category] to
 * their own compact presentation; editors use [textMateScope] only when a
 * shipped grammar exists.
 */
object FileTypeClassifier {
    private val byBasename: Map<String, FileTypeMetadata> = mapOf(
        "Dockerfile" to FileTypeMetadata(FileTypeCategory.Build, "source.shell", "{}"),
        "Makefile" to FileTypeMetadata(FileTypeCategory.Build),
        "gradlew" to FileTypeMetadata(FileTypeCategory.Build),
        "Cargo.lock" to FileTypeMetadata(FileTypeCategory.Lock, "source.toml"),
        ".gitignore" to FileTypeMetadata(FileTypeCategory.Git),
        ".gitattributes" to FileTypeMetadata(FileTypeCategory.Git),
        ".gitmodules" to FileTypeMetadata(FileTypeCategory.Git),
        ".env" to FileTypeMetadata(FileTypeCategory.Env, "source.env"),
        ".envrc" to FileTypeMetadata(FileTypeCategory.Env),
    )

    private val byExtension: Map<String, FileTypeMetadata> = mapOf(
        "kt" to FileTypeMetadata(FileTypeCategory.Code, "source.kotlin", "{}"),
        "kts" to FileTypeMetadata(FileTypeCategory.Code, "source.kotlin", "{}"),
        "java" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "py" to FileTypeMetadata(FileTypeCategory.Code, "source.python", "{}"),
        "pyi" to FileTypeMetadata(FileTypeCategory.Code, "source.python"),
        "js" to FileTypeMetadata(FileTypeCategory.Code, "source.ts", "{}"),
        "mjs" to FileTypeMetadata(FileTypeCategory.Code, "source.ts"),
        "cjs" to FileTypeMetadata(FileTypeCategory.Code, "source.ts"),
        "ts" to FileTypeMetadata(FileTypeCategory.Code, "source.ts", "{}"),
        "tsx" to FileTypeMetadata(FileTypeCategory.Code, "source.ts", "{}"),
        "jsx" to FileTypeMetadata(FileTypeCategory.Code, "source.ts", "{}"),
        "c" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "cpp" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "h" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "rs" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "go" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "rb" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "php" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "swift" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "m" to FileTypeMetadata(FileTypeCategory.Code, uploadSymbol = "{}"),
        "json" to FileTypeMetadata(FileTypeCategory.Config, "source.json", "⚙"),
        "jsonc" to FileTypeMetadata(FileTypeCategory.Config, "source.json"),
        "json5" to FileTypeMetadata(FileTypeCategory.Config, "source.json"),
        "yaml" to FileTypeMetadata(FileTypeCategory.Config, "source.yaml", "⚙"),
        "yml" to FileTypeMetadata(FileTypeCategory.Config, "source.yaml", "⚙"),
        "xml" to FileTypeMetadata(FileTypeCategory.Config, "text.xml", "⚙"),
        "toml" to FileTypeMetadata(FileTypeCategory.Config, "source.toml", "⚙"),
        "ini" to FileTypeMetadata(FileTypeCategory.Config, uploadSymbol = "⚙"),
        "conf" to FileTypeMetadata(FileTypeCategory.Config, uploadSymbol = "⚙"),
        "config" to FileTypeMetadata(FileTypeCategory.Config, uploadSymbol = "⚙"),
        "properties" to FileTypeMetadata(FileTypeCategory.Config),
        "md" to FileTypeMetadata(FileTypeCategory.Document, "text.html.markdown", "≡"),
        "markdown" to FileTypeMetadata(FileTypeCategory.Document, "text.html.markdown"),
        "txt" to FileTypeMetadata(FileTypeCategory.Document, uploadSymbol = "≡"),
        "rst" to FileTypeMetadata(FileTypeCategory.Document, uploadSymbol = "≡"),
        "log" to FileTypeMetadata(FileTypeCategory.Document, uploadSymbol = "≡"),
        "doc" to FileTypeMetadata(FileTypeCategory.Document, uploadSymbol = "▭"),
        "docx" to FileTypeMetadata(FileTypeCategory.Document, uploadSymbol = "▭"),
        "pdf" to FileTypeMetadata(FileTypeCategory.Document, uploadSymbol = "▭"),
        "png" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "jpg" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "jpeg" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "gif" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "svg" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "webp" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "ico" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "bmp" to FileTypeMetadata(FileTypeCategory.Image, uploadSymbol = "▣"),
        "mp4" to FileTypeMetadata(FileTypeCategory.Video, uploadSymbol = "▶"),
        "avi" to FileTypeMetadata(FileTypeCategory.Video, uploadSymbol = "▶"),
        "mov" to FileTypeMetadata(FileTypeCategory.Video, uploadSymbol = "▶"),
        "mkv" to FileTypeMetadata(FileTypeCategory.Video, uploadSymbol = "▶"),
        "webm" to FileTypeMetadata(FileTypeCategory.Video, uploadSymbol = "▶"),
        "mp3" to FileTypeMetadata(FileTypeCategory.Audio, uploadSymbol = "♪"),
        "wav" to FileTypeMetadata(FileTypeCategory.Audio, uploadSymbol = "♪"),
        "ogg" to FileTypeMetadata(FileTypeCategory.Audio, uploadSymbol = "♪"),
        "flac" to FileTypeMetadata(FileTypeCategory.Audio, uploadSymbol = "♪"),
        "m4a" to FileTypeMetadata(FileTypeCategory.Audio, uploadSymbol = "♪"),
        "zip" to FileTypeMetadata(FileTypeCategory.Archive, uploadSymbol = "▤"),
        "tar" to FileTypeMetadata(FileTypeCategory.Archive, uploadSymbol = "▤"),
        "gz" to FileTypeMetadata(FileTypeCategory.Archive, uploadSymbol = "▤"),
        "rar" to FileTypeMetadata(FileTypeCategory.Archive, uploadSymbol = "▤"),
        "7z" to FileTypeMetadata(FileTypeCategory.Archive, uploadSymbol = "▤"),
        "sh" to FileTypeMetadata(FileTypeCategory.Shell, "source.shell", "{}"),
        "bash" to FileTypeMetadata(FileTypeCategory.Shell, "source.shell", "{}"),
        "zsh" to FileTypeMetadata(FileTypeCategory.Shell, "source.shell"),
        "fish" to FileTypeMetadata(FileTypeCategory.Shell),
        "gradle" to FileTypeMetadata(FileTypeCategory.Build),
        "lock" to FileTypeMetadata(FileTypeCategory.Lock),
        "env" to FileTypeMetadata(FileTypeCategory.Env),
        "html" to FileTypeMetadata(FileTypeCategory.Web),
        "htm" to FileTypeMetadata(FileTypeCategory.Web),
        "css" to FileTypeMetadata(FileTypeCategory.Web),
        "scss" to FileTypeMetadata(FileTypeCategory.Web),
        "sass" to FileTypeMetadata(FileTypeCategory.Web),
        "less" to FileTypeMetadata(FileTypeCategory.Web),
        "sql" to FileTypeMetadata(FileTypeCategory.Database),
        "db" to FileTypeMetadata(FileTypeCategory.Database),
        "sqlite" to FileTypeMetadata(FileTypeCategory.Database),
    )

    fun classify(filename: String): FileTypeMetadata {
        val name = filename.substringAfterLast('/').substringAfterLast('\\')
        val dot = name.lastIndexOf('.')
        return when {
            name.isEmpty() -> unknown
            name == ".env" || name.startsWith(".env.") -> FileTypeMetadata(FileTypeCategory.Env, "source.env")
            byBasename.containsKey(name) -> byBasename.getValue(name)
            dot <= 0 || dot == name.length - 1 -> unknown
            else -> byExtension[name.substring(dot + 1).lowercase()] ?: unknown
        }
    }

    fun mappedTextMateScopes(): Set<String> = (byBasename.values + byExtension.values)
        .mapNotNull { it.textMateScope }
        .toSet() + "source.env"

    private val unknown = FileTypeMetadata(FileTypeCategory.Unknown)
}
