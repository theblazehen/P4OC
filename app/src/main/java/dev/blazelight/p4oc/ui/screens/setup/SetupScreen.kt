package dev.blazelight.p4oc.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.blazelight.p4oc.R
import dev.blazelight.p4oc.ui.theme.LocalOpenCodeTheme
import dev.blazelight.p4oc.ui.theme.Sizing
import dev.blazelight.p4oc.ui.theme.Spacing

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val theme = LocalOpenCodeTheme.current
    
    Scaffold(
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            WelcomeStep(onNext = onSetupComplete)
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val theme = LocalOpenCodeTheme.current
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "◇",
            style = MaterialTheme.typography.displayLarge,
            color = theme.accent,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = stringResource(R.string.setup_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            color = theme.text,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.setup_welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = theme.textMuted
        )

        Spacer(Modifier.height(Spacing.lg))
        
        Text(
            text = stringResource(R.string.server_remote_description),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = theme.textMuted
        )

        Spacer(Modifier.height(Spacing.lg))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = theme.accent,
                contentColor = theme.background
            )
        ) {
            Text(
                "→ ${stringResource(R.string.setup_get_started)}",
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
