package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing

@HiltViewModel
class VisualSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    
    private val _settings = MutableStateFlow(VisualSettings())
    val settings: StateFlow<VisualSettings> = _settings.asStateFlow()
    
    private val _themeName = MutableStateFlow(SettingsDataStore.DEFAULT_THEME_NAME)
    val themeName: StateFlow<String> = _themeName.asStateFlow()
    
    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    
    val availableThemes = listOf(
        "catppuccin" to "Catppuccin Mocha",
        "catppuccin-macchiato" to "Catppuccin Macchiato",
        "catppuccin-frappe" to "Catppuccin Frappé",
        "dracula" to "Dracula",
        "gruvbox" to "Gruvbox",
        "nord" to "Nord",
        "opencode" to "OpenCode",
        "tokyonight" to "Tokyo Night"
    )
    
    init {
        viewModelScope.launch {
            settingsDataStore.visualSettings.collect { saved ->
                _settings.value = saved
            }
        }
        viewModelScope.launch {
            settingsDataStore.themeName.collect { name ->
                _themeName.value = name
            }
        }
        viewModelScope.launch {
            settingsDataStore.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }
    }
    
    fun updateThemeName(name: String) {
        _themeName.value = name
        viewModelScope.launch {
            settingsDataStore.setThemeName(name)
        }
    }
    
    fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }
    
    private fun persistSettings(newSettings: VisualSettings) {
        _settings.value = newSettings
        viewModelScope.launch {
            settingsDataStore.updateVisualSettings(newSettings)
        }
    }
    
    fun updateFontSize(size: Int) {
        persistSettings(_settings.value.copy(fontSize = size.coerceIn(10, 24)))
    }
    
    fun updateLineSpacing(spacing: Float) {
        persistSettings(_settings.value.copy(lineSpacing = spacing.coerceIn(1f, 2.5f)))
    }
    
    fun updateFontFamily(family: String) {
        persistSettings(_settings.value.copy(fontFamily = family))
    }
    
    fun updateCodeBlockFontSize(size: Int) {
        persistSettings(_settings.value.copy(codeBlockFontSize = size.coerceIn(8, 20)))
    }
    
    fun toggleLineNumbers() {
        persistSettings(_settings.value.copy(showLineNumbers = !_settings.value.showLineNumbers))
    }
    
    fun toggleWordWrap() {
        persistSettings(_settings.value.copy(wordWrap = !_settings.value.wordWrap))
    }
    
    fun toggleCompactMode() {
        persistSettings(_settings.value.copy(compactMode = !_settings.value.compactMode))
    }
    
    fun updateMessageSpacing(spacing: Int) {
        persistSettings(_settings.value.copy(messageSpacing = spacing.coerceIn(0, 24)))
    }
    
    fun toggleHighContrast() {
        persistSettings(_settings.value.copy(highContrastMode = !_settings.value.highContrastMode))
    }
    
    fun toggleReasoningExpanded() {
        persistSettings(_settings.value.copy(reasoningExpandedByDefault = !_settings.value.reasoningExpandedByDefault))
    }
    
    fun updateToolWidgetDefaultState(state: String) {
        persistSettings(_settings.value.copy(toolWidgetDefaultState = state))
    }
    
    fun resetToDefaults() {
        persistSettings(VisualSettings())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettingsScreen(
    viewModel: VisualSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val themeName by viewModel.themeName.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visual_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::resetToDefaults) {
                        Text(stringResource(R.string.visual_settings_reset))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Theme") {
                ThemeModeSelector(
                    selected = themeMode,
                    onSelect = viewModel::updateThemeMode
                )
                
                Spacer(Modifier.height(Spacing.md))
                
                ThemeSelector(
                    selected = themeName,
                    options = viewModel.availableThemes,
                    onSelect = viewModel::updateThemeName
                )
            }
            
            SettingsSection(title = "Text") {
                FontSizeSlider(
                    label = "Message Font Size",
                    value = settings.fontSize,
                    onValueChange = viewModel::updateFontSize,
                    range = 10..24
                )
                
                FontSizeSlider(
                    label = "Code Block Font Size",
                    value = settings.codeBlockFontSize,
                    onValueChange = viewModel::updateCodeBlockFontSize,
                    range = 8..20
                )
            }
            
            SettingsSection(title = "Code Display") {
                SettingsSwitch(
                    title = "Show Line Numbers",
                    subtitle = "Display line numbers in code blocks",
                    checked = settings.showLineNumbers,
                    onCheckedChange = { viewModel.toggleLineNumbers() },
                    icon = Icons.Default.FormatListNumbered
                )
                
                SettingsSwitch(
                    title = "Word Wrap",
                    subtitle = "Wrap long lines in code blocks",
                    checked = settings.wordWrap,
                    onCheckedChange = { viewModel.toggleWordWrap() },
                    icon = Icons.AutoMirrored.Filled.WrapText
                )
            }
            
            SettingsSection(title = "Accessibility") {
                SettingsSwitch(
                    title = "High Contrast Mode",
                    subtitle = "Increase contrast for better visibility",
                    checked = settings.highContrastMode,
                    onCheckedChange = { viewModel.toggleHighContrast() },
                    icon = Icons.Default.Contrast
                )
            }
            
            SettingsSection(title = "Message Display") {
                SettingsSwitch(
                    title = "Expand Reasoning by Default",
                    subtitle = "Show reasoning/thinking content expanded",
                    checked = settings.reasoningExpandedByDefault,
                    onCheckedChange = { viewModel.toggleReasoningExpanded() },
                    icon = Icons.Default.Psychology
                )
            }
            
            SettingsSection(title = "Tool Call Display") {
                ToolWidgetStateSelector(
                    selected = settings.toolWidgetDefaultState,
                    onSelect = viewModel::updateToolWidgetDefaultState
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Live preview of all three states
                ToolWidgetPreviewSection(selectedState = settings.toolWidgetDefaultState)
            }
            
            Spacer(Modifier.height(Spacing.md))
            
            PreviewCard(settings = settings)
            
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun FontSizeSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${value}sp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    val modes = listOf(
        "system" to "System",
        "light" to "Light",
        "dark" to "Dark"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modes.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selected }?.second ?: selected
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.visual_settings_color_theme)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Icon(
            icon,
            contentDescription = stringResource(R.string.cd_decorative),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PreviewCard(settings: VisualSettings) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.visual_settings_preview),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = stringResource(R.string.visual_settings_sample_message),
                    modifier = Modifier.padding(Spacing.lg),
                    fontSize = settings.fontSize.sp
                )
            }
            
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "fun example(): String {\n    return \"Hello\"\n}",
                    modifier = Modifier.padding(Spacing.lg),
                    fontSize = settings.codeBlockFontSize.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolWidgetStateSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    val states = listOf(
        "oneline" to "Oneline (minimal)",
        "compact" to "Compact (summary)",
        "expanded" to "Expanded (full details)"
    )
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.visual_settings_tool_mode_label),
            style = MaterialTheme.typography.bodyMedium
        )
        states.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToolWidgetPreviewSection(selectedState: String) {
    val theme = LocalOpenCodeTheme.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.visual_settings_preview),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textMuted
        )
        
        // Show preview based on selected state
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.backgroundElement.copy(alpha = 0.3f),
            shape = RectangleShape
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (selectedState) {
                    "oneline" -> {
                        // Oneline: Just HUD summary
                        Text(
                            text = "✓ Read ×2 | ✓ Edit | ⟳ Bash",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            color = theme.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.backgroundPanel.copy(alpha = 0.5f))
                                .padding(6.dp)
                        )
                        Text(
                            text = stringResource(R.string.visual_settings_tap_to_expand),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted
                        )
                    }
                    "compact" -> {
                        // Compact: HUD + rows
                        Text(
                            text = "✓ Read ×2 | ✓ Edit | ⟳ Bash",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            color = theme.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.backgroundPanel.copy(alpha = 0.5f))
                                .padding(6.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            CompactRowPreview("✓", "Read Theme.kt", theme.success, theme)
                            CompactRowPreview("✓", "Read Colors.kt", theme.success, theme)
                            CompactRowPreview("✓", "Edit Theme.kt +12 -3", theme.success, theme)
                            CompactRowPreview("⟳", "./gradlew build", theme.warning, theme)
                        }
                        Text(
                            text = stringResource(R.string.visual_settings_shows_paths),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted
                        )
                    }
                    "expanded" -> {
                        // Expanded: HUD + detailed widgets
                        Text(
                            text = "✓ Read ×2 | ✓ Edit | ⟳ Bash",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            color = theme.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.backgroundPanel.copy(alpha = 0.5f))
                                .padding(6.dp)
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ExpandedWidgetPreview(
                                title = "Read Theme.kt",
                                preview = "data class OpenCodeTheme(...)",
                                icon = "✓",
                                color = theme.success,
                                theme = theme
                            )
                            ExpandedWidgetPreview(
                                title = "Edit Theme.kt",
                                preview = "- val primary = Color(...)\n+ val primary = theme.primary",
                                icon = "✓",
                                color = theme.success,
                                theme = theme
                            )
                            ExpandedWidgetPreview(
                                title = "./gradlew build",
                                preview = "BUILD SUCCESSFUL in 8s",
                                icon = "✓",
                                color = theme.success,
                                theme = theme
                            )
                        }
                        Text(
                            text = stringResource(R.string.visual_settings_shows_full_output),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactRowPreview(
    icon: String,
    description: String,
    iconColor: androidx.compose.ui.graphics.Color,
    theme: dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.backgroundPanel.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = iconColor
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = theme.text,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExpandedWidgetPreview(
    title: String,
    preview: String,
    icon: String,
    color: androidx.compose.ui.graphics.Color,
    theme: dev.blazelight.p4oc.ui.theme.opencode.OpenCodeTheme
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.backgroundPanel.copy(alpha = 0.5f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = theme.text
            )
        }
        Text(
            text = preview,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 12.sp
            ),
            color = theme.textMuted,
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.backgroundElement)
                .padding(4.dp),
            maxLines = 2
        )
    }
}
