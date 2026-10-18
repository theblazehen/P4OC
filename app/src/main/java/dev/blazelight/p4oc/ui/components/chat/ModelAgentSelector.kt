package dev.blazelight.p4oc.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.remote.dto.AgentDto
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.reasoningEfforts
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

private const val MODEL_PICKER_HEIGHT_FRACTION = 0.7f
private const val PERCENT_SCALE = 100
private const val CONTEXT_METER_CELLS = 4
private const val CONTEXT_METER_CEILING_OFFSET = PERCENT_SCALE - 1
private const val CONTEXT_WARNING_PERCENT = 60
private const val CONTEXT_ERROR_PERCENT = 90

@Composable
private fun getAgentColor(agent: AgentDto?): Color {
    if (agent == null) return SemanticColors.AgentSelector.build

    agent.color?.let { hex ->
        try {
            return Color(android.graphics.Color.parseColor(hex))
        } catch (_: IllegalArgumentException) {
            // Server-provided colors are optional; fall back to the stable name-derived color.
        }
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

data class ModelPickerData(
    val availableModels: List<Pair<String, ModelDto>>,
    val selectedModel: ModelInput?,
    val favoriteModels: Set<ModelInput>,
    val recentModels: List<ModelInput>,
    val providerNames: Map<String, String> = emptyMap(),
)

private data class ModelPickerGroups(
    val providers: List<Pair<String, String>>,
    val favorites: List<EnhancedModelInfo>,
    val recents: List<EnhancedModelInfo>,
    val others: List<EnhancedModelInfo>,
) {
    val count: Int = favorites.size + recents.size + others.size
}

@Composable
@Suppress("CyclomaticComplexMethod", "LongParameterList", "LongMethod", "FunctionNaming")
fun ModelAgentSelectorBar(
    availableAgents: List<AgentDto>,
    selectedAgent: String?,
    onAgentSelected: (String) -> Unit,
    availableModels: List<Pair<String, ModelDto>>,
    selectedModel: ModelInput?,
    onModelSelected: (ModelInput) -> Unit,
    selectedReasoningEffort: String?,
    onReasoningEffortSelected: (String?) -> Unit,
    favoriteModels: Set<ModelInput> = emptySet(),
    recentModels: List<ModelInput> = emptyList(),
    onToggleFavorite: (ModelInput) -> Unit = {},
    usedContextTokens: Long? = null,
    contextUsageModel: ModelInput? = null,
    providerNames: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    var showModelPicker by remember { mutableStateOf(false) }
    var showAgentPicker by remember { mutableStateOf(false) }

    val selectModelText = stringResource(R.string.select_model)
    val selectedModelDto = remember(selectedModel, availableModels) {
        if (selectedModel == null) {
            null
        } else {
            availableModels.find {
                it.first == selectedModel.providerID && it.second.id == selectedModel.modelID
            }?.second
        }
    }
    val selectedModelName = remember(selectedModel, selectedModelDto, selectModelText) {
        selectedModelDto?.name ?: selectedModel?.modelID ?: selectModelText
    }
    val selectedReasoningEfforts = remember(selectedModelDto) {
        selectedModelDto?.reasoningEfforts().orEmpty()
    }
    val contextUsageModelDto = remember(contextUsageModel, availableModels) {
        contextUsageModel?.let { usageModel ->
            availableModels.find {
                it.first == usageModel.providerID && it.second.id == usageModel.modelID
            }?.second
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("agent_selector"),
        color = theme.background
    ) {
        Row(
            modifier = Modifier.padding(
                start = Spacing.md,
                end = Spacing.md,
                top = Spacing.md,
                bottom = Spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (availableAgents.isNotEmpty()) {
                    val currentAgent = availableAgents.find { it.name == selectedAgent }
                    val agentColor = getAgentColor(currentAgent)
                    val currentAgentName = (selectedAgent ?: availableAgents.first().name).lowercase()
                    val agentSelectorDescription = stringResource(
                        R.string.agent_selector_current,
                        currentAgentName
                    )

                    Box {
                        Surface(
                            onClick = { showAgentPicker = true },
                            shape = RectangleShape,
                            color = agentColor.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(
                                Sizing.strokeMd,
                                agentColor.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .height(Sizing.buttonHeightMd)
                                .semantics { contentDescription = agentSelectorDescription }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Text(
                                    text = "@$currentAgentName",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = agentColor
                                )
                                Text(
                                    text = "▾",
                                    color = agentColor,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showAgentPicker,
                            onDismissRequest = { showAgentPicker = false }
                        ) {
                            availableAgents.forEach { agent ->
                                DropdownMenuItem(
                                    text = {
                                        Column(modifier = Modifier.widthIn(max = Sizing.panelWidthLg)) {
                                            Text(
                                                text = "@${agent.name.lowercase()}",
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            agent.description?.takeIf { it.isNotBlank() }?.let { description ->
                                                Text(
                                                    text = description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = theme.textMuted,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onAgentSelected(agent.name)
                                        showAgentPicker = false
                                    },
                                    leadingIcon = if (agent.name == selectedAgent) {
                                        { Text("✓", color = getAgentColor(agent)) }
                                    } else {
                                        null
                                    }
                                )
                            }
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
                        border = androidx.compose.foundation.BorderStroke(Sizing.strokeMd, theme.border),
                        modifier = Modifier.height(Sizing.buttonHeightMd)
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
                                modifier = Modifier.widthIn(max = Sizing.panelWidthLg)
                            )
                            Text(
                                text = "▾",
                                color = theme.textMuted,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }

                if (selectedReasoningEfforts.size > 1) {
                    ReasoningEffortSelect(
                        efforts = selectedReasoningEfforts,
                        selectedEffort = selectedReasoningEffort,
                        onEffortSelected = onReasoningEffortSelected,
                    )
                }
            }
            val ctxWindow = contextUsageModelDto?.limit?.context
            if (usedContextTokens != null && ctxWindow != null && ctxWindow > 0) {
                Spacer(Modifier.width(Spacing.sm))
                ContextUsageMeter(
                    percent = (
                        usedContextTokens.coerceIn(0L, ctxWindow.toLong()) *
                            PERCENT_SCALE / ctxWindow
                        ).toInt(),
                )
            }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            data = ModelPickerData(
                availableModels = availableModels,
                selectedModel = selectedModel,
                favoriteModels = favoriteModels,
                recentModels = recentModels,
                providerNames = providerNames,
            ),
            onModelSelected = {
                onModelSelected(it)
                showModelPicker = false
            },
            onToggleFavorite = onToggleFavorite,
            onDismiss = { showModelPicker = false }
        )
    }
}

/**
 * Compact context-window usage meter for the composer control row — `▓▓▓░ 62%`.
 * Matches design 05; four cells, warning/error tint as usage climbs.
 */
@Composable
@Suppress("FunctionNaming")
private fun ContextUsageMeter(percent: Int) {
    val theme = LocalOpenCodeTheme.current
    val filled = (percent * CONTEXT_METER_CELLS + CONTEXT_METER_CEILING_OFFSET) / PERCENT_SCALE
    val bar = buildString {
        repeat(CONTEXT_METER_CELLS) { append(if (it < filled) '▓' else '░') }
    }
    val label = stringResource(R.string.context_usage_meter, bar, percent)
    val description = stringResource(R.string.context_usage_percent, percent)
    val color = when {
        percent >= CONTEXT_ERROR_PERCENT -> theme.error
        percent >= CONTEXT_WARNING_PERCENT -> theme.warning
        else -> theme.textMuted
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        color = color,
        maxLines = 1,
        modifier = Modifier.semantics {
            contentDescription = description
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
fun ModelPickerDialog(
    data: ModelPickerData,
    onModelSelected: (ModelInput) -> Unit,
    onToggleFavorite: (ModelInput) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val groups = remember(data, searchQuery, selectedCategory) {
        buildModelPickerGroups(data, searchQuery, selectedCategory)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(MODEL_PICKER_HEIGHT_FRACTION)
                    .testTag("model_picker_sheet"),
                shape = RectangleShape,
                color = theme.backgroundPanel,
                border = androidx.compose.foundation.BorderStroke(Sizing.strokeMd, theme.border)
            ) {
                Column {
                    ModelPickerHeader(onDismiss)
                    ModelSearchField(searchQuery, onQueryChange = { searchQuery = it })
                    ProviderFilterRow(groups.providers, selectedCategory) { selectedCategory = it }

                    HorizontalDivider(color = theme.border, thickness = Sizing.dividerThickness)
                    ModelPickerList(
                        groups = groups,
                        selectedModel = data.selectedModel,
                        onModelSelected = onModelSelected,
                        onToggleFavorite = onToggleFavorite,
                    )

                    HorizontalDivider(color = theme.border, thickness = Sizing.dividerThickness)

                    // Footer with count
                    Text(
                        text = "${groups.count} models",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    )
                }
            }
        }
    }
}

private fun buildModelPickerGroups(
    data: ModelPickerData,
    searchQuery: String,
    selectedCategory: String?,
): ModelPickerGroups {
    val enhancedModels = data.availableModels.map { (providerId, model) ->
        val modelInput = ModelInput(providerID = providerId, modelID = model.id)
        EnhancedModelInfo(
            model = modelInput,
            name = model.name,
            providerName = data.providerNames[providerId] ?: providerId,
            contextWindow = model.limit?.context,
            hasReasoning = model.capabilities?.reasoning == true,
            hasTools = model.capabilities?.toolcall == true,
            isFavorite = modelInput in data.favoriteModels,
            isRecent = modelInput in data.recentModels,
        )
    }
    val providers = enhancedModels
        .map { it.model.providerID to it.providerName }
        .distinct()
        .sortedBy { it.second.lowercase() }
    val filteredModels = enhancedModels
        .filter { model ->
            val matchesSearch = searchQuery.isBlank() ||
                model.name.contains(searchQuery, ignoreCase = true) ||
                model.providerName.contains(searchQuery, ignoreCase = true) ||
                model.model.providerID.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || model.model.providerID == selectedCategory
            matchesSearch && matchesCategory
        }
        .sortedWith(
            compareByDescending<EnhancedModelInfo> { it.isFavorite }
                .thenByDescending { it.isRecent }
                .thenBy { it.name },
        )
    return ModelPickerGroups(
        providers = providers,
        favorites = filteredModels.filter { it.isFavorite },
        recents = filteredModels.filter { it.isRecent && !it.isFavorite },
        others = filteredModels.filter { !it.isFavorite && !it.isRecent },
    )
}

@Composable
@Suppress("FunctionNaming")
private fun ModelPickerHeader(onDismiss: () -> Unit) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.select_model),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = theme.text,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(Sizing.iconButtonSm)) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = theme.textMuted,
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun ModelSearchField(query: String, onQueryChange: (String) -> Unit) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        color = theme.backgroundElement,
        shape = RectangleShape,
        border = androidx.compose.foundation.BorderStroke(Sizing.strokeMd, theme.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizing.buttonHeightMd)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).testTag("model_search_field"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = theme.text,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.models_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = theme.textMuted,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(Sizing.iconButtonSm),
                ) {
                    Icon(Icons.Default.Clear, stringResource(R.string.clear), tint = theme.textMuted)
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun ProviderFilterRow(
    providers: List<Pair<String, String>>,
    selectedProvider: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TuiFilterTab(stringResource(R.string.all), selectedProvider == null) { onSelect(null) }
        providers.forEach { (providerId, label) ->
            TuiFilterTab(label, selectedProvider == providerId) {
                onSelect(providerId.takeUnless { it == selectedProvider })
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun ColumnScope.ModelPickerList(
    groups: ModelPickerGroups,
    selectedModel: ModelInput?,
    onModelSelected: (ModelInput) -> Unit,
    onToggleFavorite: (ModelInput) -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    LazyColumn(
        modifier = Modifier.weight(1f).selectableGroup(),
        contentPadding = PaddingValues(vertical = Spacing.xs),
    ) {
        modelGroup("★ favorites", groups.favorites, selectedModel, onModelSelected, onToggleFavorite)
        modelGroup("recent", groups.recents, selectedModel, onModelSelected, onToggleFavorite)
        modelGroup(
            if (groups.favorites.isEmpty() && groups.recents.isEmpty()) "models" else "other",
            groups.others,
            selectedModel,
            onModelSelected,
            onToggleFavorite,
        )
        if (groups.count == 0) {
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                    Text(
                        "-- no models found --",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.modelGroup(
    title: String,
    models: List<EnhancedModelInfo>,
    selectedModel: ModelInput?,
    onModelSelected: (ModelInput) -> Unit,
    onToggleFavorite: (ModelInput) -> Unit,
) {
    if (models.isEmpty()) return
    item { TuiSectionHeader(title) }
    items(models, key = { "${it.model.providerID}/${it.model.modelID}" }) { model ->
        TuiModelListItem(
            model = model,
            isSelected = model.model == selectedModel,
            onSelect = { onModelSelected(model.model) },
            onToggleFavorite = { onToggleFavorite(model.model) },
        )
    }
}

@Composable
private fun ReasoningEffortSelect(
    efforts: List<String>,
    selectedEffort: String?,
    onEffortSelected: (String?) -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    var expanded by remember { mutableStateOf(false) }
    val options = remember(efforts) { listOf<String?>(null) + efforts }
    val currentLabel = selectedEffort ?: "default"

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RectangleShape,
            color = theme.background,
            border = androidx.compose.foundation.BorderStroke(Sizing.strokeMd, theme.border),
            modifier = Modifier
                .height(Sizing.buttonHeightMd)
                .testTag("reasoning_effort_select")
        ) {
            Box(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = if (selectedEffort == null) theme.textMuted else theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = theme.backgroundPanel,
        ) {
            options.forEach { effort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = effort ?: "default",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (effort == selectedEffort) theme.accent else theme.text,
                        )
                    },
                    onClick = {
                        onEffortSelected(effort)
                        expanded = false
                    },
                )
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
        color = if (selected) theme.backgroundElement else Color.Transparent,
        shape = RectangleShape,
        modifier = Modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Tab,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.primary else theme.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
        )
    }
}

/** Uppercase, letter-spaced section label — `RECENT` / `OTHER`, matching design 06. */
@Composable
private fun TuiSectionHeader(text: String) {
    val theme = LocalOpenCodeTheme.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        color = theme.textMuted,
        modifier = Modifier.padding(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.md,
            bottom = Spacing.xs
        )
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
    val addFavoriteDescription = stringResource(R.string.cd_add_to_favorites)
    val removeFavoriteDescription = stringResource(R.string.cd_remove_from_favorites)
    val providerColor = SemanticColors.Provider.forName(model.model.providerID).first

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (isSelected) theme.backgroundElement else Color.Transparent)
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
    ) {
        // Left accent strip on the selected row.
        Box(
            modifier = Modifier
                .width(Sizing.strokeThick)
                .fillMaxHeight()
                .background(if (isSelected) theme.success else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection chevron (reserves width so squares stay aligned).
            Text(
                text = if (isSelected) ">" else " ",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = theme.primary
            )

            // Provider color square.
            Text(
                text = "■",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = providerColor
            )

            // Model info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = theme.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Metadata row: provider · context
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.providerName,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = theme.textMuted
                    )
                    model.contextWindow?.let { ctx ->
                        if (ctx > 0) {
                            Text(
                                text = "· ${formatContextWindow(ctx)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = theme.textMuted
                            )
                        }
                    }
                }
            }

            // Capability badges + favorite, right-aligned.
            if (model.hasReasoning) {
                Text(
                    text = "[R]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted
                )
            }
            if (model.hasTools) {
                Text(
                    text = "[T]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(Sizing.iconButtonSm)
                    .semantics {
                        contentDescription = if (model.isFavorite) {
                            removeFavoriteDescription
                        } else {
                            addFavoriteDescription
                        }
                    }
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

private const val CONTEXT_MILLION = 1_000_000
private const val CONTEXT_THOUSAND = 1_000

/** `200K`, `1M`, `64K` — uppercase, matching design 06. */
private fun formatContextWindow(ctx: Int): String = when {
    ctx >= CONTEXT_MILLION -> "${ctx / CONTEXT_MILLION}M"
    ctx >= CONTEXT_THOUSAND -> "${ctx / CONTEXT_THOUSAND}K"
    else -> ctx.toString()
}
