package com.pocketcode.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = getErrorIcon(errorState.errorType),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = getErrorTitle(errorState.errorType),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = errorState.errorMessage ?: getErrorDescription(errorState.errorType),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            onDismiss?.let {
                OutlinedButton(onClick = it) {
                    Text("Dismiss")
                }
            }
            onRetry?.let {
                Button(onClick = it) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    errorType: ErrorType = ErrorType.UNKNOWN,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
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
                imageVector = getErrorIcon(errorType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getErrorTitle(errorType),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onRetry?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                onDismiss?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
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
    errorType: ErrorType = ErrorType.UNKNOWN,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier,
        action = {
            onRetry?.let {
                TextButton(onClick = it) {
                    Text("Retry")
                }
            }
        },
        dismissAction = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                getErrorIcon(errorType),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(message)
        }
    }
}

@Composable
fun InlineError(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        onRetry?.let {
            TextButton(
                onClick = it,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Retry", style = MaterialTheme.typography.labelSmall)
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
    if (isVisible) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "No network connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                TextButton(onClick = onRetry) {
                    Text("Retry")
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
