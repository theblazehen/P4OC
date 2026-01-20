package com.pocketcode.ui.components.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class ModelOption(
    val key: String,
    val displayName: String
)

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    attachedFiles: List<SelectedFile> = emptyList(),
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Column {
            if (attachedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachedFiles.forEach { file ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveAttachment(file.path) },
                            label = { 
                                Text(
                                    file.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                ) 
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onAttachClick,
                    enabled = enabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        modifier = Modifier.size(22.dp)
                    )
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp),
                    placeholder = { Text("Type a message...") },
                    enabled = enabled,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (value.isNotBlank() && enabled) onSend() }
                    ),
                    shape = MaterialTheme.shapes.extraLarge
                )

                FilledIconButton(
                    onClick = onSend,
                    enabled = (value.isNotBlank() || attachedFiles.isNotEmpty()) && enabled && !isLoading,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
