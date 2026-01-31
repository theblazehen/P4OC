package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.domain.model.Permission
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiOutlinedButton
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.theme.Spacing

@Composable
fun PermissionDialog(
    permission: Permission,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onAlways: () -> Unit
) {
    TuiAlertDialog(
        onDismissRequest = onDeny,
        icon = Icons.Default.Security,
        title = stringResource(R.string.permission_required),
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TuiTextButton(onClick = onAlways) {
                    Text(stringResource(R.string.always_allow))
                }
                TuiButton(onClick = onAllow) {
                    Text(stringResource(R.string.allow))
                }
            }
        },
        dismissButton = {
            TuiOutlinedButton(onClick = onDeny) {
                Text(stringResource(R.string.deny))
            }
        }
    ) {
        Text(permission.title)
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = "Type: ${permission.type}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
