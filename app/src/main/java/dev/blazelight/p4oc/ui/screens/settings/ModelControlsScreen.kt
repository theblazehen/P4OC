@file:Suppress("ImportOrdering")

package dev.blazelight.p4oc.ui.screens.settings

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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiEmptyState
import dev.blazelight.p4oc.ui.components.TuiSnackbar
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.screens.chat.ModelSelectionCoordinator
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelInfo(
    val id: String,
    val name: String,
    val providerId: String,
    /** The provider's own display name; falls back to [providerId] when the server omits one. */
    val providerName: String = providerId,
    val contextLength: Int = 0,
    val inputCostPer1k: Double = 0.0,
    val outputCostPer1k: Double = 0.0,
    val supportsTools: Boolean = true,
    val supportsReasoning: Boolean = false,
    val isFavorite: Boolean = false
)

data class ModelControlsState(
    val models: List<ModelInfo> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val selectedModelId: String? = null,
    val canSetDefaultModel: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loadFailed: Boolean = false,
    val searchQuery: String = "",
    val filterProvider: String? = null
)

class ModelControlsViewModel constructor(
    private val workspaceClient: WorkspaceClient,
    private val modelSelectionCoordinator: ModelSelectionCoordinator = ModelSelectionCoordinator(),
    serverConnectionRegistry: dev.blazelight.p4oc.core.network.ServerConnectionRegistry? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ModelControlsState(canSetDefaultModel = workspaceClient.supportsGlobalModelConfig)
    )
    val state: StateFlow<ModelControlsState> = _state.asStateFlow()

    init {
        loadModels()
        serverConnectionRegistry?.observeWorkspaceCatalogEvents(
            workspaceClient,
            viewModelScope,
        ) { loadModels(background = true) }
    }

    fun loadModels() = loadModels(background = false)

    @Suppress("CyclomaticComplexMethod")
    private fun loadModels(background: Boolean) {
        viewModelScope.launch {
            if (!background) _state.update { it.copy(isLoading = true, error = null, loadFailed = false) }
            // Use getProviders() which returns all providers with their models
            // The /model endpoint returns HTML (server-side routing issue)
            val result = safeApiCall { workspaceClient.getProviders() }
            when (result) {
                is ApiResult.Success -> {
                    val models = result.data.all.flatMap { provider ->
                        provider.models.values.map { dto ->
                            ModelInfo(
                                id = dto.id,
                                name = dto.name,
                                providerId = dto.providerId,
                                providerName = provider.name.takeIf { it.isNotBlank() } ?: dto.providerId,
                                contextLength = dto.limit?.context ?: dto.contextLength ?: 0,
                                inputCostPer1k = dto.cost?.input ?: dto.inputCostPer1k ?: 0.0,
                                outputCostPer1k = dto.cost?.output ?: dto.outputCostPer1k ?: 0.0,
                                supportsTools = dto.capabilities?.toolcall ?: dto.supportsTools ?: true,
                                supportsReasoning = dto.capabilities?.reasoning ?: dto.supportsReasoning ?: false,
                                isFavorite = _state.value.favorites.contains(dto.id)
                            )
                        }
                    }
                    _state.update { it.copy(models = models, isLoading = false, error = null, loadFailed = false) }
                }
                is ApiResult.Error -> {
                    if (!background) {
                        _state.update {
                            it.copy(isLoading = false, error = "Could not load models. Try again.", loadFailed = true)
                        }
                    }
                }
            }
        }
    }

    fun toggleFavorite(modelId: String) {
        _state.update { state ->
            val newFavorites = if (state.favorites.contains(modelId)) {
                state.favorites - modelId
            } else {
                state.favorites + modelId
            }
            val newModels = state.models.map { model ->
                if (model.id == modelId) model.copy(isFavorite = !model.isFavorite) else model
            }
            state.copy(favorites = newFavorites, models = newModels)
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            val previousModelId = _state.value.selectedModelId
            val model = _state.value.models.find { it.id == modelId } ?: run {
                _state.update {
                    it.copy(selectedModelId = previousModelId, error = "Model not available", loadFailed = false)
                }
                return@launch
            }
            val selectedModel = ModelInput(
                providerID = model.providerId,
                modelID = model.id
            )
            when (safeApiCall { workspaceClient.updateCurrentModel("${model.providerId}/${model.id}") }) {
                is ApiResult.Success -> {
                    _state.update { it.copy(selectedModelId = modelId, error = null, loadFailed = false) }
                    modelSelectionCoordinator.publishActiveModel(
                        workspaceClient.workspace,
                        workspaceClient.generation,
                        selectedModel,
                    )
                }
                is ApiResult.Error -> {
                    _state.update {
                        it.copy(
                            selectedModelId = previousModelId,
                            error = "Could not update the model. Try again.",
                            loadFailed = false
                        )
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setFilterProvider(providerId: String?) {
        _state.update { it.copy(filterProvider = providerId) }
    }

    fun clearSearchAndFilter() {
        _state.update { it.copy(searchQuery = "", filterProvider = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

internal enum class ModelListContentState { MODELS, EMPTY, NO_RESULTS }

internal fun filteredModels(state: ModelControlsState): List<ModelInfo> = state.models.filter { model ->
    val matchesSearch = state.searchQuery.isBlank() ||
        model.name.contains(state.searchQuery, ignoreCase = true) ||
        model.id.contains(state.searchQuery, ignoreCase = true) ||
        model.providerName.contains(state.searchQuery, ignoreCase = true)
    val matchesProvider = state.filterProvider == null || model.providerId == state.filterProvider
    matchesSearch && matchesProvider
}.sortedByDescending { it.isFavorite }

internal fun modelListContentState(
    state: ModelControlsState,
    filteredModels: List<ModelInfo> = filteredModels(state)
): ModelListContentState = when {
    state.models.isEmpty() -> ModelListContentState.EMPTY
    filteredModels.isEmpty() -> ModelListContentState.NO_RESULTS
    else -> ModelListContentState.MODELS
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod", "NoNameShadowing")
@Composable
fun ModelControlsScreen(
    viewModel: ModelControlsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filteredModels = remember(state.models, state.searchQuery, state.filterProvider) {
        filteredModels(state)
    }

    val providers = remember(state.models) {
        state.models.map { it.providerId to it.providerName }.distinct().sortedBy { it.second.lowercase() }
    }

    val theme = LocalOpenCodeTheme.current
    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.models_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.loadModels() },
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!state.canSetDefaultModel) {
                Text(
                    text = stringResource(R.string.v2_model_settings_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
                )
            }
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl)
            )

            if (providers.size > 1) {
                ProviderFilterChips(
                    providers = providers,
                    selected = state.filterProvider,
                    onSelect = viewModel::setFilterProvider,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
                Spacer(Modifier.height(Spacing.md))
            }

            if (state.isLoading) {
                TuiLoadingScreen()
            } else if (state.loadFailed) {
                TuiEmptyState(
                    icon = Icons.Default.ErrorOutline,
                    title = stringResource(R.string.models_load_failed_title),
                    description = stringResource(R.string.models_load_failed_description),
                    iconTint = theme.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    action = {
                        TuiButton(onClick = viewModel::loadModels) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                )
            } else if (modelListContentState(state, filteredModels) == ModelListContentState.EMPTY) {
                TuiEmptyState(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.models_empty_title),
                    description = stringResource(R.string.models_empty_description),
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    action = {
                        TuiButton(onClick = viewModel::loadModels) {
                            Text(stringResource(R.string.refresh))
                        }
                    }
                )
            } else if (modelListContentState(state, filteredModels) == ModelListContentState.NO_RESULTS) {
                TuiEmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.models_no_results_title),
                    description = stringResource(R.string.models_no_results_description),
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    action = {
                        TuiButton(onClick = viewModel::clearSearchAndFilter) {
                            Text(stringResource(R.string.models_clear_search_filters))
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup(),
                    contentPadding = PaddingValues(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    val favoriteModels = filteredModels.filter { it.isFavorite }
                    val otherModels = filteredModels.filter { !it.isFavorite }

                    if (favoriteModels.isNotEmpty()) {
                        item {
                            val theme = LocalOpenCodeTheme.current
                            Text(
                                text = stringResource(R.string.models_favorites),
                                style = MaterialTheme.typography.titleSmall,
                                color = theme.accent,
                                modifier = Modifier.padding(vertical = Spacing.md)
                            )
                        }
                        items(favoriteModels, key = { it.id }) { model ->
                            ModelCard(
                                model = model,
                                isSelected = model.id == state.selectedModelId,
                                enabled = state.canSetDefaultModel,
                                onSelect = { viewModel.selectModel(model.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(model.id) }
                            )
                        }
                    }

                    if (otherModels.isNotEmpty()) {
                        item {
                            val theme = LocalOpenCodeTheme.current
                            Text(
                                text = stringResource(R.string.models_all),
                                style = MaterialTheme.typography.titleSmall,
                                color = theme.accent,
                                modifier = Modifier.padding(vertical = Spacing.md)
                            )
                        }
                        items(otherModels, key = { it.id }) { model ->
                            ModelCard(
                                model = model,
                                isSelected = model.id == state.selectedModelId,
                                enabled = state.canSetDefaultModel,
                                onSelect = { viewModel.selectModel(model.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(model.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    state.error?.takeUnless { state.loadFailed }?.let {
        TuiSnackbar(
            modifier = Modifier.padding(Spacing.xl),
            action = {
                TextButton(onClick = viewModel::clearError, shape = RectangleShape) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        ) {
            Text(stringResource(R.string.models_operation_failed))
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.models_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_models)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun ProviderFilterChips(
    providers: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.all)) },
            shape = RectangleShape
        )
        providers.forEach { (providerId, label) ->
            FilterChip(
                selected = selected == providerId,
                onClick = { onSelect(if (selected == providerId) null else providerId) },
                label = { Text(label) },
                shape = RectangleShape
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "LongMethod")
@Composable
internal fun ModelCard(
    model: ModelInfo,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val currentModelDescription = stringResource(R.string.models_current_model)
    val favoriteActionDescription = stringResource(
        if (model.isFavorite) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                enabled = enabled,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .semantics {
                if (isSelected) stateDescription = currentModelDescription
            },
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                theme.accent.copy(alpha = 0.2f)
            } else {
                theme.backgroundElement
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder()
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = model.providerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (isSelected) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.accent,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    }
                    IconToggleButton(
                        checked = model.isFavorite,
                        onCheckedChange = { onToggleFavorite() },
                        modifier = Modifier.semantics {
                            contentDescription = favoriteActionDescription
                        }
                    ) {
                        Text(
                            text = if (model.isFavorite) "★" else "☆",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (model.isFavorite) SemanticColors.Accent.favorite else theme.textMuted,
                            modifier = Modifier.clearAndSetSemantics { }
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                if (model.supportsTools) {
                    ModelCapabilityBadge(
                        label = stringResource(R.string.models_tools),
                        icon = {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(Sizing.iconXs)
                            )
                        }
                    )
                }
                if (model.supportsReasoning) {
                    ModelCapabilityBadge(
                        label = stringResource(R.string.models_reasoning),
                        icon = {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(Sizing.iconXs)
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (model.contextLength > 0) {
                    Text(
                        text = stringResource(R.string.models_context_format, formatContextLength(model.contextLength)),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
                if (model.inputCostPer1k > 0 || model.outputCostPer1k > 0) {
                    Text(
                        text = "$${String.format(
                            java.util.Locale.US,
                            "%.4f",
                            model.inputCostPer1k
                        )} / $${String.format(java.util.Locale.US, "%.4f", model.outputCostPer1k)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ModelCapabilityBadge(
    label: String,
    icon: @Composable () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        color = theme.background,
        contentColor = theme.textMuted,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatContextLength(length: Int): String = when {
    length >= 1_000_000 -> "${length / 1_000_000}M"
    length >= 1_000 -> "${length / 1_000}K"
    else -> length.toString()
}
