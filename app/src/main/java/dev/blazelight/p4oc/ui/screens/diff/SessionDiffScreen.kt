package dev.blazelight.p4oc.ui.screens.diff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.core.network.safeApiCall
import dev.blazelight.p4oc.data.remote.dto.FileDiffDto
import dev.blazelight.p4oc.data.workspace.WorkspaceClient
import dev.blazelight.p4oc.ui.components.TuiEmptyState
import dev.blazelight.p4oc.ui.components.TuiLoadingScreen
import dev.blazelight.p4oc.ui.components.TuiTopBar
import dev.blazelight.p4oc.ui.components.chat.InlineDiffViewer
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDiffScreen(
    sessionId: String,
    workspaceClient: WorkspaceClient,
    onNavigateBack: () -> Unit,
) {
    val theme = LocalOpenCodeTheme.current
    var diffs by remember { mutableStateOf<List<FileDiffDto>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId) {
        isLoading = true
        errorMessage = null

        when (val result = safeApiCall {
            workspaceClient.getSessionDiff(sessionId)
        }) {
            is ApiResult.Success -> diffs = result.data
            is ApiResult.Error -> errorMessage = result.message
        }
        isLoading = false
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TuiTopBar(
                title = stringResource(R.string.session_diff_title),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                TuiLoadingScreen(
                    modifier = Modifier.padding(padding),
                    text = stringResource(R.string.session_diff_loading)
                )
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    TuiEmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = errorMessage.orEmpty()
                    )
                }
            }

            diffs.isNullOrEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    TuiEmptyState(
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.session_diff_no_changes)
                    )
                }
            }

            else -> {
                val fileList = diffs.orEmpty()
                val totalAdditions = fileList.sumOf { it.additions }
                val totalDeletions = fileList.sumOf { it.deletions }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    item(key = "summary") {
                        Surface(color = theme.backgroundElement) {
                            Text(
                                text = stringResource(
                                    R.string.session_diff_summary,
                                    fileList.size,
                                    totalAdditions,
                                    totalDeletions
                                ),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = theme.accent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md)
                            )
                        }
                    }

                    items(fileList, key = { it.file }) { fileDiff ->
                        val diffContent = remember(fileDiff.file, fileDiff.before, fileDiff.after) {
                            toUnifiedDiff(
                                filePath = fileDiff.file,
                                before = fileDiff.before,
                                after = fileDiff.after
                            )
                        }
                        InlineDiffViewer(
                            fileName = fileDiff.file,
                            diffContent = diffContent,
                            additions = fileDiff.additions,
                            deletions = fileDiff.deletions
                        )
                    }
                }
            }
        }
    }
}

private fun toUnifiedDiff(filePath: String, before: String, after: String): String {
    val beforeLines = before.lines()
    val afterLines = after.lines()
    val beforeCount = beforeLines.size
    val afterCount = afterLines.size

    return buildString {
        append("--- a/")
        append(filePath)
        append('\n')
        append("+++ b/")
        append(filePath)
        append('\n')
        append("@@ -1,")
        append(beforeCount)
        append(" +1,")
        append(afterCount)
        append(" @@\n")

        beforeLines.forEach {
            append('-')
            append(it)
            append('\n')
        }
        afterLines.forEach {
            append('+')
            append(it)
            append('\n')
        }
    }
}
