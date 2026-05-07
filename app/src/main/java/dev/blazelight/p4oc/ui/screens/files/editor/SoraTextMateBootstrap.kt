package dev.blazelight.p4oc.ui.screens.files.editor

import android.content.Context
import android.util.Log
import dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.blazelight.p4oc.ui.components.code.OpenCodeScopeColors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds an in-memory TextMate theme JSON from the active [OpenCodeTheme] and
 * registers it with Sora's [ThemeRegistry], and registers the curated bundle
 * of TextMate grammars shipped under `app/src/main/assets/textmate/` with
 * Sora's [GrammarRegistry]. Uses only the public API surface (LGPL §6
 * compliance — no fork/modify of sora-editor).
 *
 * Theme: generated in memory from the active [OpenCodeTheme] every time
 * [applyTheme] is called, so the editor chrome and token colours always
 * match the OpenCode palette without shipping any theme JSON assets.
 *
 * Grammars: a fixed bundle (env, json, kotlin, markdown, yaml, toml, xml,
 * shell, typescript, python — see `textmate/SOURCES.md`) is loaded once via
 * [ensureGrammars]. Filenames are mapped to scopes by [SoraLanguageRegistry];
 * the editor falls back to [io.github.rosemoe.sora.lang.EmptyLanguage] only
 * when no scope is mapped or the grammar fails to parse.
 */
internal object SoraTextMateBootstrap {

    private const val TAG = "SoraTextMateBootstrap"

    /** Path to the bundled grammar manifest under `app/src/main/assets/`. */
    private const val LANGUAGES_CONFIG = "textmate/languages.json"

    private val activeThemeName = AtomicReference<String?>(null)
    private val grammarsLoaded = AtomicBoolean(false)

    /**
     * Idempotently registers the curated TextMate grammar bundle with sora's
     * [GrammarRegistry]. Safe to call from every [io.github.rosemoe.sora.widget.CodeEditor]
     * `factory{}`: the first call performs the work, subsequent calls are
     * effectively no-ops via [AtomicBoolean].
     *
     * Failures are logged and swallowed; callers should fall back to
     * [io.github.rosemoe.sora.lang.EmptyLanguage] if a scope cannot be resolved.
     */
    @Synchronized
    fun ensureGrammars(context: Context) {
        if (grammarsLoaded.get()) return
        val appCtx = context.applicationContext
        runCatching {
            // FileProviderRegistry is a public sora singleton (no app-global
            // state of ours). Adding the resolver again is harmless if it was
            // added previously by another caller; we still gate behind
            // `grammarsLoaded` so we don't re-register on the same flow.
            FileProviderRegistry.getInstance()
                .addFileProvider(AssetsFileResolver(appCtx.assets))
            GrammarRegistry.getInstance().loadGrammars(LANGUAGES_CONFIG)
        }.onFailure { t ->
            Log.w(TAG, "Grammar bootstrap failed; editor will fall back to plain text", t)
            // Mark as loaded anyway: retrying mid-session will not help and we
            // don't want every editor open to repeat the same failing load.
        }
        grammarsLoaded.set(true)
    }

    /**
     * Idempotently registers a theme derived from [theme] and marks it as the
     * current ThemeRegistry theme. Returns the registered theme name (suitable
     * for diagnostics; the [io.github.rosemoe.sora.langs.textmate.TextMateColorScheme]
     * already reads the current theme from the registry).
     */
    @Synchronized
    fun applyTheme(theme: OpenCodeTheme): String {
        val themeName = "opencode-${if (theme.isDark) "dark" else "light"}-${theme.name.hashCode()}"
        val registry = ThemeRegistry.getInstance()
        val json = buildThemeJson(themeName, theme)
        val source = IThemeSource.fromString(IThemeSource.ContentType.JSON, json)
        val model = ThemeModel(source, themeName).apply { setDark(theme.isDark) }
        try {
            registry.loadTheme(model)
            registry.setTheme(themeName)
        } catch (t: Throwable) {
            // Fall back silently — theme registry will keep its previous state
            // and the editor renders with default EditorColorScheme.
            return activeThemeName.get() ?: ""
        }
        activeThemeName.set(themeName)
        return themeName
    }

    private fun hex(color: Color): String {
        val argb = color.toArgb()
        // TextMate themes use #RRGGBB or #RRGGBBAA
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val a = (argb shr 24) and 0xFF
        return if (a == 0xFF) {
            "#%02X%02X%02X".format(r, g, b)
        } else {
            "#%02X%02X%02X%02X".format(r, g, b, a)
        }
    }

    private fun buildThemeJson(name: String, t: OpenCodeTheme): String {
        // Minimal TextMate theme: editor chrome via "colors"; token rules via
        // "tokenColors" generated from the shared [OpenCodeScopeColors.rules]
        // so view-mode AnnotatedString and editor highlighting stay aligned.
        val tokenRules = OpenCodeScopeColors.rules.joinToString(separator = ",\n    ") { rule ->
            """{ "scope": ["${rule.scopePrefix}"], "settings": { "foreground": "${hex(rule.color(t))}" } }"""
        }
        return """
{
  "name": "$name",
  "type": "${if (t.isDark) "dark" else "light"}",
  "colors": {
    "editor.background": "${hex(t.background)}",
    "editor.foreground": "${hex(t.text)}",
    "editorLineNumber.foreground": "${hex(t.textMuted)}",
    "editorLineNumber.activeForeground": "${hex(t.text)}",
    "editor.selectionBackground": "${hex(t.accent.copy(alpha = 0.30f))}",
    "editor.lineHighlightBackground": "${hex(t.backgroundElement)}",
    "editorCursor.foreground": "${hex(t.accent)}",
    "editorIndentGuide.background": "${hex(t.borderSubtle)}",
    "editorWhitespace.foreground": "${hex(t.borderSubtle)}"
  },
  "tokenColors": [
    $tokenRules
  ]
}
        """.trimIndent()
    }

}
