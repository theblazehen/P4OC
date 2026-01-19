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
import com.pocketcode.core.network.ConnectionManager
import com.pocketcode.core.network.safeApiCall
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SkillInfo(
    val name: String,
    val description: String,
    val source: String,
    val isEnabled: Boolean = true,
    val tools: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
    val prompts: List<String> = emptyList()
)

data class SkillsState(
    val skills: List<SkillInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedSkill: SkillInfo? = null
)

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(SkillsState())
    val state: StateFlow<SkillsState> = _state.asStateFlow()
    
    init {
        loadSkills()
    }
    
    fun loadSkills() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val api = connectionManager.getApi() ?: run {
                _state.update { it.copy(isLoading = false, error = "Not connected") }
                return@launch
            }
            val result = safeApiCall { api.getMcpStatus() }
            when (result) {
                is ApiResult.Success -> {
                    val skills = result.data.map { (name, status) ->
                        SkillInfo(
                            name = name,
                            description = "MCP Server: $name",
                            source = "mcp",
                            isEnabled = status.status == "connected",
                            tools = emptyList(),
                            resources = emptyList(),
                            prompts = emptyList()
                        )
                    }
                    _state.update { it.copy(skills = skills, isLoading = false) }
                }
                is ApiResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
    
    fun selectSkill(skill: SkillInfo?) {
        _state.update { it.copy(selectedSkill = skill) }
    }
    
    fun toggleSkill(skillName: String) {
        _state.update { state ->
            val updatedSkills = state.skills.map { skill ->
                if (skill.name == skillName) {
                    skill.copy(isEnabled = !skill.isEnabled)
                } else {
                    skill
                }
            }
            state.copy(skills = updatedSkills)
        }
    }
    
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    viewModel: SkillsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSkills() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
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
                        text = "Manage MCP servers and skills",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                val connectedSkills = state.skills.filter { it.isEnabled }
                val disconnectedSkills = state.skills.filter { !it.isEnabled }
                
                if (connectedSkills.isNotEmpty()) {
                    item {
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    items(connectedSkills, key = { it.name }) { skill ->
                        SkillCard(
                            skill = skill,
                            onToggle = { viewModel.toggleSkill(skill.name) },
                            onClick = { viewModel.selectSkill(skill) }
                        )
                    }
                }
                
                if (disconnectedSkills.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Disconnected",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    items(disconnectedSkills, key = { it.name }) { skill ->
                        SkillCard(
                            skill = skill,
                            onToggle = { viewModel.toggleSkill(skill.name) },
                            onClick = { viewModel.selectSkill(skill) }
                        )
                    }
                }
                
                if (state.skills.isEmpty()) {
                    item {
                        EmptySkillsView()
                    }
                }
            }
        }
    }
    
    state.selectedSkill?.let { skill ->
        SkillDetailDialog(
            skill = skill,
            onDismiss = { viewModel.selectSkill(null) }
        )
    }
    
    state.error?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = viewModel::clearError) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillCard(
    skill: SkillInfo,
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
                    color = if (skill.isEnabled) 
                        Color(0xFF4CAF50).copy(alpha = 0.2f) 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = if (skill.isEnabled) 
                            Color(0xFF4CAF50) 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
                
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (skill.isEnabled) 
                                Color(0xFF4CAF50).copy(alpha = 0.2f) 
                            else 
                                MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (skill.isEnabled) "Connected" else "Disconnected",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (skill.isEnabled) 
                                    Color(0xFF4CAF50) 
                                else 
                                    MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (skill.tools.isNotEmpty() || skill.resources.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (skill.tools.isNotEmpty()) {
                                Text(
                                    text = "${skill.tools.size} tools",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (skill.resources.isNotEmpty()) {
                                Text(
                                    text = "${skill.resources.size} resources",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
            
            Switch(
                checked = skill.isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun SkillDetailDialog(
    skill: SkillInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = if (skill.isEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        },
        title = { Text(skill.name) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(skill.description)
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = skill.source,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (skill.tools.isNotEmpty()) {
                    Text(
                        text = "Tools",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        skill.tools.forEach { tool ->
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
                
                if (skill.resources.isNotEmpty()) {
                    Text(
                        text = "Resources",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        skill.resources.forEach { resource ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = resource,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
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
private fun EmptySkillsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "No skills configured",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "MCP servers will appear here once configured",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
