package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing

/**
 * Contextual chat navigation actions, ordered from older content to newer content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "FunctionNaming")
fun ChatJumpNavigationButtons(
    showPrevious: Boolean,
    showBottom: Boolean,
    hasNewContent: Boolean,
    onPrevious: () -> Unit,
    onBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalOpenCodeTheme.current
    val previousDescription = stringResource(R.string.cd_jump_to_previous_user_message)
    val bottomDescription = stringResource(R.string.cd_jump_to_bottom)

    AnimatedVisibility(
        visible = showPrevious || showBottom,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPrevious) {
                ChatJumpNavigationButton(
                    glyph = "↑",
                    description = previousDescription,
                    onClick = onPrevious,
                    containerColor = theme.backgroundElement,
                    contentColor = theme.textMuted,
                    testTag = "jump_to_previous_user_button",
                )
            }
            if (showBottom) {
                ChatJumpNavigationButton(
                    glyph = "↓",
                    description = bottomDescription,
                    onClick = onBottom,
                    containerColor = if (hasNewContent) theme.accent else theme.backgroundElement,
                    contentColor = if (hasNewContent) theme.background else theme.textMuted,
                    testTag = "jump_to_bottom_button",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "FunctionNaming")
private fun ChatJumpNavigationButton(
    glyph: String,
    description: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    testTag: String,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(description)
            }
        },
        state = rememberTooltipState(),
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = RectangleShape,
            modifier = Modifier
                .semantics { contentDescription = description }
                .testTag(testTag),
        ) {
            Text(
                text = glyph,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
