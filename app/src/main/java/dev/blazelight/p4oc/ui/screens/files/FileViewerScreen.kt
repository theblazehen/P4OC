package dev.blazelight.p4oc.ui.screens.files

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatListNumberedRtl
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.components.code.Language
import dev.blazelight.p4oc.ui.components.code.SyntaxHighlightedCode
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    path: String,
    viewModel: FilesViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLineNumbers by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        viewModel.loadFileContent(path)
    }

    val filename = path.substringAfterLast("/")
    val language = remember(filename) { Language.fromFilename(filename) }
    val theme = LocalOpenCodeTheme.current

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = "",
                onNavigateBack = onNavigateBack,
                titleContent = {
                    Column {
                        Text(
                            filename,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (language != Language.UNKNOWN) language.name.lowercase() else "plain text",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMuted
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showLineNumbers = !showLineNumbers },
                        modifier = Modifier.size(Sizing.iconButtonMd)
                    ) {
                        Icon(
                            imageVector = if (showLineNumbers) 
                                Icons.Default.FormatListNumbered 
                            else 
                                Icons.Default.FormatListNumberedRtl,
                            contentDescription = stringResource(R.string.cd_toggle_line_numbers),
                            modifier = Modifier.size(Sizing.iconAction)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val fileContent = uiState.fileContent
        val error = uiState.error
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    TuiLoadingScreen(modifier = Modifier.align(Alignment.Center))
                }
                fileContent != null -> {
                    SyntaxHighlightedCode(
                        code = fileContent,
                        filename = filename,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.md),
                        showLineNumbers = showLineNumbers
                    )
                }
                error != null -> {
                    Text(
                        text = error,
                        modifier = Modifier.align(Alignment.Center),
                        color = theme.error
                    )
                }
            }
        }
    }
}
