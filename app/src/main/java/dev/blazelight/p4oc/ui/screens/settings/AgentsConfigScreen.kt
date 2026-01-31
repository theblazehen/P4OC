package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentInfo(
    val name: String,
    val description: String,
    val systemPrompt: String? = null,
    val tools: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = true
)

data class AgentsConfigState(
    val agents: List<AgentInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedAgent: AgentInfo? = null
)

@HiltViewModel
class AgentsConfigViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(AgentsConfigState())
    val state: StateFlow<AgentsConfigState> = _state.asStateFlow()
    
    init {
        loadAgents()
    }
    
    fun loadAgents() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val api = connectionManager.getApi() ?: run {
                _state.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.getAgents() }
            when (result) {
                is ApiResult.Success -> {
                    val agents = result.data.map { dto ->
                        AgentInfo(
                            name = dto.name,
                            description = dto.description ?: "",
                            systemPrompt = dto.systemPrompt,
                            tools = dto.tools?.keys?.toList() ?: emptyList(),
                            isEnabled = dto.isEnabled ?: true,
                            isBuiltIn = dto.isBuiltIn ?: dto.builtIn
                        )
                    }
                    _state.update { it.copy(agents = agents, isLoading = false) }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
    
    fun selectAgent(agent: AgentInfo?) {
        _state.update { it.copy(selectedAgent = agent) }
    }
    
    fun toggleAgent(agentName: String) {
        _state.update { state ->
            val updatedAgents = state.agents.map { agent ->
                if (agent.name == agentName) {
                    agent.copy(isEnabled = !agent.isEnabled)
                } else {
                    agent
                }
            }
            state.copy(agents = updatedAgents)
        }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsConfigScreen(
    viewModel: AgentsConfigViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = stringResource(R.string.dismiss)
    
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = dismissLabel,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agents_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAgents() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.agents.isEmpty() && state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = stringResource(R.string.cd_error_state),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.agents_failed_to_load),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { viewModel.loadAgents() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.agents_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                val builtInAgents = state.agents.filter { it.isBuiltIn }
                val customAgents = state.agents.filter { !it.isBuiltIn }
                
                if (builtInAgents.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.agents_builtin),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    items(builtInAgents, key = { it.name }) { agent ->
                        AgentCard(
                            agent = agent,
                            onToggle = { viewModel.toggleAgent(agent.name) },
                            onClick = { viewModel.selectAgent(agent) }
                        )
                    }
                }
                
                if (customAgents.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.agents_custom),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    items(customAgents, key = { it.name }) { agent ->
                        AgentCard(
                            agent = agent,
                            onToggle = { viewModel.toggleAgent(agent.name) },
                            onClick = { viewModel.selectAgent(agent) }
                        )
                    }
                }
                
                if (state.agents.isEmpty()) {
                    item {
                        EmptyAgentsView()
                    }
                }
            }
        }
    }
    
    state.selectedAgent?.let { agent ->
        AgentDetailDialog(
            agent = agent,
            onDismiss = { viewModel.selectAgent(null) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentCard(
    agent: AgentInfo,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = getAgentColor(agent.name).copy(alpha = 0.2f)
                ) {
                    Icon(
                        getAgentIcon(agent.name),
                        contentDescription = stringResource(R.string.cd_agent_icon),
                        modifier = Modifier.padding(8.dp),
                        tint = getAgentColor(agent.name)
                    )
                }
                
                Column {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = agent.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    
                    if (agent.tools.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            agent.tools.take(3).forEach { tool ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { 
                                        Text(
                                            tool, 
                                            style = MaterialTheme.typography.labelSmall
                                        ) 
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            if (agent.tools.size > 3) {
                                Text(
                                    text = "+${agent.tools.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                }
            }
            
            Switch(
                checked = agent.isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun AgentDetailDialog(
    agent: AgentInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                getAgentIcon(agent.name),
                contentDescription = stringResource(R.string.cd_agent_icon),
                tint = getAgentColor(agent.name)
            )
        },
        title = { Text(agent.name) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(agent.description)
                
                if (agent.tools.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.agents_tools),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        agent.tools.forEach { tool ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = stringResource(R.string.agents_tools),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = tool,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                agent.systemPrompt?.let { prompt ->
                    Text(
                        text = stringResource(R.string.agents_system_prompt),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = prompt.take(500) + if (prompt.length > 500) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun EmptyAgentsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = stringResource(R.string.cd_agent_icon),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = stringResource(R.string.agents_no_agents),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.agents_appear_here),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getAgentIcon(name: String) = when (name.lowercase()) {
    "coder", "coding" -> Icons.Default.Code
    "researcher", "research" -> Icons.Default.Search
    "writer", "writing" -> Icons.Default.Edit
    "analyst", "analysis" -> Icons.Default.Analytics
    "planner", "planning" -> Icons.Default.DateRange // EventNote is deprecated, use DateRange instead
    "reviewer", "review" -> Icons.Default.RateReview
    "debugger", "debug" -> Icons.Default.BugReport
    "explorer", "explore" -> Icons.Default.Explore
    else -> Icons.Default.SmartToy
}

private fun getAgentColor(name: String): Color = SemanticColors.Agent.forName(name)
