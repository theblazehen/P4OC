@file:Suppress("ImportOrdering")

package dev.blazelight.p4oc.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.data.remote.dto.ModelDto
import dev.blazelight.p4oc.data.remote.dto.ProviderAuthMethodDto
import dev.blazelight.p4oc.data.remote.dto.ProviderDto
import dev.blazelight.p4oc.ui.components.TuiAlertDialog
import dev.blazelight.p4oc.ui.components.TuiButton
import dev.blazelight.p4oc.ui.components.TuiCard
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.SemanticColors
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.workspace.WorkspaceRepositoryOwner
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun ProviderConfigScreen(
    workspaceOwner: WorkspaceRepositoryOwner,
    viewModel: ProviderConfigViewModel = koinViewModel(
        parameters = { parametersOf(workspaceOwner) },
    ),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pendingAuthorization = uiState.pendingAuthorization
    var authorizationCode by remember(pendingAuthorization) { mutableStateOf("") }

    pendingAuthorization?.let { pending ->
        TuiAlertDialog(
            onDismissRequest = viewModel::dismissAuthorization,
            title = stringResource(R.string.provider_auth_title),
            confirmButton = {
                TuiButton(
                    onClick = {
                        viewModel.completeOAuth(
                            authorizationCode.takeIf { pending.authorization.method == "code" }
                        )
                    },
                    enabled = !uiState.isAuthenticating &&
                        (pending.authorization.method != "code" || authorizationCode.isNotBlank())
                ) {
                    Text(stringResource(R.string.provider_auth_complete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            val uri = Uri.parse(pending.authorization.url)
                            if (uri.scheme.equals("https", ignoreCase = true) ||
                                uri.scheme.equals("http", ignoreCase = true)
                            ) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.provider_auth_open_browser))
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(pending.authorization.instructions)
                if (pending.authorization.method == "code") {
                    OutlinedTextField(
                        value = authorizationCode,
                        onValueChange = { authorizationCode = it },
                        label = { Text(stringResource(R.string.provider_auth_code)) },
                        singleLine = true,
                    )
                }
            }
        }
    }

    val theme = LocalOpenCodeTheme.current
    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.provider_config_title),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.loadProviders() },
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
        when {
            uiState.isLoading -> {
                TuiLoadingScreen(
                    modifier = Modifier.padding(padding)
                )
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            text = "✗",
                            style = MaterialTheme.typography.displayMedium,
                            color = theme.error
                        )
                        Text(
                            text = uiState.error ?: stringResource(R.string.connection_error_generic),
                            color = theme.error
                        )
                        TuiButton(onClick = { viewModel.loadProviders() }) {
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
                    if (!uiState.canSetDefaultModel) {
                        item {
                            Text(
                                text = stringResource(R.string.v2_model_settings_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textMuted,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )
                        }
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
                            color = theme.text,
                            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
                        )
                    }

                    items(connectedProviders, key = { it.id }) { provider ->
                        ProviderCard(
                            provider = provider,
                            isExpanded = uiState.selectedProviderId == provider.id,
                            currentModel = uiState.currentModel,
                            canSetDefaultModel = uiState.canSetDefaultModel,
                            authMethods = uiState.authMethods[provider.id].orEmpty(),
                            isAuthenticating = uiState.isAuthenticating,
                            onToggle = {
                                viewModel.selectProvider(
                                    if (uiState.selectedProviderId == provider.id) "" else provider.id
                                )
                            },
                            onAuthenticate = { methodIndex ->
                                viewModel.startOAuth(provider.id, methodIndex)
                            },
                            onSelectModel = { modelId -> viewModel.setModel(provider.id, modelId) }
                        )
                    }

                    if (disconnectedProviders.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.provider_disconnected),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textMuted,
                                modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs)
                            )
                        }

                        items(disconnectedProviders, key = { it.id }) { provider ->
                            DisabledProviderCard(
                                provider = provider,
                                authMethods = uiState.authMethods[provider.id].orEmpty(),
                                isAuthenticating = uiState.isAuthenticating,
                                onAuthenticate = { methodIndex ->
                                    viewModel.startOAuth(provider.id, methodIndex)
                                }
                            )
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
    TuiCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = theme.accent.copy(alpha = 0.15f))
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.Medium,
                    color = theme.text
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList", "FunctionNaming")
private fun ProviderCard(
    provider: ProviderDto,
    isExpanded: Boolean,
    currentModel: String?,
    canSetDefaultModel: Boolean,
    authMethods: List<ProviderAuthMethodDto>,
    isAuthenticating: Boolean,
    onToggle: () -> Unit,
    onAuthenticate: (Int) -> Unit,
    onSelectModel: (String) -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    val currentProviderId = currentModel?.substringBefore("/")
    val currentModelId = currentModel?.substringAfter("/")
    val isActiveProvider = currentProviderId == provider.id

    TuiCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveProvider) {
                theme.accent.copy(alpha = 0.1f)
            } else {
                theme.backgroundElement
            }
        )
    ) {
        Column {
            ProviderCardHeader(provider, isActiveProvider, isExpanded, onToggle)

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    HorizontalDivider(
                        thickness = Sizing.dividerThickness,
                        color = theme.borderSubtle
                    )
                    Spacer(Modifier.height(Spacing.xs))

                    authMethods.withIndex().firstOrNull { it.value.type == "oauth" }?.let { oauthMethod ->
                        TuiButton(
                            onClick = { onAuthenticate(oauthMethod.index) },
                            enabled = !isAuthenticating
                        ) {
                            Text(oauthMethod.value.label)
                        }
                    }

                    provider.models.values.sortedBy { it.name }.forEach { model ->
                        ModelItem(
                            model = model,
                            isSelected = currentProviderId == provider.id && currentModelId == model.id,
                            enabled = canSetDefaultModel,
                            onClick = { onSelectModel(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun ProviderCardHeader(
    provider: ProviderDto,
    isActiveProvider: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderIcon(provider.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = theme.text
            )
            Text(
                text = "${provider.models.size} model${if (provider.models.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMuted
            )
        }
        if (isActiveProvider) {
            Text(text = "✓", style = MaterialTheme.typography.titleMedium, color = theme.accent)
        }
        Text(
            text = if (isExpanded) "▴" else "▾",
            style = MaterialTheme.typography.titleMedium,
            color = theme.textMuted
        )
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun ModelItem(
    model: ModelDto,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) {
                    theme.accent.copy(alpha = 0.1f)
                } else {
                    Color.Transparent
                },
                shape = RectangleShape
            )
            .selectable(
                enabled = enabled,
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TUI-style selection indicator instead of RadioButton
        Text(
            text = if (isSelected) "◉" else "○",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = if (isSelected) theme.accent else theme.textMuted
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = theme.text
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                model.capabilities?.let { caps ->
                    if (caps.reasoning) {
                        Text(
                            text = "[R]",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = theme.info,
                        )
                    }
                    if (caps.toolcall) {
                        Text(
                            text = "[T]",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = theme.accent,
                        )
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
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = theme.textMuted
                    )
                    Text(
                        text = "$${String.format(java.util.Locale.US, "%.2f", cost.output)}/M out",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
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
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = theme.textMuted,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun DisabledProviderCard(
    provider: ProviderDto,
    authMethods: List<ProviderAuthMethodDto>,
    isAuthenticating: Boolean,
    onAuthenticate: (Int) -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    TuiCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = theme.backgroundElement.copy(alpha = 0.5f))
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

            val oauthMethod = authMethods.withIndex().firstOrNull { it.value.type == "oauth" }
            if (oauthMethod != null) {
                TuiButton(
                    onClick = { onAuthenticate(oauthMethod.index) },
                    enabled = !isAuthenticating
                ) {
                    Text(oauthMethod.value.label)
                }
            } else {
                Text(
                    text = "⊘",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textMuted
                )
            }
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
            .background(bgColor.copy(alpha = alpha), RectangleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = iconChar,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            fontWeight = FontWeight.Bold,
            color = theme.text.copy(alpha = alpha)
        )
    }
}
