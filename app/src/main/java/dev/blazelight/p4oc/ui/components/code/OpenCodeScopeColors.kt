package dev.blazelight.p4oc.ui.components.code

import androidx.compose.ui.graphics.Color
import dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme

/** Shared scope→color rules for the AnnotatedString builder and editor theme JSON. */
internal object OpenCodeScopeColors {
    data class Rule(val scopePrefix: String, val color: (OpenCodeTheme) -> Color)

    val rules: List<Rule> = listOf(
        Rule("comment") { it.syntaxComment }, Rule("string") { it.syntaxString },
        Rule("constant.numeric") { it.syntaxNumber }, Rule("constant.language") { it.syntaxNumber },
        Rule("entity.name.function") { it.syntaxFunction }, Rule("support.function") { it.syntaxFunction },
        Rule("meta.function-call") { it.syntaxFunction },
        Rule("entity.name.type") { it.syntaxType }, Rule("entity.name.class") { it.syntaxType },
        Rule("support.type") { it.syntaxType }, Rule("support.class") { it.syntaxType },
        Rule("storage") { it.syntaxKeyword }, Rule("keyword.operator") { it.syntaxOperator },
        Rule("keyword") { it.syntaxKeyword }, Rule("variable") { it.syntaxVariable },
        Rule("punctuation") { it.syntaxPunctuation },
    )

    /**
     * Returns the color from the highest-priority (earliest in [rules]) rule
     * that matches any scope in the chain. Priority order beats scope-stack
     * order so e.g. `punctuation.definition.comment.python` resolves to
     * comment color (not punctuation), matching TextMate-theme conventions.
     */
    fun colorFor(scopes: List<String>, theme: OpenCodeTheme): Color? {
        for (r in rules) {
            for (s in scopes) if (s == r.scopePrefix || s.startsWith(r.scopePrefix + ".")) return r.color(theme)
        }
        return null
    }
}
