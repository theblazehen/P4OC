package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.ModelInput
import dev.blazelight.p4oc.data.remote.dto.SetActiveModelRequest
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.blazelight.p4oc.ui.theme.Spacing

data class ModelInfo(
    val id: String,
    val name: String,
    val providerId: String,
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val filterProvider: String? = null
)

@HiltViewModel
class ModelControlsViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(ModelControlsState())
    val state: StateFlow<ModelControlsState> = _state.asStateFlow()
    
    init {
        loadModels()
    }
    
    fun loadModels() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val api = connectionManager.getApi() ?: run {
                _state.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            // Use getProviders() which returns all providers with their models
            // The /model endpoint returns HTML (server-side routing issue)
            val result = safeApiCall { api.getProviders() }
            when (result) {
                is ApiResult.Success -> {
                    val models = result.data.all.flatMap { provider ->
                        provider.models.values.map { dto ->
                            ModelInfo(
                                id = dto.id,
                                name = dto.name,
                                providerId = dto.providerId,
                                contextLength = dto.limit?.context ?: dto.contextLength ?: 0,
                                inputCostPer1k = dto.cost?.input ?: dto.inputCostPer1k ?: 0.0,
                                outputCostPer1k = dto.cost?.output ?: dto.outputCostPer1k ?: 0.0,
                                supportsTools = dto.capabilities?.toolcall ?: dto.supportsTools ?: true,
                                supportsReasoning = dto.capabilities?.reasoning ?: dto.supportsReasoning ?: false,
                                isFavorite = _state.value.favorites.contains(dto.id)
                            )
                        }
                    }
                    _state.update { it.copy(models = models, isLoading = false) }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
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
            _state.update { it.copy(selectedModelId = modelId) }
            val api = connectionManager.getApi() ?: return@launch
            // Find the model to get its providerId
            val model = _state.value.models.find { it.id == modelId } ?: return@launch
            val request = SetActiveModelRequest(
                model = ModelInput(
                    providerID = model.providerId,
                    modelID = model.id
                )
            )
            safeApiCall { api.setActiveModel(request) }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }
    
    fun setFilterProvider(providerId: String?) {
        _state.update { it.copy(filterProvider = providerId) }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelControlsScreen(
    viewModel: ModelControlsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val filteredModels = remember(state.models, state.searchQuery, state.filterProvider) {
        state.models.filter { model ->
            val matchesSearch = state.searchQuery.isEmpty() || 
                model.name.contains(state.searchQuery, ignoreCase = true) ||
                model.id.contains(state.searchQuery, ignoreCase = true)
            val matchesProvider = state.filterProvider == null || 
                model.providerId == state.filterProvider
            matchesSearch && matchesProvider
        }.sortedByDescending { it.isFavorite }
    }
    
    val providers = remember(state.models) {
        state.models.map { it.providerId }.distinct()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadModels() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val favoriteModels = filteredModels.filter { it.isFavorite }
                    val otherModels = filteredModels.filter { !it.isFavorite }
                    
                    if (favoriteModels.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.models_favorites),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(favoriteModels, key = { it.id }) { model ->
                            ModelCard(
                                model = model,
                                isSelected = model.id == state.selectedModelId,
                                onSelect = { viewModel.selectModel(model.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(model.id) }
                            )
                        }
                    }
                    
                    if (otherModels.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.models_all),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(otherModels, key = { it.id }) { model ->
                            ModelCard(
                                model = model,
                                isSelected = model.id == state.selectedModelId,
                                onSelect = { viewModel.selectModel(model.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(model.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    state.error?.let { error ->
        Snackbar(
            modifier = Modifier.padding(Spacing.xl),
            action = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        ) {
            Text(error)
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
    providers: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.all)) }
        )
        providers.forEach { provider ->
            FilterChip(
                selected = selected == provider,
                onClick = { onSelect(if (selected == provider) null else provider) },
                label = { Text(provider) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelCard(
    model: ModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            CardDefaults.outlinedCardBorder() 
        else 
            null
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        text = model.providerId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.cd_selected),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (model.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (model.isFavorite) stringResource(R.string.cd_remove_from_favorites) else stringResource(R.string.cd_add_to_favorites),
                            tint = if (model.isFavorite) SemanticColors.Accent.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (model.supportsTools) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.models_tools)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = stringResource(R.string.models_tools),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                if (model.supportsReasoning) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.models_reasoning)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = stringResource(R.string.models_reasoning),
                                modifier = Modifier.size(16.dp)
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
                        text = "Context: ${formatContextLength(model.contextLength)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (model.inputCostPer1k > 0 || model.outputCostPer1k > 0) {
                    Text(
                        text = "$${String.format(java.util.Locale.US, "%.4f", model.inputCostPer1k)} / $${String.format(java.util.Locale.US, "%.4f", model.outputCostPer1k)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatContextLength(length: Int): String = when {
    length >= 1_000_000 -> "${length / 1_000_000}M"
    length >= 1_000 -> "${length / 1_000}K"
    else -> length.toString()
}
