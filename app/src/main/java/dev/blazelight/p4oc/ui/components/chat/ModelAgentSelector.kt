package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.remote.dto.AgentDto
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

@Composable
private fun getAgentColor(agent: AgentDto?): Color {
    if (agent == null) return SemanticColors.AgentSelector.build
    
    agent.color?.let { hex ->
        try {
            return Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {}
    }
    
    return SemanticColors.AgentSelector.forName(agent.name)
}

data class EnhancedModelInfo(
    val model: ModelInput,
    val name: String,
    val providerName: String,
    val contextWindow: Int?,
    val hasReasoning: Boolean,
    val hasTools: Boolean,
    val isFavorite: Boolean = false,
    val isRecent: Boolean = false
)

@Composable
fun ModelAgentSelectorBar(
    availableAgents: List<AgentDto>,
    selectedAgent: String?,
    onAgentSelected: (String) -> Unit,
    availableModels: List<Pair<String, ModelDto>>,
    selectedModel: ModelInput?,
    onModelSelected: (ModelInput) -> Unit,
    favoriteModels: Set<ModelInput> = emptySet(),
    recentModels: List<ModelInput> = emptyList(),
    onToggleFavorite: (ModelInput) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    var showModelPicker by remember { mutableStateOf(false) }
    
    val selectModelText = stringResource(R.string.select_model)
    val selectedModelName = remember(selectedModel, availableModels, selectModelText) {
        if (selectedModel == null) return@remember selectModelText
        availableModels.find { it.first == selectedModel.providerID && it.second.id == selectedModel.modelID }
            ?.second?.name ?: selectedModel.modelID
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.backgroundElement
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (availableAgents.isNotEmpty()) {
                val currentAgent = availableAgents.find { it.name == selectedAgent }
                val agentColor = getAgentColor(currentAgent)
                
                Surface(
                    onClick = {
                        val currentIndex = availableAgents.indexOfFirst { it.name == selectedAgent }
                        val nextIndex = (currentIndex + 1) % availableAgents.size
                        onAgentSelected(availableAgents[nextIndex].name)
                    },
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    color = agentColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, agentColor.copy(alpha = 0.4f)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "@${(selectedAgent ?: "build").lowercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = agentColor
                        )
                    }
                }
            }

            if (availableAgents.isNotEmpty() && availableModels.isNotEmpty()) {
                VerticalDivider(
                    modifier = Modifier.height(Sizing.iconLg),
                    color = theme.border
                )
            }

            if (availableModels.isNotEmpty()) {
                Surface(
                    onClick = { showModelPicker = true },
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    color = theme.background,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.border),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = selectedModelName,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = theme.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                        Text(
                            text = "▾",
                            color = theme.textMuted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            availableModels = availableModels,
            selectedModel = selectedModel,
            favoriteModels = favoriteModels,
            recentModels = recentModels,
            onModelSelected = { 
                onModelSelected(it)
                showModelPicker = false
            },
            onToggleFavorite = onToggleFavorite,
            onDismiss = { showModelPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerDialog(
    availableModels: List<Pair<String, ModelDto>>,
    selectedModel: ModelInput?,
    favoriteModels: Set<ModelInput>,
    recentModels: List<ModelInput>,
    onModelSelected: (ModelInput) -> Unit,
    onToggleFavorite: (ModelInput) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val enhancedModels = remember(availableModels, favoriteModels, recentModels) {
        availableModels.map { (providerId, model) ->
            val modelInput = ModelInput(providerID = providerId, modelID = model.id)
            EnhancedModelInfo(
                model = modelInput,
                name = model.name,
                providerName = providerId.replaceFirstChar { it.uppercase() },
                contextWindow = model.limit?.context,
                hasReasoning = model.capabilities?.reasoning == true,
                hasTools = model.capabilities?.toolcall == true,
                isFavorite = modelInput in favoriteModels,
                isRecent = modelInput in recentModels
            )
        }
    }

    val providers = remember(enhancedModels) {
        enhancedModels.map { it.model.providerID }.distinct().sorted()
    }

    val filteredModels = remember(enhancedModels, searchQuery, selectedCategory) {
        enhancedModels.filter { model ->
            val matchesSearch = searchQuery.isBlank() || 
                model.name.contains(searchQuery, ignoreCase = true) ||
                model.model.providerID.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || model.model.providerID == selectedCategory
            matchesSearch && matchesCategory
        }.sortedWith(
            compareByDescending<EnhancedModelInfo> { it.isFavorite }
                .thenByDescending { it.isRecent }
                .thenBy { it.name }
        )
    }

    val favorites = filteredModels.filter { it.isFavorite }
    val recents = filteredModels.filter { it.isRecent && !it.isFavorite }
    val others = filteredModels.filter { !it.isFavorite && !it.isRecent }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.medium,
            color = theme.background,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.border)
        ) {
            Column {
                // TUI-style header
                Surface(
                    color = theme.backgroundElement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[ ${stringResource(R.string.select_model)} ]",
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.text
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(Sizing.iconButtonSm)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = theme.textMuted,
                                modifier = Modifier.size(Sizing.iconSm)
                            )
                        }
                    }
                }

                // Search field - TUI style
                Surface(
                    color = theme.backgroundElement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.accent
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.models_search_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = theme.textMuted
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(Sizing.iconButtonSm)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = theme.textMuted,
                                    modifier = Modifier.size(Sizing.iconXs)
                                )
                            }
                        }
                    }
                }

                // Provider filter tabs - TUI style
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    TuiFilterTab(
                        text = "all",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    providers.forEach { provider ->
                        TuiFilterTab(
                            text = provider.lowercase(),
                            selected = selectedCategory == provider,
                            onClick = { 
                                selectedCategory = if (selectedCategory == provider) null else provider 
                            }
                        )
                    }
                }

                HorizontalDivider(color = theme.border, thickness = 0.5.dp)

                // Model list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = Spacing.xs)
                ) {
                    if (favorites.isNotEmpty()) {
                        item {
                            TuiSectionHeader(text = "★ favorites", color = theme.warning)
                        }
                        items(favorites, key = { "${it.model.providerID}/${it.model.modelID}" }) { model ->
                            TuiModelListItem(
                                model = model,
                                isSelected = model.model == selectedModel,
                                onSelect = { onModelSelected(model.model) },
                                onToggleFavorite = { onToggleFavorite(model.model) }
                            )
                        }
                    }

                    if (recents.isNotEmpty()) {
                        item {
                            TuiSectionHeader(text = "◷ recent", color = theme.accent)
                        }
                        items(recents, key = { "${it.model.providerID}/${it.model.modelID}" }) { model ->
                            TuiModelListItem(
                                model = model,
                                isSelected = model.model == selectedModel,
                                onSelect = { onModelSelected(model.model) },
                                onToggleFavorite = { onToggleFavorite(model.model) }
                            )
                        }
                    }

                    if (others.isNotEmpty()) {
                        item {
                            TuiSectionHeader(
                                text = if (favorites.isEmpty() && recents.isEmpty()) "models" else "other",
                                color = theme.textMuted
                            )
                        }
                        items(others, key = { "${it.model.providerID}/${it.model.modelID}" }) { model ->
                            TuiModelListItem(
                                model = model,
                                isSelected = model.model == selectedModel,
                                onSelect = { onModelSelected(model.model) },
                                onToggleFavorite = { onToggleFavorite(model.model) }
                            )
                        }
                    }

                    if (filteredModels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "-- no models found --",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = theme.textMuted
                                )
                            }
                        }
                    }
                }

                // Footer with count
                Surface(
                    color = theme.backgroundElement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${filteredModels.size} models",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun TuiFilterTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        onClick = onClick,
        color = if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent,
        shape = MaterialTheme.shapes.extraSmall,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, theme.accent.copy(alpha = 0.5f))
        } else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) theme.accent else theme.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
        )
    }
}

@Composable
private fun TuiSectionHeader(
    text: String,
    color: Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
    )
}

@Composable
private fun TuiModelListItem(
    model: EnhancedModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        onClick = onSelect,
        color = if (isSelected) theme.accent.copy(alpha = 0.1f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator
            Text(
                text = if (isSelected) ">" else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.accent
            )
            
            // Model info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) theme.text else theme.text.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                
                // Metadata row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.providerName.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                    model.contextWindow?.let { ctx ->
                        if (ctx > 0) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textMuted
                            )
                            Text(
                                text = "${ctx / 1000}k",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textMuted
                            )
                        }
                    }
                    if (model.hasReasoning) {
                        Text(
                            text = "[R]",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.warning
                        )
                    }
                    if (model.hasTools) {
                        Text(
                            text = "[T]",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.accent
                        )
                    }
                }
            }
            
            // Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(Sizing.iconButtonSm)
            ) {
                Text(
                    text = if (model.isFavorite) "★" else "☆",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (model.isFavorite) theme.warning else theme.textMuted
                )
            }
        }
    }
}
