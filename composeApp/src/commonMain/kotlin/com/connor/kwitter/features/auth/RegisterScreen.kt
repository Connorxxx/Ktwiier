package com.connor.kwitter.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.connor.kwitter.core.theme.KwitterTheme
import androidx.compose.ui.text.input.KeyboardType
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 注册界面 - 状态提升版本
 * State 和 Action 都通过参数传递，支持预览
 */
@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onAction: (RegisterIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isPasswordVisible by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val textPrimary = colors.onBackground
    val textSecondary = colors.onSurfaceVariant
    val textMuted = colors.onSurfaceVariant.copy(alpha = 0.7f)

    // 监听注册成功
    LaunchedEffect(state.registeredToken) {
        state.registeredToken?.let {
            onAction(RegisterNavAction.OnRegisterSuccess)
        }
    }

    // 显示错误信息
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(RegisterAction.ErrorDismissed)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp).padding(bottom = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.register_terms),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = textMuted
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                KwitterLogo(
                    modifier = Modifier.size(44.dp),
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.register_title),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RegisterTextField(
                        label = stringResource(Res.string.register_email_label),
                        value = state.email,
                        onValueChange = { onAction(RegisterAction.EmailChanged(it)) },
                        placeholder = stringResource(Res.string.register_email_placeholder),
                        enabled = !state.isLoading
                    )

                    RegisterTextField(
                        label = stringResource(Res.string.register_username_label),
                        value = state.name,
                        onValueChange = { onAction(RegisterAction.NameChanged(it)) },
                        placeholder = stringResource(Res.string.register_username_placeholder),
                        enabled = !state.isLoading
                    )

                    RegisterTextField(
                        label = stringResource(Res.string.register_password_label),
                        value = state.password,
                        onValueChange = { input ->
                            onAction(RegisterAction.PasswordChanged(filterPasswordInput(input)))
                        },
                        placeholder = stringResource(Res.string.register_password_placeholder),
                        enabled = !state.isLoading,
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                enabled = !state.isLoading
                            ) {
                                PasswordVisibilityIcon(
                                    isVisible = isPasswordVisible,
                                    tint = textSecondary
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                val isFormValid = state.email.isNotBlank() &&
                    state.name.isNotBlank() &&
                    state.password.isNotBlank()
                val buttonColors = if (state.isLoading) {
                    ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = colors.primary,
                        disabledContentColor = colors.onPrimary
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = colors.surfaceContainerHigh,
                        disabledContentColor = colors.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onAction(RegisterAction.RegisterClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !state.isLoading && isFormValid,
                    shape = RoundedCornerShape(14.dp),
                    colors = buttonColors,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    )
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.register_button),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = stringResource(Res.string.register_have_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary
                    )
                    TextButton(
                        onClick = { onAction(RegisterNavAction.LoginClick) },
                        colors = ButtonDefaults.textButtonColors(contentColor = textPrimary)
                    ) {
                        Text(
                            text = stringResource(Res.string.register_login_link),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RegisterTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val textPrimary = colors.onSurface
    val textSecondary = colors.onSurfaceVariant
    val textMuted = colors.onSurfaceVariant.copy(alpha = 0.7f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = textSecondary
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textPrimary),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            enabled = enabled,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = textPrimary,
                unfocusedTextColor = textPrimary,
                disabledTextColor = textPrimary.copy(alpha = 0.6f),
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface,
                focusedIndicatorColor = colors.primary,
                unfocusedIndicatorColor = colors.outline,
                disabledIndicatorColor = colors.outline,
                cursorColor = colors.primary,
                focusedPlaceholderColor = textMuted,
                unfocusedPlaceholderColor = textMuted,
                disabledPlaceholderColor = textMuted
            )
        )
    }
}

private fun filterPasswordInput(raw: String): String {
    if (raw.isEmpty()) return raw
    return raw.filter { it.isAllowedPasswordChar() }
}

private fun Char.isAllowedPasswordChar(): Boolean {
    return isAsciiLetterOrDigit() || isAsciiPunctuation()
}

private fun Char.isAsciiLetterOrDigit(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

private fun Char.isAsciiPunctuation(): Boolean {
    val code = this.code
    return code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126
}

@Composable
private fun PasswordVisibilityIcon(
    isVisible: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(20.dp)
    ) {
        val strokeWidth = size.minDimension * 0.12f
        val outline = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val eyeHeight = size.height * 0.6f
        val eyeTop = (size.height - eyeHeight) / 2f

        drawOval(
            color = tint,
            topLeft = Offset(0f, eyeTop),
            size = Size(size.width, eyeHeight),
            style = outline
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.12f,
            center = center
        )

        if (!isVisible) {
            drawLine(
                color = tint,
                start = Offset(size.width * 0.18f, size.height * 0.18f),
                end = Offset(size.width * 0.82f, size.height * 0.82f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun KwitterLogo(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onBackground
    } else {
        color
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val leftX = size.width * 0.18f
        val centerY = size.height * 0.52f
        val topY = size.height * 0.14f
        val bottomY = size.height * 0.88f
        val rightX = size.width * 0.84f

        drawLine(
            color = resolvedColor,
            start = Offset(leftX, topY),
            end = Offset(leftX, bottomY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = resolvedColor,
            start = Offset(leftX, centerY),
            end = Offset(rightX, topY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = resolvedColor,
            start = Offset(leftX, centerY),
            end = Offset(rightX, bottomY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = resolvedColor,
            radius = stroke * 0.35f,
            center = Offset(size.width * 0.9f, centerY)
        )
    }
}

@Preview
@Composable
private fun RegisterScreenPreview() {
    KwitterTheme(darkTheme = false) {
        RegisterScreen(
            state = RegisterUiState(
                email = "test@example.com",
                name = "TestUser",
                password = "password123"
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun RegisterScreenLoadingPreview() {
    KwitterTheme(darkTheme = true) {
        RegisterScreen(
            state = RegisterUiState(
                email = "test@example.com",
                name = "TestUser",
                password = "password123",
                isLoading = true
            ),
            onAction = {}
        )
    }
}
