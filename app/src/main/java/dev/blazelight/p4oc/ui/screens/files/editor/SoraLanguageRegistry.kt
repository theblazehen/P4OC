package dev.blazelight.p4oc.ui.screens.files.editor

/**
 * Pure filename → TextMate scope mapping for the curated grammar bundle shipped
 * under `app/src/main/assets/textmate/`.
 *
 * Order of resolution:
 *  1. Special `.env` / `.env.<flavour>` family
 *  2. Exact basename match (e.g. `Dockerfile`, `Cargo.lock`)
 *  3. File extension (lower-cased)
 *
 * Returning `null` is meaningful — the caller falls back to plain text via
 * sora's `EmptyLanguage`. Do **not** add silent fallback chains here; we only
 * report scopes for grammars we actually ship.
 *
 * No Android dependencies on purpose: this stays trivially unit-testable.
 */
internal object SoraLanguageRegistry {

    private val byBasename: Map<String, String> = mapOf(
        // Pragmatic mapping: Dockerfile gets shell highlighting until we ship a
        // dedicated Dockerfile grammar. Documented limitation, not a fallback
        // chain — this is an explicit basename rule.
        "Dockerfile" to "source.shell",
        "Cargo.lock" to "source.toml",
    )

    private val byExtension: Map<String, String> = mapOf(
        "kt" to "source.kotlin", "kts" to "source.kotlin",
        "json" to "source.json", "jsonc" to "source.json", "json5" to "source.json",
        "md" to "text.html.markdown", "markdown" to "text.html.markdown",
        "yml" to "source.yaml", "yaml" to "source.yaml",
        "toml" to "source.toml",
        "xml" to "text.xml",
        "sh" to "source.shell", "bash" to "source.shell", "zsh" to "source.shell",
        "ts" to "source.ts", "tsx" to "source.ts",
        "js" to "source.ts", "mjs" to "source.ts", "cjs" to "source.ts", "jsx" to "source.ts",
        "py" to "source.python", "pyi" to "source.python",
    )

    /**
     * Returns the TextMate scope name for [filename], or `null` if no shipped
     * grammar matches. [filename] may be a bare name or a full path; only the
     * basename is consulted.
     */
    fun scopeFor(filename: String): String? {
        if (filename.isEmpty()) return null
        val name = filename.substringAfterLast('/').substringAfterLast('\\')
        if (name.isEmpty()) return null

        // .env, .env.local, .env.production, ...
        if (name == ".env" || name.startsWith(".env.")) return "source.env"

        byBasename[name]?.let { return it }

        val dot = name.lastIndexOf('.')
        // Treat dotfiles with no inner extension (".gitignore") as having no ext.
        if (dot <= 0 || dot == name.length - 1) return null
        val ext = name.substring(dot + 1).lowercase()
        return byExtension[ext]
    }
}

/**
 * Human-readable label for [scope], used as the file-viewer subtitle. Returns
 * `"plain text"` for null/unknown scopes; only scopes we actually ship are
 * mapped here.
 */
internal fun displayLabelForScope(scope: String?): String = when (scope) {
    "source.kotlin" -> "kotlin"
    "source.json" -> "json"
    "source.python" -> "python"
    "source.ts" -> "typescript"
    "source.yaml" -> "yaml"
    "source.toml" -> "toml"
    "source.shell" -> "shell"
    "source.env" -> "env"
    "text.xml" -> "xml"
    "text.html.markdown" -> "markdown"
    else -> "plain text"
}
