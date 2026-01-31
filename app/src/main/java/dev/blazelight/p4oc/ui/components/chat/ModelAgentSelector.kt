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
    var showModelPicker by remember { mutableStateOf(false) }
    
    val selectModelText = stringResource(R.string.select_model)
    val selectedModelName = remember(selectedModel, availableModels, selectModelText) {
        if (selectedModel == null) return@remember selectModelText
        availableModels.find { it.first == selectedModel.providerID && it.second.id == selectedModel.modelID }
            ?.second?.name ?: selectedModel.modelID
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    shape = MaterialTheme.shapes.small,
                    color = agentColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, agentColor.copy(alpha = 0.4f)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (selectedAgent ?: "build").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = agentColor
                        )
                    }
                }
            }

            if (availableAgents.isNotEmpty() && availableModels.isNotEmpty()) {
                VerticalDivider(
                    modifier = Modifier.height(Sizing.iconLg),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            if (availableModels.isNotEmpty()) {
                Surface(
                    onClick = { showModelPicker = true },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = selectedModelName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp)
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.cd_expand),
                            modifier = Modifier.size(Sizing.iconMd)
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
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.select_model)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, stringResource(R.string.close))
                        }
                    }
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    placeholder = { Text(stringResource(R.string.models_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.clear))
                        }}
                    } else null,
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.xl, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.all)) }
                    )
                    providers.forEach { provider ->
                        FilterChip(
                            selected = selectedCategory == provider,
                            onClick = { 
                                selectedCategory = if (selectedCategory == provider) null else provider 
                            },
                            label = { Text(provider.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (favorites.isNotEmpty()) {
                        item {
                            Text(
                                "Favorites",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 8.dp)
                            )
                        }
                        items(favorites, key = { "${it.model.providerID}/${it.model.modelID}" }) { model ->
                            ModelListItem(
                                model = model,
                                isSelected = model.model == selectedModel,
                                onSelect = { onModelSelected(model.model) },
                                onToggleFavorite = { onToggleFavorite(model.model) }
                            )
                        }
                    }

                    if (recents.isNotEmpty()) {
                        item {
                            Text(
                                "Recent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 8.dp)
                            )
                        }
                        items(recents, key = { "${it.model.providerID}/${it.model.modelID}" }) { model ->
                            ModelListItem(
                                model = model,
                                isSelected = model.model == selectedModel,
                                onSelect = { onModelSelected(model.model) },
                                onToggleFavorite = { onToggleFavorite(model.model) }
                            )
                        }
                    }

                    if (others.isNotEmpty()) {
                        item {
                            Text(
                                if (favorites.isEmpty() && recents.isEmpty()) "All Models" else "Other Models",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 8.dp)
                            )
                        }
                        items(others, key = { "${it.model.providerID}/${it.model.modelID}" }) { model ->
                            ModelListItem(
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
                                    .padding(Spacing.lg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No models found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelListItem(
    model: EnhancedModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onSelect),
        headlineContent = {
            Text(
                text = model.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = model.providerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                model.contextWindow?.let { ctx ->
                    if (ctx > 0) {
                        val ctxK = ctx / 1000
                        item {
                            Text(
                                text = "${ctxK}K",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                if (model.hasReasoning) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "R",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                if (model.hasTools) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "T",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        },
        leadingContent = if (isSelected) {
            { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        trailingContent = {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (model.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (model.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (model.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    )
}
