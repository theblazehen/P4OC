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
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.ConnectionManager
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiTextButton
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen

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


class AgentsConfigViewModel constructor(
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
    viewModel: AgentsConfigViewModel = koinViewModel(),
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
            TuiLoadingScreen(
                modifier = Modifier.padding(padding)
            )
        } else if (state.agents.isEmpty() && state.error != null) {
            val theme = LocalOpenCodeTheme.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "✗",
                        style = MaterialTheme.typography.displayMedium,
                        color = theme.error
                    )
                    Text(
                        text = stringResource(R.string.agents_failed_to_load),
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.error
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
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    val theme = LocalOpenCodeTheme.current
                    Text(
                        text = stringResource(R.string.agents_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textMuted,
                        modifier = Modifier.padding(bottom = Spacing.md)
                    )
                }
                
                val builtInAgents = state.agents.filter { it.isBuiltIn }
                val customAgents = state.agents.filter { !it.isBuiltIn }
                
                if (builtInAgents.isNotEmpty()) {
                    item {
                        val theme = LocalOpenCodeTheme.current
                        Text(
                            text = stringResource(R.string.agents_builtin),
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.accent
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
                        val theme = LocalOpenCodeTheme.current
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(R.string.agents_custom),
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.accent
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
    val theme = LocalOpenCodeTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = getAgentColor(agent.name).copy(alpha = 0.2f)
                ) {
                    Icon(
                        getAgentIcon(agent.name),
                        contentDescription = stringResource(R.string.cd_agent_icon),
                        modifier = Modifier.padding(Spacing.md),
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
                        color = theme.textMuted,
                        maxLines = 2
                    )
                    
                    if (agent.tools.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            agent.tools.take(3).forEach { tool ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { 
                                        Text(
                                            tool, 
                                            style = MaterialTheme.typography.labelSmall
                                        ) 
                                    },
                                    modifier = Modifier.height(Sizing.iconLg)
                                )
                            }
                            if (agent.tools.size > 3) {
                                Text(
                                    text = "+${agent.tools.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.textMuted,
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
    val theme = LocalOpenCodeTheme.current
    TuiAlertDialog(
        onDismissRequest = onDismiss,
        icon = getAgentIcon(agent.name),
        title = agent.name,
        confirmButton = {
            TuiTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        Text(agent.description)
        
        if (agent.tools.isNotEmpty()) {
            Text(
                text = stringResource(R.string.agents_tools),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                agent.tools.forEach { tool ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = stringResource(R.string.agents_tools),
                            modifier = Modifier.size(Sizing.iconXs),
                            tint = theme.textMuted
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
                    containerColor = theme.backgroundElement
                )
            ) {
                Text(
                    text = prompt.take(500) + if (prompt.length > 500) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Spacing.lg)
                )
            }
        }
    }
}

@Composable
private fun EmptyAgentsView() {
    val theme = LocalOpenCodeTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "◇",
            style = MaterialTheme.typography.displayMedium,
            color = theme.textMuted
        )
        Text(
            text = stringResource(R.string.agents_no_agents),
            style = MaterialTheme.typography.titleMedium,
            color = theme.text
        )
        Text(
            text = stringResource(R.string.agents_appear_here),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted
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

@Composable
private fun getAgentColor(name: String): Color = SemanticColors.Agent.forName(name)
