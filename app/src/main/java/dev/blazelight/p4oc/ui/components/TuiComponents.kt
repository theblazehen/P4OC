package dev.blazelight.p4oc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.ui.theme.Spacing

/**
 * TUI-style card with dense padding.
 * Use instead of Card { Column(Modifier.padding(16.dp)) { ... } }
 */
@Composable
fun TuiCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
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

/**
 * TUI-style empty state view with icon, title, and optional description.
 */
@Composable
fun TuiEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outline,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(Spacing.emptyStatePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sectionSpacing)
    ) {
        Icon(
            icon,
            contentDescription = title,
            modifier = Modifier.size(48.dp),
            tint = iconTint
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.primary,
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

/**
 * TUI-style row with icon and text.
 */
@Composable
fun TuiIconRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    secondaryText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
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
            modifier = Modifier.size(20.dp)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * TUI-style lazy column with proper content padding.
 */
@Composable
fun TuiLazyColumnDefaults(): Pair<PaddingValues, Arrangement.Vertical> {
    return PaddingValues(Spacing.screenPadding) to Arrangement.spacedBy(Spacing.listItemSpacing)
}
