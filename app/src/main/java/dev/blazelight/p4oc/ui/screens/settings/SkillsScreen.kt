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


class SkillsViewModel constructor(
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
    viewModel: SkillsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error in snackbar
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skills_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSkills() }) {
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
                        text = stringResource(R.string.skills_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textMuted,
                        modifier = Modifier.padding(bottom = Spacing.md)
                    )
                }
                
                val connectedSkills = state.skills.filter { it.isEnabled }
                val disconnectedSkills = state.skills.filter { !it.isEnabled }
                
                if (connectedSkills.isNotEmpty()) {
                    item {
                        val theme = LocalOpenCodeTheme.current
                        Text(
                            text = stringResource(R.string.skills_connected),
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.accent
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
                        val theme = LocalOpenCodeTheme.current
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = stringResource(R.string.skills_disconnected),
                            style = MaterialTheme.typography.titleSmall,
                            color = theme.error
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillCard(
    skill: SkillInfo,
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
                    color = if (skill.isEnabled) 
                        theme.success.copy(alpha = 0.2f)
                    else 
                        theme.error.copy(alpha = 0.2f)
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = stringResource(R.string.cd_skill_icon),
                        modifier = Modifier.padding(Spacing.md),
                        tint = if (skill.isEnabled) 
                            theme.success
                        else 
                            theme.error
                    )
                }
                
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (skill.isEnabled) 
                                theme.success.copy(alpha = 0.2f)
                            else 
                                theme.error.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (skill.isEnabled) stringResource(R.string.skills_connected) else stringResource(R.string.skills_disconnected),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (skill.isEnabled) 
                                    theme.success
                                else 
                                    theme.error,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                            )
                        }
                    }
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMuted
                    )
                    
                    if (skill.tools.isNotEmpty() || skill.resources.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            if (skill.tools.isNotEmpty()) {
                                Text(
                                    text = "${skill.tools.size} tools",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.accent
                                )
                            }
                            if (skill.resources.isNotEmpty()) {
                                Text(
                                    text = "${skill.resources.size} resources",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.secondary
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
    val theme = LocalOpenCodeTheme.current
    TuiAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Default.Extension,
        title = skill.name,
        confirmButton = {
            TuiTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        Text(skill.description)
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.skills_source),
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
                text = stringResource(R.string.skills_tools),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                skill.tools.forEach { tool ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = stringResource(R.string.skills_tools),
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
        
        if (skill.resources.isNotEmpty()) {
            Text(
                text = stringResource(R.string.skills_resources),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                skill.resources.forEach { resource ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = stringResource(R.string.skills_resources),
                            modifier = Modifier.size(Sizing.iconXs),
                            tint = theme.textMuted
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
}

@Composable
private fun EmptySkillsView() {
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
            text = stringResource(R.string.skills_no_skills),
            style = MaterialTheme.typography.titleMedium,
            color = theme.text
        )
        Text(
            text = stringResource(R.string.skills_appear_here),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted
        )
    }
}
