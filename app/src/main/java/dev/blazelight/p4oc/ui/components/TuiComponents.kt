package dev.blazelight.p4oc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

// =============================================================================
// CARDS
// =============================================================================

/**
 * TUI-style card with dense padding.
 * Use instead of Card { Column(Modifier.padding(16.dp)) { ... } }
 */
@Composable
fun TuiCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = colors
        ) {
            Column(
                modifier = Modifier.padding(Spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardContentSpacing),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = colors
        ) {
            Column(
                modifier = Modifier.padding(Spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardContentSpacing),
                content = content
            )
        }
    }
}

/**
 * TUI-style outlined card.
 */
@Composable
fun TuiOutlinedCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            colors = colors
        ) {
            Column(
                modifier = Modifier.padding(Spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardContentSpacing),
                content = content
            )
        }
    } else {
        OutlinedCard(
            modifier = modifier.fillMaxWidth(),
            colors = colors
        ) {
            Column(
                modifier = Modifier.padding(Spacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardContentSpacing),
                content = content
            )
        }
    }
}

// =============================================================================
// BUTTONS
// =============================================================================

/**
 * TUI-style filled button with compact height.
 */
@Composable
fun TuiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(Sizing.buttonHeightMd),
        enabled = enabled,
        colors = colors,
        shape = RectangleShape,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * TUI-style outlined button.
 */
@Composable
fun TuiOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Sizing.buttonHeightMd),
        enabled = enabled,
        colors = colors,
        shape = RectangleShape,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * TUI-style text button with minimal padding.
 */
@Composable
fun TuiTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(Sizing.buttonHeightMd),
        enabled = enabled,
        colors = colors,
        shape = RectangleShape,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * TUI-style icon button with consistent sizing.
 */
@Composable
fun TuiIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizing.iconButtonMd),
        enabled = enabled,
        colors = colors,
        content = content
    )
}

// =============================================================================
// TEXT FIELDS
// =============================================================================

/**
 * TUI-style outlined text field with compact styling.
 */
@Composable
fun TuiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingIcon = leadingIcon?.let { 
            { Icon(it, contentDescription = null, modifier = Modifier.size(Sizing.iconMd)) } 
        },
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

// =============================================================================
// LIST ITEMS
// =============================================================================

/**
 * TUI-style clickable list item with icon, text, and optional trailing content.
 */
@Composable
fun TuiListItem(
    headlineText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalOpenCodeTheme.current.textMuted,
    leadingContent: @Composable (() -> Unit)? = null,
    supportingText: String? = null,
    overlineText: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val theme = LocalOpenCodeTheme.current
    val clickableModifier = if (onClick != null && enabled) {
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.sm)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.sm)
    }
    
    Row(
        modifier = clickableModifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.inlineSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading content
        when {
            leadingContent != null -> leadingContent()
            leadingIcon != null -> Icon(
                leadingIcon,
                contentDescription = null,
                tint = if (enabled) leadingIconTint else leadingIconTint.copy(alpha = 0.38f),
                modifier = Modifier.size(Sizing.iconMd)
            )
        }
        
        // Text content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            overlineText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMuted.copy(alpha = if (enabled) 1f else 0.38f)
                )
            }
            Text(
                text = headlineText,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.text.copy(alpha = if (enabled) 1f else 0.38f)
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted.copy(alpha = if (enabled) 1f else 0.38f)
                )
            }
        }
        
        // Trailing content
        trailingContent?.invoke()
    }
}

/**
 * TUI-style row with icon and text (simpler than TuiListItem).
 */
@Composable
fun TuiIconRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    iconTint: Color = LocalOpenCodeTheme.current.textMuted,
    secondaryText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.listItemPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inlineSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Sizing.iconMd)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
            secondaryText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted
                )
            }
        }
        trailing?.invoke()
    }
}

// =============================================================================
// DIVIDERS & CHIPS
// =============================================================================

/**
 * TUI-style thin divider.
 */
@Composable
fun TuiDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalOpenCodeTheme.current.borderSubtle
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = Sizing.dividerThickness,
        color = color
    )
}

/**
 * TUI-style chip with compact height.
 */
@Composable
fun TuiChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    if (onClick != null) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            modifier = modifier.height(Sizing.chipHeight),
            enabled = enabled,
            shape = RectangleShape,
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, modifier = Modifier.size(Sizing.iconSm)) }
            }
        )
    } else {
        AssistChip(
            onClick = { },
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            modifier = modifier.height(Sizing.chipHeight),
            enabled = false,
            shape = RectangleShape,
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, modifier = Modifier.size(Sizing.iconSm)) }
            }
        )
    }
}

// =============================================================================
// EMPTY STATES & SECTIONS
// =============================================================================

/**
 * TUI-style empty state view with icon, title, and optional description.
 */
@Composable
fun TuiEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconTint: Color = LocalOpenCodeTheme.current.border,
    action: (@Composable () -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = modifier.padding(Spacing.emptyStatePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing)
    ) {
        Icon(
            icon,
            contentDescription = title,
            modifier = Modifier.size(Sizing.iconHero),
            tint = iconTint
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textMuted
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMuted
            )
        }
        action?.invoke()
    }
}

/**
 * TUI-style section with header and content.
 */
@Composable
fun TuiSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalOpenCodeTheme.current.accent,
            modifier = Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.xs)
        )
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.listItemSpacing),
            content = content
        )
    }
}

/**
 * TUI-style bottom sheet content wrapper.
 */
@Composable
fun TuiSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenPadding)
            .padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardContentSpacing),
        content = content
    )
}

// =============================================================================
// DIALOGS
// =============================================================================

/**
 * TUI-style alert dialog with dense padding.
 */
@Composable
fun TuiAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = theme.backgroundPanel,
        iconContentColor = theme.accent,
        titleContentColor = theme.text,
        textContentColor = theme.textMuted,
        shape = RectangleShape,
        icon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(Sizing.iconLg)) } },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.cardContentSpacing),
                content = content
            )
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

/**
 * TUI-style confirm dialog with preset confirm/cancel buttons.
 */
@Composable
fun TuiConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false
) {
    TuiAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        icon = icon,
        confirmButton = {
            TuiButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = LocalOpenCodeTheme.current.error,
                        contentColor = LocalOpenCodeTheme.current.background
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TuiTextButton(onClick = onDismissRequest) {
                Text(dismissText)
            }
        }
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalOpenCodeTheme.current.textMuted
        )
    }
}

/**
 * TUI-style input dialog with a text field.
 */
@Composable
fun TuiInputDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    initialValue: String = "",
    placeholder: String? = null,
    label: String? = null,
    confirmText: String = "OK",
    dismissText: String = "Cancel"
) {
    val (text, setText) = remember { androidx.compose.runtime.mutableStateOf(initialValue) }
    
    TuiAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        modifier = modifier,
        confirmButton = {
            TuiButton(
                onClick = {
                    onConfirm(text)
                    onDismissRequest()
                },
                enabled = text.isNotBlank()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TuiTextButton(onClick = onDismissRequest) {
                Text(dismissText)
            }
        }
    ) {
        TuiTextField(
            value = text,
            onValueChange = setText,
            label = label,
            placeholder = placeholder,
            singleLine = true
        )
    }
}

// =============================================================================
// DROPDOWN MENUS
// =============================================================================

/**
 * TUI-style dropdown menu with rounded card shape and border.
 * Wrap DropdownMenu calls with this for consistent styling.
 */
@Composable
fun TuiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.backgroundElement)
            .border(1.dp, theme.border.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        content = content
    )
}

/**
 * TUI-style dropdown menu item with compact padding.
 */
@Composable
fun TuiDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val theme = LocalOpenCodeTheme.current
    DropdownMenuItem(
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (enabled) theme.text else theme.textMuted
            )
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(Sizing.iconMd), tint = theme.textMuted) }
        },
        trailingIcon = trailingIcon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(Sizing.iconMd), tint = theme.textMuted) }
        },
        enabled = enabled,
        colors = MenuItemColors(
            textColor = if (enabled) theme.text else theme.textMuted,
            leadingIconColor = theme.textMuted,
            trailingIconColor = theme.textMuted,
            disabledTextColor = theme.textMuted,
            disabledLeadingIconColor = theme.textMuted,
            disabledTrailingIconColor = theme.textMuted
        ),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)
    )
}

// =============================================================================
// LOADING & PROGRESS
// =============================================================================

/**
 * TUI-style loading indicator with optional text.
 */
@Composable
fun TuiLoadingIndicator(
    modifier: Modifier = Modifier,
    text: String? = null
) {
    Row(
        modifier = modifier.padding(Spacing.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.inlineSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Sizing.iconMd),
            strokeWidth = Sizing.strokeMd
        )
        text?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalOpenCodeTheme.current.textMuted
            )
        }
    }
}

/**
 * TUI-style full-screen loading state.
 */
@Composable
fun TuiLoadingScreen(
    modifier: Modifier = Modifier,
    text: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        TuiLoadingIndicator(text = text)
    }
}

// =============================================================================
// BADGES & INDICATORS
// =============================================================================

/**
 * TUI-style badge/tag.
 */
@Composable
fun TuiBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalOpenCodeTheme.current.backgroundPanel,
    contentColor: Color = LocalOpenCodeTheme.current.secondary
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RectangleShape
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * TUI-style status dot indicator.
 */
@Composable
fun TuiStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = Sizing.iconXs
) {
    Surface(
        modifier = modifier.size(size),
        color = color,
        shape = androidx.compose.foundation.shape.CircleShape
    ) {}
}

// =============================================================================
// UTILITY FUNCTIONS
// =============================================================================

/**
 * TUI-style lazy column with proper content padding.
 */
@Composable
fun TuiLazyColumnDefaults(): Pair<PaddingValues, Arrangement.Vertical> {
    return PaddingValues(Spacing.screenPadding) to Arrangement.spacedBy(Spacing.listItemSpacing)
}

/**
 * Standard content padding for lazy lists.
 */
val TuiContentPadding: PaddingValues
    get() = PaddingValues(Spacing.screenPadding)

/**
 * Standard vertical arrangement for lists.
 */
val TuiListArrangement: Arrangement.Vertical
    get() = Arrangement.spacedBy(Spacing.listItemSpacing)

// =============================================================================
// SNACKBARS
// =============================================================================

/**
 * TUI-style snackbar with themed colors and rectangular shape.
 * Use instead of raw Snackbar() to ensure consistent theming.
 */
@Composable
fun TuiSnackbar(
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    Snackbar(
        modifier = modifier,
        action = action,
        containerColor = theme.backgroundElement,
        contentColor = theme.text,
        actionContentColor = theme.accent,
        dismissActionContentColor = theme.accent,
        shape = RectangleShape,
        content = content
    )
}

// =============================================================================
// SWITCHES
// =============================================================================

/**
 * TUI-style two-cell segmented toggle switch.
 *
 * Renders as two adjacent rectangular cells — OFF (left) and ON (right).
 * The active cell is filled with accent color and inverted text;
 * the inactive cell is hollow with muted text. Fixed width in both states.
 *
 * ┌──────┬──────┐      ┌──────┬──────┐
 * │▓ OFF ▓│  ON  │  vs  │  OFF │▓ ON ▓│
 * └──────┴──────┘      └──────┴──────┘
 */
@Composable
fun TuiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val theme = LocalOpenCodeTheme.current
    val contentAlpha = if (enabled) 1f else 0.38f

    // Active cell: solid accent background with inverted text
    val activeBg = theme.accent.copy(alpha = contentAlpha)
    val activeText = theme.background.copy(alpha = contentAlpha)

    // Inactive cell: panel background with muted text
    val inactiveBg = theme.backgroundPanel.copy(alpha = contentAlpha)
    val inactiveText = theme.textMuted.copy(alpha = contentAlpha)

    val outerBorder = theme.border.copy(alpha = contentAlpha)
    val dividerColor = theme.borderSubtle.copy(alpha = contentAlpha)

    val monoLabel = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        letterSpacing = 0.5.sp
    )

    Row(
        modifier = modifier
            .height(Sizing.buttonHeightSm)
            .border(
                width = Sizing.strokeMd,
                color = outerBorder,
                shape = RectangleShape
            )
            .then(
                if (onCheckedChange != null && enabled) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // OFF cell (left)
        Box(
            modifier = Modifier
                .width(Sizing.switchCellWidth)
                .fillMaxHeight()
                .background(
                    color = if (!checked) activeBg else inactiveBg,
                    shape = RectangleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OFF",
                style = monoLabel,
                color = if (!checked) activeText else inactiveText
            )
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .width(Sizing.strokeMd)
                .fillMaxHeight()
                .background(dividerColor)
        )

        // ON cell (right)
        Box(
            modifier = Modifier
                .width(Sizing.switchCellWidth)
                .fillMaxHeight()
                .background(
                    color = if (checked) activeBg else inactiveBg,
                    shape = RectangleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ON",
                style = monoLabel,
                color = if (checked) activeText else inactiveText
            )
        }
    }
}

// =============================================================================
// STEPPERS
// =============================================================================

/**
 * TUI-style integer stepper: [−] value [+]
 *
 * Renders as three adjacent rectangular cells sharing borders,
 * like a terminal spin-box widget. Use instead of Slider for
 * small discrete integer ranges.
 */
@Composable
fun TuiStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    valueLabel: String = value.toString(),
    enabled: Boolean = true
) {
    val theme = LocalOpenCodeTheme.current
    val contentAlpha = if (enabled) 1f else 0.38f
    val borderColor = theme.borderSubtle.copy(alpha = contentAlpha)

    val canDecrement = enabled && value - step >= range.first
    val canIncrement = enabled && value + step <= range.last

    Row(
        modifier = modifier
            .height(Sizing.buttonHeightMd)
            .border(
                width = Sizing.strokeMd,
                color = borderColor,
                shape = RectangleShape
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [−] button
        StepperButton(
            label = "−",
            enabled = canDecrement,
            onClick = { onValueChange((value - step).coerceAtLeast(range.first)) }
        )

        // Vertical divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(Sizing.strokeMd)
                .background(borderColor)
        )

        // Value display
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .defaultMinSize(minWidth = Sizing.panelWidthSm)
                .background(theme.backgroundElement.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = theme.accent.copy(alpha = contentAlpha),
                textAlign = TextAlign.Center
            )
        }

        // Vertical divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(Sizing.strokeMd)
                .background(borderColor)
        )

        // [+] button
        StepperButton(
            label = "+",
            enabled = canIncrement,
            onClick = { onValueChange((value + step).coerceAtMost(range.last)) }
        )
    }
}

@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val textColor = if (enabled) theme.text else theme.textMuted.copy(alpha = 0.38f)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(Sizing.buttonHeightMd)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = textColor
        )
    }
}