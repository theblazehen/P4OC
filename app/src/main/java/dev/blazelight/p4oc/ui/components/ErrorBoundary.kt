package dev.blazelight.p4oc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import dev.blazelight.p4oc.ui.theme.Spacing
import dev.blazelight.p4oc.ui.theme.Sizing

data class ErrorState(
    val hasError: Boolean = false,
    val error: Throwable? = null,
    val errorMessage: String? = null,
    val errorType: ErrorType = ErrorType.UNKNOWN
)

enum class ErrorType {
    NETWORK,
    API,
    PARSING,
    PERMISSION,
    NOT_FOUND,
    TIMEOUT,
    UNKNOWN
}

@Composable
fun ErrorBoundary(
    errorState: ErrorState,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (errorState.hasError) {
        ErrorFallback(
            errorState = errorState,
            onRetry = onRetry,
            onDismiss = onDismiss
        )
    } else {
        content()
    }
}

@Composable
fun ErrorFallback(
    errorState: ErrorState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = "✗",
            style = MaterialTheme.typography.displayMedium,
            color = theme.error,
            fontFamily = FontFamily.Monospace
        )
        
        Text(
            text = getErrorTitle(errorState.errorType),
            style = MaterialTheme.typography.titleLarge,
            color = theme.error,
            fontFamily = FontFamily.Monospace
        )
        
        Text(
            text = errorState.errorMessage ?: getErrorDescription(errorState.errorType),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textMuted,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            onDismiss?.let {
                OutlinedButton(
                    onClick = it,
                    shape = RectangleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted)
                ) {
                    Text(stringResource(R.string.dismiss), fontFamily = FontFamily.Monospace)
                }
            }
            onRetry?.let {
                Button(
                    onClick = it,
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        contentColor = theme.background
                    )
                ) {
                    Text("↻ ${stringResource(R.string.retry)}", fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    errorType: ErrorType = ErrorType.UNKNOWN,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, theme.error.copy(alpha = 0.5f), RectangleShape),
        color = theme.error.copy(alpha = 0.1f),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✗",
                color = theme.error,
                fontFamily = FontFamily.Monospace
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getErrorTitle(errorType),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.error,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.error.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                onRetry?.let {
                    IconButton(onClick = it) {
                        Text(
                            text = "↻",
                            color = theme.error,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                onDismiss?.let {
                    IconButton(onClick = it) {
                        Text(
                            text = "×",
                            color = theme.error,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    errorType: ErrorType = ErrorType.UNKNOWN,
    onRetry: (() -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    
    Snackbar(
        modifier = modifier,
        action = {
            onRetry?.let {
                TextButton(onClick = it) {
                    Text(
                        stringResource(R.string.retry),
                        color = theme.error,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        dismissAction = {
            IconButton(onClick = onDismiss) {
                Text(
                    text = "×",
                    color = theme.error,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        containerColor = theme.error.copy(alpha = 0.15f),
        contentColor = theme.error,
        shape = RectangleShape
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✗",
                fontFamily = FontFamily.Monospace
            )
            Text(message, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val theme = LocalOpenCodeTheme.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "!",
            color = theme.error,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = theme.error,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        onRetry?.let {
            TextButton(
                onClick = it,
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Text(
                    "[${stringResource(R.string.retry)}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.accent,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun NetworkErrorBanner(
    isVisible: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalOpenCodeTheme.current
    
    if (isVisible) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = theme.error.copy(alpha = 0.15f),
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠",
                        color = theme.error,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.no_network_connection),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.error,
                        fontFamily = FontFamily.Monospace
                    )
                }
                TextButton(onClick = onRetry) {
                    Text(
                        "[${stringResource(R.string.retry)}]",
                        color = theme.accent,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun getErrorIcon(errorType: ErrorType) = when (errorType) {
    ErrorType.NETWORK -> Icons.Default.WifiOff
    ErrorType.API -> Icons.Default.Cloud
    ErrorType.PARSING -> Icons.Default.DataObject
    ErrorType.PERMISSION -> Icons.Default.Lock
    ErrorType.NOT_FOUND -> Icons.Default.SearchOff
    ErrorType.TIMEOUT -> Icons.Default.Timer
    ErrorType.UNKNOWN -> Icons.Default.ErrorOutline
}

private fun getErrorTitle(errorType: ErrorType) = when (errorType) {
    ErrorType.NETWORK -> "Network Error"
    ErrorType.API -> "Server Error"
    ErrorType.PARSING -> "Data Error"
    ErrorType.PERMISSION -> "Permission Denied"
    ErrorType.NOT_FOUND -> "Not Found"
    ErrorType.TIMEOUT -> "Request Timeout"
    ErrorType.UNKNOWN -> "Something went wrong"
}

private fun getErrorDescription(errorType: ErrorType) = when (errorType) {
    ErrorType.NETWORK -> "Please check your internet connection and try again."
    ErrorType.API -> "The server encountered an error. Please try again later."
    ErrorType.PARSING -> "Failed to process the response. Please try again."
    ErrorType.PERMISSION -> "You don't have permission to perform this action."
    ErrorType.NOT_FOUND -> "The requested resource could not be found."
    ErrorType.TIMEOUT -> "The request took too long. Please try again."
    ErrorType.UNKNOWN -> "An unexpected error occurred. Please try again."
}

fun Throwable.toErrorType(): ErrorType = when {
    this is java.net.UnknownHostException -> ErrorType.NETWORK
    this is java.net.SocketTimeoutException -> ErrorType.TIMEOUT
    this is java.net.ConnectException -> ErrorType.NETWORK
    this.message?.contains("401") == true -> ErrorType.PERMISSION
    this.message?.contains("403") == true -> ErrorType.PERMISSION
    this.message?.contains("404") == true -> ErrorType.NOT_FOUND
    this.message?.contains("5") == true -> ErrorType.API
    else -> ErrorType.UNKNOWN
}

fun Throwable.toErrorState(): ErrorState = ErrorState(
    hasError = true,
    error = this,
    errorMessage = this.message,
    errorType = this.toErrorType()
)
