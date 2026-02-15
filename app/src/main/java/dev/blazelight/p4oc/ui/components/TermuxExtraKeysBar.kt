package dev.blazelight.p4oc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.RectangleShape
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.TuiCodeFontSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Terminal extra keys bar in Termux style.
 * Two-row grid layout with transparent buttons and Unicode symbols.
 * 
 * Default layout matches Termux:
 * Row 1: ESC  /  ―  HOME  ↑  END  PGUP
 * Row 2: TAB  CTRL ALT  ←  ↓  →  PGDN
 */
@Composable
fun TermuxExtraKeysBar(
    onKeyPress: (String) -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SemanticColors.TerminalKeys.background)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
    ) {
        // Row 1: ESC  /  ―  HOME  ↑  END  PGUP
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizing.buttonHeightMd)
        ) {
            ExtraKey("ESC", "\u001B", enabled, onKeyPress, Modifier.weight(1f))
            ExtraKey("/", "/", enabled, onKeyPress, Modifier.weight(1f))
            ExtraKey("―", "-", enabled, onKeyPress, Modifier.weight(1f))
            ExtraKey("HOME", "\u001B[H", enabled, onKeyPress, Modifier.weight(1f))
            RepeatableExtraKey("↑", "\u001B[A", enabled, onKeyPress, Modifier.weight(1f))
            ExtraKey("END", "\u001B[F", enabled, onKeyPress, Modifier.weight(1f))
            ExtraKey("PGUP", "\u001B[5~", enabled, onKeyPress, Modifier.weight(1f))
        }
        
        // Row 2: TAB  CTRL  ALT  ←  ↓  →  PGDN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizing.buttonHeightMd)
        ) {
            ExtraKey("↹", "\t", enabled, onKeyPress, Modifier.weight(1f))
            ModifierKey("CTRL", ctrlActive, enabled, onCtrlToggle, Modifier.weight(1f))
            ModifierKey("ALT", altActive, enabled, onAltToggle, Modifier.weight(1f))
            RepeatableExtraKey("←", "\u001B[D", enabled, onKeyPress, Modifier.weight(1f))
            RepeatableExtraKey("↓", "\u001B[B", enabled, onKeyPress, Modifier.weight(1f))
            RepeatableExtraKey("→", "\u001B[C", enabled, onKeyPress, Modifier.weight(1f))
            ExtraKey("PGDN", "\u001B[6~", enabled, onKeyPress, Modifier.weight(1f))
        }
    }
}

/**
 * Single extra key button (non-repeating).
 */
@Composable
private fun ExtraKey(
    label: String,
    sequence: String,
    enabled: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .background(
                color = if (isPressed) SemanticColors.TerminalKeys.keyPressed else Color.Transparent,
                shape = RectangleShape
            )
            .pointerInput(enabled, sequence) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        onKeyPress(sequence)
                    }
                )
            }
            .padding(vertical = Spacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) SemanticColors.TerminalKeys.keyText else SemanticColors.TerminalKeys.keyText.copy(alpha = 0.5f),
            fontSize = TuiCodeFontSize.md,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Repeatable extra key button (long-press triggers repeat).
 * Matches Termux behavior: 400ms initial delay, then 80ms repeat interval.
 */
@Composable
private fun RepeatableExtraKey(
    label: String,
    sequence: String,
    enabled: Boolean,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var repeatJob by remember { mutableStateOf<Job?>(null) }
    
    Box(
        modifier = modifier
            .background(
                color = if (isPressed) SemanticColors.TerminalKeys.keyPressed else Color.Transparent,
                shape = RectangleShape
            )
            .pointerInput(enabled, sequence) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        
                        // Start repeat job
                        repeatJob = scope.launch {
                            onKeyPress(sequence) // First press
                            delay(400) // Initial delay (Termux: FALLBACK_LONG_PRESS_DURATION)
                            while (true) {
                                onKeyPress(sequence)
                                delay(80) // Repeat delay (Termux: DEFAULT_LONG_PRESS_REPEAT_DELAY)
                            }
                        }
                        
                        tryAwaitRelease()
                        
                        // Cancel repeat on release
                        repeatJob?.cancel()
                        repeatJob = null
                        isPressed = false
                    }
                )
            }
            .padding(vertical = Spacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) SemanticColors.TerminalKeys.keyText else SemanticColors.TerminalKeys.keyText.copy(alpha = 0.5f),
            fontSize = TuiCodeFontSize.md,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Modifier key button (CTRL/ALT) with active state.
 * Active state shows in text color, background stays transparent.
 */
@Composable
private fun ModifierKey(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .background(
                color = if (isPressed) SemanticColors.TerminalKeys.keyPressed else Color.Transparent,
                shape = RectangleShape
            )
            .pointerInput(enabled, active) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        onClick()
                    }
                )
            }
            .padding(vertical = Spacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> SemanticColors.TerminalKeys.keyText.copy(alpha = 0.5f)
                active -> SemanticColors.TerminalKeys.activeModifier
                else -> SemanticColors.TerminalKeys.keyText
            },
            fontSize = TuiCodeFontSize.md,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}
