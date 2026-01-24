package dev.blazelight.p4oc.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
                .padding(32.dp),
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            Icons.Default.Code,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Welcome to Pocket Code",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your AI coding assistant, now on Android",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}

@Composable
private fun TermuxInfoStep(onNext: () -> Unit, onSkip: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Terminal,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )

        Text(
            text = "Run OpenCode Locally",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Pocket Code can run OpenCode directly on your device using Termux. This lets you use AI coding assistance without a separate server.",
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
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Requirements:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text("• Termux app from F-Droid", style = MaterialTheme.typography.bodySmall)
                Text("• Node.js (installed via Termux)", style = MaterialTheme.typography.bodySmall)
                Text("• OpenCode CLI", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }

        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}
