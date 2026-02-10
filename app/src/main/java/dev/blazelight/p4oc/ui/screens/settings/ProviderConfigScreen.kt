package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ProviderDto
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    viewModel: ProviderConfigViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val theme = LocalOpenCodeTheme.current
    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadProviders() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.backgroundElement)
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                TuiLoadingScreen(
                    modifier = Modifier.padding(padding)
                )
            }
            uiState.error != null -> {
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
                            text = uiState.error ?: "Unknown error",
                            color = theme.error
                        )
                        Button(onClick = { viewModel.loadProviders() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                            text = stringResource(R.string.provider_available),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
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
                            val theme = LocalOpenCodeTheme.current
                            Text(
                                text = stringResource(R.string.provider_disconnected),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textMuted,
                                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs)
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
    val theme = LocalOpenCodeTheme.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = theme.accent.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◎",
                style = MaterialTheme.typography.titleLarge,
                color = theme.accent
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.provider_current_model),
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textMuted
                )
                Text(
                    text = currentModel ?: stringResource(R.string.provider_not_configured),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = theme.text
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
    val theme = LocalOpenCodeTheme.current
    val currentProviderId = currentModel?.substringBefore("/")
    val currentModelId = currentModel?.substringAfter("/")
    val isActiveProvider = currentProviderId == provider.id

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveProvider) 
                theme.accent.copy(alpha = 0.1f)
            else theme.backgroundElement
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
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
                        color = theme.textMuted
                    )
                }

                if (isActiveProvider) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.accent
                    )
                }

                Text(
                    text = if (isExpanded) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textMuted
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
                        .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(
                if (isSelected) theme.accent.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
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
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
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
                        text = "$${String.format(java.util.Locale.US, "%.2f", cost.input)}/M in",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                    Text(
                        text = "$${String.format(java.util.Locale.US, "%.2f", cost.output)}/M out",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityChip(text: String) {
    val theme = LocalOpenCodeTheme.current
    Surface(
        color = theme.backgroundPanel,
        shape = RectangleShape
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
        )
    }
}

@Composable
private fun DisabledProviderCard(provider: ProviderDto) {
    val theme = LocalOpenCodeTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = theme.backgroundElement.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderIcon(provider.name, alpha = 0.5f)
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textMuted
                )
                Text(
                    text = stringResource(R.string.provider_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMuted.copy(alpha = 0.7f)
                )
            }

            Text(
                text = "⊘",
                style = MaterialTheme.typography.titleMedium,
                color = theme.textMuted
            )
        }
    }
}

@Composable
private fun ProviderIcon(providerName: String, alpha: Float = 1f) {
    val (bgColor, iconChar) = SemanticColors.Provider.forName(providerName)
    val theme = LocalOpenCodeTheme.current

    Box(
        modifier = Modifier
            .size(Sizing.iconButtonMd)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = iconChar,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = theme.text.copy(alpha = alpha)
        )
    }
}
