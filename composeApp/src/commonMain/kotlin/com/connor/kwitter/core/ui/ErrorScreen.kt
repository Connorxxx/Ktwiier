package com.connor.kwitter.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.error_screen_default_message
import kwitter.composeapp.generated.resources.error_screen_dismiss
import kwitter.composeapp.generated.resources.error_screen_retry
import kwitter.composeapp.generated.resources.error_screen_title

@Composable
fun ErrorScreen(
    message: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    title: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val resolvedTitle = title ?: stringResource(Res.string.error_screen_title)
    val resolvedMessage = message.ifBlank {
        stringResource(Res.string.error_screen_default_message)
    }
    val retryText = stringResource(Res.string.error_screen_retry)
    val dismissText = stringResource(Res.string.error_screen_dismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = resolvedTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Text(
                text = resolvedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onRetry != null || onDismiss != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    onRetry?.let { retry ->
                        TextButton(onClick = retry) {
                            Text(text = retryText)
                        }
                    }
                    onDismiss?.let { dismiss ->
                        TextButton(onClick = dismiss) {
                            Text(text = dismissText)
                        }
                    }
                }
            }
        }
    }
}
