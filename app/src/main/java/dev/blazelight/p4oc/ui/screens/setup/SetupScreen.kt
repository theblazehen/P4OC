package dev.blazelight.p4oc.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.Spacing

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (currentStep) {
                0 -> WelcomeStep(onNext = { currentStep = 1 })
                1 -> TermuxInfoStep(onNext = { currentStep = 2 }, onSkip = onSetupComplete)
                2 -> {
                    LaunchedEffect(Unit) {
                        onSetupComplete()
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Code,
            contentDescription = stringResource(R.string.cd_decorative),
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.setup_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.setup_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setup_get_started))
        }
    }
}

@Composable
private fun TermuxInfoStep(onNext: () -> Unit, onSkip: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Terminal,
            contentDescription = stringResource(R.string.cd_terminal),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = stringResource(R.string.setup_run_locally_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.setup_run_locally_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_requirements),
                    style = MaterialTheme.typography.titleSmall
                )
                Text("• ${stringResource(R.string.setup_requirement_termux)}", style = MaterialTheme.typography.bodySmall)
                Text("• ${stringResource(R.string.setup_requirement_nodejs)}", style = MaterialTheme.typography.bodySmall)
                Text("• ${stringResource(R.string.setup_requirement_opencode)}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.setup_continue))
        }

        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.setup_skip))
        }
    }
}
