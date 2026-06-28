package dev.blazelight.p4oc.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

/**
 * In-chat search bar: a transparent-bordered query field plus a match counter
 * ("3/12"), previous/next navigation and a close button. Mirrors the file/command
 * search UX. Auto-focuses on appearance so the keyboard opens immediately.
 */
@Composable
internal fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(color = theme.backgroundPanel, shape = RectangleShape, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = "⌕",
                color = theme.accent,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.chat_search_placeholder),
                        color = theme.textMuted,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = theme.text,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("chat_search_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = theme.accent,
                    focusedTextColor = theme.text,
                    unfocusedTextColor = theme.text,
                ),
            )
            val counter = when {
                query.isBlank() -> ""
                matchCount == 0 -> stringResource(R.string.chat_search_no_matches)
                else -> "${currentIndex + 1}/$matchCount"
            }
            if (counter.isNotEmpty()) {
                Text(
                    text = counter,
                    color = theme.textMuted,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            SearchGlyphButton(
                glyph = "↑",
                contentDescription = stringResource(R.string.previous),
                testTag = "chat_search_previous",
                enabled = matchCount > 0,
                onClick = onPrev,
                color = theme.accent,
                mutedColor = theme.textMuted,
            )
            SearchGlyphButton(
                glyph = "↓",
                contentDescription = stringResource(R.string.next),
                testTag = "chat_search_next",
                enabled = matchCount > 0,
                onClick = onNext,
                color = theme.accent,
                mutedColor = theme.textMuted,
            )
            SearchGlyphButton(
                glyph = "✕",
                contentDescription = stringResource(R.string.button_cancel),
                testTag = "chat_search_close",
                enabled = true,
                onClick = onClose,
                color = theme.textMuted,
                mutedColor = theme.textMuted,
            )
        }
    }
}

@Composable
private fun SearchGlyphButton(
    glyph: String,
    contentDescription: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    color: Color,
    mutedColor: Color,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(Sizing.iconButtonMd)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
    ) {
        Text(
            text = glyph,
            color = if (enabled) color else mutedColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
