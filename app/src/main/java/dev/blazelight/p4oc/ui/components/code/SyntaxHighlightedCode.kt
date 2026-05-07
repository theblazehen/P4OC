package dev.blazelight.p4oc.ui.components.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.ui.screens.files.editor.SoraLanguageRegistry
import dev.blazelight.p4oc.ui.screens.files.editor.SoraTextMateBootstrap
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing

/**
 * Read-only code viewer with TextMate-backed highlighting (via
 * [TextMateAnnotatedString]), optional line-number gutter, and optional
 * selection. Filename → scope mapping uses [SoraLanguageRegistry] so view-
 * and edit-mode highlighting stay aligned.
 */
@Composable
fun SyntaxHighlightedCode(
    code: String,
    filename: String,
    modifier: Modifier = Modifier,
    showLineNumbers: Boolean = true,
    fontSize: Int = 12,
    selectable: Boolean = false,
) {
    val context = LocalContext.current
    // Tracks whether [SoraTextMateBootstrap.ensureGrammars] has completed for
    // this composition. ensureGrammars is idempotent and synchronous, but we
    // can't call it inline (Composables must be side-effect free), so we run
    // it inside a LaunchedEffect and flip a Compose-state flag once it
    // returns. Including [grammarsReady] in the highlight memo key forces a
    // recomputation after bootstrap — without it, the first frame caches the
    // plain (unstyled) result because findGrammar() returns null.
    var grammarsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        SoraTextMateBootstrap.ensureGrammars(context)
        grammarsReady = true
    }
    val theme = LocalOpenCodeTheme.current
    val scope = remember(filename) { SoraLanguageRegistry.scopeFor(filename) }
    val highlighted = remember(code, scope, theme, grammarsReady) {
        TextMateAnnotatedString.highlight(code, scope, theme)
    }
    val lines = remember(code) { code.lines() }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gutterWidth = remember(lines.size, fontSize) {
        val m = measurer.measure(
            text = lines.size.toString(),
            style = TextStyle(fontSize = fontSize.sp, fontFamily = FontFamily.Monospace),
        )
        with(density) { m.size.width.toDp() + Spacing.md + Spacing.md }
    }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Surface(modifier = modifier, color = theme.backgroundElement, shape = RectangleShape) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showLineNumbers) {
                Surface(
                    modifier = Modifier.width(gutterWidth).fillMaxHeight().verticalScroll(vScroll),
                    color = theme.backgroundElement,
                ) {
                    Column(modifier = Modifier.padding(vertical = Spacing.md)) {
                        lines.forEachIndexed { i, _ ->
                            Text(
                                text = "${i + 1}",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                                color = theme.textMuted,
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.weight(1f).verticalScroll(vScroll)
                    .horizontalScroll(hScroll).padding(Spacing.md),
            ) {
                val body: @Composable () -> Unit = {
                    Text(
                        text = highlighted,
                        fontSize = fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (fontSize * 1.5).sp,
                        color = theme.text,
                    )
                }
                if (selectable) SelectionContainer { body() } else body()
            }
        }
    }
}
