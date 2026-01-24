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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .background(Color(0xFF2B2B2B))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
        
        CtrlComboKey("C", ctrlActive, enabled) { 
            onKeyPress("\u0003")
            ctrlActive = false
        }
        CtrlComboKey("D", ctrlActive, enabled) { 
            onKeyPress("\u0004")
            ctrlActive = false
        }
        CtrlComboKey("Z", ctrlActive, enabled) { 
            onKeyPress("\u001A")
            ctrlActive = false
        }
        CtrlComboKey("L", ctrlActive, enabled) { 
            onKeyPress("\u000C")
            ctrlActive = false
        }
        CtrlComboKey("A", ctrlActive, enabled) { 
            onKeyPress("\u0001")
            ctrlActive = false
        }
        CtrlComboKey("E", ctrlActive, enabled) { 
            onKeyPress("\u0005")
            ctrlActive = false
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
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF3C3C3C),
            contentColor = Color(0xFFE0E0E0),
            disabledContainerColor = Color(0xFF2A2A2A),
            disabledContentColor = Color(0xFF666666)
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
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
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (active) Color(0xFF4CAF50) else Color(0xFF3C3C3C),
            contentColor = if (active) Color.White else Color(0xFFE0E0E0),
            disabledContainerColor = Color(0xFF2A2A2A),
            disabledContentColor = Color(0xFF666666)
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun CtrlComboKey(
    letter: String,
    ctrlActive: Boolean,
    enabled: Boolean,
    onPress: () -> Unit
) {
    if (!ctrlActive) return
    
    FilledTonalButton(
        onClick = onPress,
        enabled = enabled,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF4CAF50),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2A2A2A),
            disabledContentColor = Color(0xFF666666)
        )
    ) {
        Text(
            text = "^$letter",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
