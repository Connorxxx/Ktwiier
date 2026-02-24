package com.connor.kwitter.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.ui.ErrorStateCard
import com.connor.kwitter.features.auth.AuthUiError
import com.connor.kwitter.features.main.LocalMainBottomBarOverlayPadding
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.auth_error_client
import kwitter.composeapp.generated.resources.auth_error_invalid_credentials
import kwitter.composeapp.generated.resources.auth_error_invalid_credentials_with_detail
import kwitter.composeapp.generated.resources.auth_error_network
import kwitter.composeapp.generated.resources.auth_error_server
import kwitter.composeapp.generated.resources.auth_error_session_revoked
import kwitter.composeapp.generated.resources.auth_error_storage
import kwitter.composeapp.generated.resources.auth_error_unknown
import kwitter.composeapp.generated.resources.profile_cancel_edit
import kwitter.composeapp.generated.resources.settings_change_password_in_development
import kwitter.composeapp.generated.resources.settings_account_privacy
import kwitter.composeapp.generated.resources.settings_allow_default_pm_subtitle
import kwitter.composeapp.generated.resources.settings_allow_default_pm_title
import kwitter.composeapp.generated.resources.settings_change_password_subtitle
import kwitter.composeapp.generated.resources.settings_change_password_title
import kwitter.composeapp.generated.resources.settings_logout
import kwitter.composeapp.generated.resources.settings_logout_confirm_message
import kwitter.composeapp.generated.resources.settings_logout_confirm_title
import kwitter.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    val textPrimary = colors.onBackground
    val textSecondary = colors.onSurfaceVariant
    val infoMessage = state.infoMessage?.let { resolveSettingsInfoMessage(it) }
    val errorMessage = state.error?.let { resolveSettingsAuthError(it) }

    LaunchedEffect(infoMessage) {
        infoMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onAction(SettingsAction.ChangePasswordMessageConsumed)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            val bottomOverlayPadding = LocalMainBottomBarOverlayPadding.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(
                        start = 24.dp,
                        top = 24.dp,
                        end = 24.dp,
                        bottom = 24.dp + bottomOverlayPadding
                    )
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = textPrimary
                )
                Text(
                    text = stringResource(Res.string.settings_account_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )

                if (errorMessage != null) {
                    ErrorStateCard(
                        message = errorMessage,
                        onDismiss = { onAction(SettingsAction.ErrorDismissed) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                SettingsActionCard(
                    title = stringResource(Res.string.settings_change_password_title),
                    subtitle = stringResource(Res.string.settings_change_password_subtitle),
                    onClick = { onAction(SettingsAction.ChangePasswordClick) }
                )

                SettingsToggleCard(
                    title = stringResource(Res.string.settings_allow_default_pm_title),
                    subtitle = stringResource(Res.string.settings_allow_default_pm_subtitle),
                    checked = state.allowDefaultPm,
                    onCheckedChange = {
                        onAction(SettingsAction.AllowDefaultPmChanged(it))
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onAction(SettingsAction.LogoutClick) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = textPrimary,
                        contentColor = colors.background,
                        disabledContainerColor = textPrimary,
                        disabledContentColor = colors.background
                    ),
                    enabled = !state.isLoggingOut,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    if (state.isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.background,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.settings_logout),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (state.isLogoutDialogVisible) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.LogoutDismiss) },
            title = { Text(stringResource(Res.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_logout_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = { onAction(SettingsAction.LogoutConfirm) }
                ) {
                    Text(stringResource(Res.string.settings_logout))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SettingsAction.LogoutDismiss) }
                ) {
                    Text(stringResource(Res.string.profile_cancel_edit))
                }
            }
        )
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .border(
                width = 1.dp,
                color = colors.outline.copy(alpha = 0.55f),
                shape = shape
            ),
        shape = shape,
        color = colors.surface,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .border(
                width = 1.dp,
                color = colors.outline.copy(alpha = 0.55f),
                shape = shape
            ),
        shape = shape,
        color = colors.surface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 14.dp, vertical = 13.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun resolveSettingsInfoMessage(message: SettingsInfoMessage): String = when (message) {
    SettingsInfoMessage.ChangePasswordInDevelopment -> {
        stringResource(Res.string.settings_change_password_in_development)
    }
}

@Composable
private fun resolveSettingsAuthError(error: AuthUiError): String = when (error) {
    is AuthUiError.Network -> stringResource(Res.string.auth_error_network, error.detail)
    is AuthUiError.Server -> stringResource(Res.string.auth_error_server, error.code, error.detail)
    is AuthUiError.Client -> stringResource(Res.string.auth_error_client, error.code, error.detail)
    is AuthUiError.InvalidCredentials -> {
        val detail = error.detail
        if (detail.isNullOrBlank()) {
            stringResource(Res.string.auth_error_invalid_credentials)
        } else {
            stringResource(Res.string.auth_error_invalid_credentials_with_detail, detail)
        }
    }
    is AuthUiError.Storage -> stringResource(Res.string.auth_error_storage, error.detail)
    is AuthUiError.Unknown -> stringResource(Res.string.auth_error_unknown, error.detail)
    is AuthUiError.SessionRevoked -> stringResource(Res.string.auth_error_session_revoked, error.detail)
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    KwitterTheme(darkTheme = false) {
        SettingsScreen(
            state = SettingsUiState(),
            onAction = {}
        )
    }
}
