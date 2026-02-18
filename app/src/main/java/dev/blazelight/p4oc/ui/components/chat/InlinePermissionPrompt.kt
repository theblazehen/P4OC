package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize

/**
 * Compact inline permission prompt that appears below a tool widget.
 * Shows: permission title + Allow/Always/Reject buttons
 */
@Composable
fun InlinePermissionPrompt(
    permission: Permission,
    onAllow: () -> Unit,
    onAlways: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.warning.copy(alpha = 0.1f))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Permission title with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "◉",
                style = MaterialTheme.typography.labelMedium,
                color = theme.warning
            )
            Text(
                text = permission.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = TuiCodeFontSize.lg
                ),
                color = theme.text,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.none),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = theme.error
                )
            ) {
                Text(
                    stringResource(R.string.deny),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            OutlinedButton(
                onClick = onAlways,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.none)
            ) {
                Text(
                    stringResource(R.string.always_allow),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            Button(
                onClick = onAllow,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.none),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.success,
                    contentColor = theme.background
                )
            ) {
                Text(
                    stringResource(R.string.allow),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
