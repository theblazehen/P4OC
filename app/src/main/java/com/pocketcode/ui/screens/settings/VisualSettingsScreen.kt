package com.pocketcode.ui.screens.settings

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

data class VisualSettings(
    val fontSize: Int = 14,
    val lineSpacing: Float = 1.5f,
    val fontFamily: String = "System",
    val codeBlockFontSize: Int = 12,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val compactMode: Boolean = false,
    val messageSpacing: Int = 8,
    val highContrastMode: Boolean = false
)

@HiltViewModel
class VisualSettingsViewModel @Inject constructor() : ViewModel() {
    
    private val _settings = MutableStateFlow(VisualSettings())
    val settings: StateFlow<VisualSettings> = _settings.asStateFlow()
    
    fun updateFontSize(size: Int) {
        _settings.update { it.copy(fontSize = size.coerceIn(10, 24)) }
    }
    
    fun updateLineSpacing(spacing: Float) {
        _settings.update { it.copy(lineSpacing = spacing.coerceIn(1f, 2.5f)) }
    }
    
    fun updateFontFamily(family: String) {
        _settings.update { it.copy(fontFamily = family) }
    }
    
    fun updateCodeBlockFontSize(size: Int) {
        _settings.update { it.copy(codeBlockFontSize = size.coerceIn(8, 20)) }
    }
    
    fun toggleLineNumbers() {
        _settings.update { it.copy(showLineNumbers = !it.showLineNumbers) }
    }
    
    fun toggleWordWrap() {
        _settings.update { it.copy(wordWrap = !it.wordWrap) }
    }
    
    fun toggleCompactMode() {
        _settings.update { it.copy(compactMode = !it.compactMode) }
    }
    
    fun updateMessageSpacing(spacing: Int) {
        _settings.update { it.copy(messageSpacing = spacing.coerceIn(0, 24)) }
    }
    
    fun toggleHighContrast() {
        _settings.update { it.copy(highContrastMode = !it.highContrastMode) }
    }
    
    fun resetToDefaults() {
        _settings.value = VisualSettings()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualSettingsScreen(
    viewModel: VisualSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    
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
                
                LineSpacingSlider(
                    value = settings.lineSpacing,
                    onValueChange = viewModel::updateLineSpacing
                )
                
                FontFamilySelector(
                    selected = settings.fontFamily,
                    onSelect = viewModel::updateFontFamily
                )
            }
            
            SettingsSection(title = "Layout") {
                SettingsSwitch(
                    title = "Compact Mode",
                    subtitle = "Reduce padding and margins",
                    checked = settings.compactMode,
                    onCheckedChange = { viewModel.toggleCompactMode() },
                    icon = Icons.Default.ViewCompact
                )
                
                SpacingSlider(
                    label = "Message Spacing",
                    value = settings.messageSpacing,
                    onValueChange = viewModel::updateMessageSpacing,
                    range = 0..24
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

@Composable
private fun LineSpacingSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Line Spacing", style = MaterialTheme.typography.bodyMedium)
            Text(
                String.format("%.1fx", value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..2.5f,
            steps = 5
        )
    }
}

@Composable
private fun SpacingSlider(
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
                "${value}dp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 2 - 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFamilySelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("System", "Monospace", "Serif", "Sans Serif")
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Font Family") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            option, 
                            fontFamily = when (option) {
                                "Monospace" -> FontFamily.Monospace
                                "Serif" -> FontFamily.Serif
                                "Sans Serif" -> FontFamily.SansSerif
                                else -> FontFamily.Default
                            }
                        ) 
                    },
                    onClick = {
                        onSelect(option)
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
            verticalArrangement = Arrangement.spacedBy(settings.messageSpacing.dp)
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
                    fontSize = settings.fontSize.sp,
                    lineHeight = (settings.fontSize * settings.lineSpacing).sp,
                    fontFamily = when (settings.fontFamily) {
                        "Monospace" -> FontFamily.Monospace
                        "Serif" -> FontFamily.Serif
                        "Sans Serif" -> FontFamily.SansSerif
                        else -> FontFamily.Default
                    }
                )
            }
            
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (settings.showLineNumbers) {
                        Row {
                            Text(
                                "1",
                                fontSize = settings.codeBlockFontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "fun example(): String {",
                                fontSize = settings.codeBlockFontSize.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row {
                            Text(
                                "2",
                                fontSize = settings.codeBlockFontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "    return \"Hello\"",
                                fontSize = settings.codeBlockFontSize.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Text(
                            "fun example(): String {\n    return \"Hello\"\n}",
                            fontSize = settings.codeBlockFontSize.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
