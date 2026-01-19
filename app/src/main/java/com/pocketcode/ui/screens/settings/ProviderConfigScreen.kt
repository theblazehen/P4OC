package com.pocketcode.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketcode.data.remote.dto.ModelDto
import com.pocketcode.data.remote.dto.ProviderDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    viewModel: ProviderConfigViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Configuration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadProviders() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
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
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = uiState.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.loadProviders() }) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CurrentModelCard(
                            currentModel = uiState.currentModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    val connectedProviders = uiState.providers.filter { 
                        it.id in uiState.connectedProviderIds 
                    }
                    val disconnectedProviders = uiState.providers.filter { 
                        it.id !in uiState.connectedProviderIds 
                    }

                    item {
                        Text(
                            text = "Available Providers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(connectedProviders) { provider ->
                        ProviderCard(
                            provider = provider,
                            isExpanded = uiState.selectedProviderId == provider.id,
                            currentModel = uiState.currentModel,
                            onToggle = { viewModel.selectProvider(if (uiState.selectedProviderId == provider.id) "" else provider.id) },
                            onSelectModel = { modelId -> viewModel.setModel(provider.id, modelId) }
                        )
                    }

                    if (disconnectedProviders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Disconnected Providers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }

                        items(disconnectedProviders) { provider ->
                            DisabledProviderCard(provider = provider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentModelCard(
    currentModel: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Model",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = currentModel ?: "Not configured",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderDto,
    isExpanded: Boolean,
    currentModel: String?,
    onToggle: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    val currentProviderId = currentModel?.substringBefore("/")
    val currentModelId = currentModel?.substringAfter("/")
    val isActiveProvider = currentProviderId == provider.id

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveProvider) 
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderIcon(provider.name)
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${provider.models.size} model${if (provider.models.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isActiveProvider) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()
                    
                    provider.models.values.sortedBy { it.name }.forEach { model ->
                        ModelItem(
                            model = model,
                            isSelected = currentProviderId == provider.id && currentModelId == model.id,
                            onClick = { onSelectModel(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: ModelDto,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                model.capabilities?.let { caps ->
                    if (caps.reasoning) {
                        CapabilityChip("Reasoning")
                    }
                    if (caps.toolcall) {
                        CapabilityChip("Tools")
                    }
                }
                
                model.limit?.let { limit ->
                    if (limit.context > 0) {
                        CapabilityChip("${limit.context / 1000}k ctx")
                    }
                }
            }
        }
        
        model.cost?.let { cost ->
            if (cost.input > 0 || cost.output > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$${String.format("%.2f", cost.input)}/M in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${String.format("%.2f", cost.output)}/M out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DisabledProviderCard(provider: ProviderDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderIcon(provider.name, alpha = 0.5f)
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Not configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Icon(
                Icons.Default.Lock,
                contentDescription = "Requires API key",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ProviderIcon(providerName: String, alpha: Float = 1f) {
    val (bgColor, iconChar) = when (providerName.lowercase()) {
        "anthropic" -> Color(0xFFD97706) to "A"
        "openai" -> Color(0xFF10A37F) to "O"
        "google" -> Color(0xFF4285F4) to "G"
        "aws", "amazon" -> Color(0xFFFF9900) to "A"
        "azure" -> Color(0xFF0078D4) to "Az"
        "mistral" -> Color(0xFFFF6B35) to "M"
        "groq" -> Color(0xFFF55036) to "Gr"
        "ollama" -> Color(0xFF6366F1) to "Ol"
        "xai" -> Color(0xFF000000) to "X"
        "deepseek" -> Color(0xFF0066FF) to "D"
        else -> MaterialTheme.colorScheme.primary to providerName.take(2).uppercase()
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = iconChar,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = alpha)
        )
    }
}
