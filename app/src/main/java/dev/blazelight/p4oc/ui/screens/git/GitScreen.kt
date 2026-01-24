package dev.blazelight.p4oc.ui.screens.git

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
import dev.blazelight.p4oc.domain.model.FileStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    projectId: String? = null,
    viewModel: GitViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onViewDiff: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(projectId) {
        viewModel.loadGitInfoForProject(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.projectName?.let { "$it - Git" } ?: "Git") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadGitInfoForProject(projectId) }) {
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
            !uiState.hasVcs -> {
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
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Not a Git Repository",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "This project is not using Git version control",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        BranchCard(branch = uiState.branch ?: "unknown")
                    }

                    item {
                        ChangedFilesHeader(count = uiState.changedFiles.size)
                    }

                    if (uiState.changedFiles.isEmpty()) {
                        item {
                            EmptyChangesCard()
                        }
                    } else {
                        items(uiState.changedFiles) { file ->
                            FileStatusItem(
                                file = file,
                                onClick = { onViewDiff?.invoke(file.path) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchCard(branch: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Icons.Default.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Branch",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = branch,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ChangedFilesHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Changed Files",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (count > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyChangesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = "Working tree clean",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "No uncommitted changes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileStatusItem(
    file: FileStatus,
    onClick: () -> Unit
) {
    val (statusColor, statusLabel) = getStatusInfo(file.status)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (file.added > 0 || file.removed > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (file.added > 0) {
                        Text(
                            text = "+${file.added}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    if (file.removed > 0) {
                        Text(
                            text = "-${file.removed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF44336)
                        )
                    }
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View diff",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getStatusInfo(status: String): Pair<Color, String> {
    return when (status.uppercase().firstOrNull()) {
        'M' -> Color(0xFFFF9800) to "M"
        'A' -> Color(0xFF4CAF50) to "A"
        'D' -> Color(0xFFF44336) to "D"
        'R' -> Color(0xFF2196F3) to "R"
        'C' -> Color(0xFF9C27B0) to "C"
        '?' -> Color(0xFF607D8B) to "?"
        'U' -> Color(0xFFFF5722) to "U"
        else -> Color(0xFF9E9E9E) to status.take(1).uppercase()
    }
}
