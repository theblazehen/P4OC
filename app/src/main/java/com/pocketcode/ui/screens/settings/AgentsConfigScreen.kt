package com.pocketcode.ui.screens.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcode.core.network.ApiResult
import com.pocketcode.core.network.OpenCodeApi
import com.pocketcode.core.network.safeApiCall
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
    private val api: OpenCodeApi
) : ViewModel() {
    
    private val _state = MutableStateFlow(AgentsConfigState())
    val state: StateFlow<AgentsConfigState> = _state.asStateFlow()
    
    init {
        loadAgents()
    }
    
    fun loadAgents() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = safeApiCall { api.listAgents() }
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
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agents") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAgents() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Failed to load agents",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { viewModel.loadAgents() }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Configure AI agents and their capabilities",
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
                            text = "Built-in Agents",
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
                            text = "Custom Agents",
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
                .padding(16.dp),
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
                        contentDescription = null,
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
                contentDescription = null,
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
                        text = "Tools",
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
                                    contentDescription = null,
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
                        text = "System Prompt",
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
                Text("Close")
            }
        }
    )
}

@Composable
private fun EmptyAgentsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "No agents configured",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Agents will appear here once configured on the server",
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
    "planner", "planning" -> Icons.Default.EventNote
    "reviewer", "review" -> Icons.Default.RateReview
    "debugger", "debug" -> Icons.Default.BugReport
    "explorer", "explore" -> Icons.Default.Explore
    else -> Icons.Default.SmartToy
}

private fun getAgentColor(name: String): Color = when (name.lowercase()) {
    "coder", "coding" -> Color(0xFF42A5F5)
    "researcher", "research" -> Color(0xFF66BB6A)
    "writer", "writing" -> Color(0xFFAB47BC)
    "analyst", "analysis" -> Color(0xFFFFA726)
    "planner", "planning" -> Color(0xFF26A69A)
    "reviewer", "review" -> Color(0xFFEF5350)
    "debugger", "debug" -> Color(0xFFEC407A)
    "explorer", "explore" -> Color(0xFF7E57C2)
    else -> Color(0xFF78909C)
}
