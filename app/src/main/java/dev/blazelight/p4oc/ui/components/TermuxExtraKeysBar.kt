package dev.blazelight.p4oc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize

@Composable
fun TermuxExtraKeysBar(
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SemanticColors.TerminalKeys.background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        ExtraKey("ESC", "\u001B", enabled, onKeyPress)
        
        ModifierKey(
            label = "CTRL",
            active = ctrlActive,
            enabled = enabled,
            onClick = { ctrlActive = !ctrlActive }
        )
        
        ModifierKey(
            label = "ALT",
            active = altActive,
            enabled = enabled,
            onClick = { altActive = !altActive }
        )
        
        ExtraKey("TAB", "\t", enabled, onKeyPress)
        ExtraKey("-", "-", enabled, onKeyPress)
        ExtraKey("/", "/", enabled, onKeyPress)
        ExtraKey("|", "|", enabled, onKeyPress)
        ExtraKey("~", "~", enabled, onKeyPress)
        ExtraKey("HOME", "\u001B[H", enabled, onKeyPress)
        ExtraKey("END", "\u001B[F", enabled, onKeyPress)
        ExtraKey("PGUP", "\u001B[5~", enabled, onKeyPress)
        ExtraKey("PGDN", "\u001B[6~", enabled, onKeyPress)
        
        ExtraKey("↑", "\u001B[A", enabled, onKeyPress)
        ExtraKey("↓", "\u001B[B", enabled, onKeyPress)
        ExtraKey("←", "\u001B[D", enabled, onKeyPress)
        ExtraKey("→", "\u001B[C", enabled, onKeyPress)
        
        if (ctrlActive) {
            CtrlComboKey("C", enabled) { 
                onKeyPress("\u0003")
                ctrlActive = false
            }
            CtrlComboKey("D", enabled) { 
                onKeyPress("\u0004")
                ctrlActive = false
            }
            CtrlComboKey("Z", enabled) { 
                onKeyPress("\u001A")
                ctrlActive = false
            }
            CtrlComboKey("L", enabled) { 
                onKeyPress("\u000C")
                ctrlActive = false
            }
            CtrlComboKey("A", enabled) { 
                onKeyPress("\u0001")
                ctrlActive = false
            }
            CtrlComboKey("E", enabled) { 
                onKeyPress("\u0005")
                ctrlActive = false
            }
        }
    }
}

@Composable
private fun ExtraKey(
    label: String,
    sequence: String,
    enabled: Boolean,
    onKeyPress: (String) -> Unit
) {
    FilledTonalButton(
        onClick = { onKeyPress(sequence) },
        enabled = enabled,
        modifier = Modifier.height(Sizing.buttonHeightMd),
        contentPadding = PaddingValues(horizontal = Spacing.mdLg, vertical = Spacing.none),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = SemanticColors.TerminalKeys.keyBackground,
            contentColor = SemanticColors.TerminalKeys.keyText,
            disabledContainerColor = SemanticColors.TerminalKeys.keyDisabledBackground,
            disabledContentColor = SemanticColors.TerminalKeys.keyDisabledText
        )
    ) {
        Text(
            text = label,
            fontSize = TuiCodeFontSize.md,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ModifierKey(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(Sizing.buttonHeightMd),
        contentPadding = PaddingValues(horizontal = Spacing.mdLg, vertical = Spacing.none),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (active) SemanticColors.TerminalKeys.activeModifier else SemanticColors.TerminalKeys.keyBackground,
            contentColor = if (active) SemanticColors.TerminalKeys.keyTextActive else SemanticColors.TerminalKeys.keyText,
            disabledContainerColor = SemanticColors.TerminalKeys.keyDisabledBackground,
            disabledContentColor = SemanticColors.TerminalKeys.keyDisabledText
        )
    ) {
        Text(
            text = label,
            fontSize = TuiCodeFontSize.md,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CtrlComboKey(
    letter: String,
    enabled: Boolean,
    onPress: () -> Unit
) {
    FilledTonalButton(
        onClick = onPress,
        enabled = enabled,
        modifier = Modifier.height(Sizing.buttonHeightMd),
        contentPadding = PaddingValues(horizontal = Spacing.mdLg, vertical = Spacing.none),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = SemanticColors.TerminalKeys.activeModifier,
            contentColor = SemanticColors.TerminalKeys.keyTextActive,
            disabledContainerColor = SemanticColors.TerminalKeys.keyDisabledBackground,
            disabledContentColor = SemanticColors.TerminalKeys.keyDisabledText
        )
    ) {
        Text(
            text = "^$letter",
            fontSize = TuiCodeFontSize.md,
            fontFamily = FontFamily.Monospace
        )
    }
}
