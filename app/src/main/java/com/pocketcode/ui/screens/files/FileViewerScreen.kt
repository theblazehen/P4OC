package com.pocketcode.ui.screens.files

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatListNumberedRtl
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pocketcode.ui.components.code.Language
import com.pocketcode.ui.components.code.SyntaxHighlightedCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    path: String,
    viewModel: FilesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLineNumbers by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        viewModel.loadFileContent(path)
    }

    val filename = path.substringAfterLast("/")
    val language = remember(filename) { Language.fromFilename(filename) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(filename)
                        Text(
                            text = if (language != Language.UNKNOWN) language.name.lowercase() else "plain text",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLineNumbers = !showLineNumbers }) {
                        Icon(
                            imageVector = if (showLineNumbers) 
                                Icons.Default.FormatListNumbered 
                            else 
                                Icons.Default.FormatListNumberedRtl,
                            contentDescription = "Toggle line numbers"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.fileContent != null -> {
                    SyntaxHighlightedCode(
                        code = uiState.fileContent!!,
                        filename = filename,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        showLineNumbers = showLineNumbers
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
