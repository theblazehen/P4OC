package dev.blazelight.p4oc.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.blazelight.p4oc.core.datastore.SettingsDataStore
import dev.blazelight.p4oc.core.datastore.VisualSettings

@HiltViewModel
class VisualSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    
    private val _settings = MutableStateFlow(VisualSettings())
    val settings: StateFlow<VisualSettings> = _settings.asStateFlow()
    
    private val _themeName = MutableStateFlow(SettingsDataStore.DEFAULT_THEME_NAME)
    val themeName: StateFlow<String> = _themeName.asStateFlow()
    
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
    }
    
    fun updateThemeName(name: String) {
        _themeName.value = name
        viewModelScope.launch {
            settingsDataStore.setThemeName(name)
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
    
    fun toggleToolCallsExpanded() {
        persistSettings(_settings.value.copy(toolCallsExpandedByDefault = !_settings.value.toolCallsExpandedByDefault))
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
    val settings by viewModel.settings.collectAsState()
    val themeName by viewModel.themeName.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visual Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::resetToDefaults) {
                        Text("Reset")
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
                    icon = Icons.Default.WrapText
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
                
                SettingsSwitch(
                    title = "Expand Tool Calls by Default",
                    subtitle = "Show tool call details expanded",
                    checked = settings.toolCallsExpandedByDefault,
                    onCheckedChange = { viewModel.toggleToolCallsExpanded() },
                    icon = Icons.Default.Build
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            PreviewCard(settings = settings)
            
            Spacer(Modifier.height(32.dp))
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
        Spacer(Modifier.height(16.dp))
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
            label = { Text("Color Theme") },
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
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
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "This is a sample message with your current settings.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = settings.fontSize.sp
                )
            }
            
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "fun example(): String {\n    return \"Hello\"\n}",
                    modifier = Modifier.padding(12.dp),
                    fontSize = settings.codeBlockFontSize.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
