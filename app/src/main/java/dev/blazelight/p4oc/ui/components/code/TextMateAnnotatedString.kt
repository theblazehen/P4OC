package dev.blazelight.p4oc.ui.components.code

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack

/**
 * Pure TextMate-backed AnnotatedString builder. Tokenizes line-by-line via
 * TM4E's [IGrammar.tokenizeLine], threading [IStateStack] across newlines so
 * block comments / multiline strings highlight correctly. Returns plain
 * (unstyled) text when the scope has no registered grammar.
 *
 * Caller is responsible for grammar-registry bootstrap (see
 * [dev.blazelight.p4oc.ui.screens.files.editor.SoraTextMateBootstrap.ensureGrammars]).
 */
internal object TextMateAnnotatedString {
    fun highlight(code: String, scope: String?, palette: OpenCodeTheme): AnnotatedString {
        val grammar = scope?.let {
            runCatching { GrammarRegistry.getInstance().findGrammar(it) }.getOrNull()
        }
        return highlightWithGrammar(code, grammar, palette)
    }

    /** Visible to tests so they can inject a grammar loaded via TM4E [org.eclipse.tm4e.core.registry.Registry]. */
    internal fun highlightWithGrammar(
        code: String,
        grammar: IGrammar?,
        palette: OpenCodeTheme,
    ): AnnotatedString {
        if (grammar == null || code.isEmpty()) return AnnotatedString(code)
        return buildAnnotatedString {
            val lines = code.split('\n')
            var state: IStateStack? = null
            lines.forEachIndexed { idx, line ->
                val res = runCatching { grammar.tokenizeLine(line, state, null) }.getOrNull()
                if (res == null) {
                    append(line)
                } else {
                    state = res.ruleStack
                    for (tok in res.tokens) {
                        val s = tok.startIndex.coerceIn(0, line.length)
                        val e = tok.endIndex.coerceIn(s, line.length)
                        if (s == e) continue
                        val color = OpenCodeScopeColors.colorFor(tok.scopes, palette)
                        if (color != null) withStyle(SpanStyle(color = color)) { append(line, s, e) }
                        else append(line, s, e)
                    }
                }
                if (idx < lines.lastIndex) append('\n')
            }
        }
    }
}
