package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.animation.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme

/**
 * Floating action button that appears when the user has scrolled away from the bottom.
 * Tapping it scrolls back to the latest content.
 */
@Composable
fun JumpToBottomButton(
    visible: Boolean,
    hasNewContent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    val jumpDescription = stringResource(R.string.cd_jump_to_bottom)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = if (hasNewContent) {
                theme.accent
            } else {
                theme.backgroundElement
            },
            contentColor = if (hasNewContent) {
                theme.background
            } else {
                theme.textMuted
            },
            shape = RectangleShape,
            modifier = Modifier
                .semantics { contentDescription = jumpDescription }
                .testTag("jump_to_bottom_button")
        ) {
            Text(
                text = "↓",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
