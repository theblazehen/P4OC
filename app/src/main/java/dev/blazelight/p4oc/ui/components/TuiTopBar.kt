package dev.blazelight.p4oc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

/**
 * Shared compact top bar for all tab-hosted screens.
 *
 * Replaces both custom Surface+Row bars and stock Material3 TopAppBar
 * with a single consistent height across Chat, Files, Terminal, Settings, etc.
 *
 * Height is determined by content + Spacing.xs vertical padding (~44-48dp),
 * not the stock TopAppBar's hardcoded 64dp minimum.
 */
@Composable
fun TuiTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: @Composable (() -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.backgroundElement,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(Sizing.iconButtonMd)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.size(Sizing.iconLg)
                    )
                }
            }

            if (titleContent != null) {
                Box(modifier = Modifier.weight(1f)) {
                    titleContent()
                }
            } else {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            actions()
        }
    }
}
