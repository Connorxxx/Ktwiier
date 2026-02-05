package com.connor.kwitter.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "注册账号",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = { onAction(RegisterAction.EmailChanged(it)) },
                label = { Text("邮箱") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(RegisterAction.NameChanged(it)) },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.password,
                onValueChange = { onAction(RegisterAction.PasswordChanged(it)) },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onAction(RegisterAction.RegisterClicked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading &&
                        state.email.isNotBlank() &&
                        state.name.isNotBlank() &&
                        state.password.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("注册")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { onAction(RegisterAction.LoginClick) }) {
                Text("已有账号？去登录")
            }
        }
    }
}

@Preview
@Composable
private fun RegisterScreenPreview() {
    RegisterScreen(
        state = RegisterUiState(
            email = "test@example.com",
            name = "TestUser",
            password = "password123"
        ),
        onAction = {}
    )
}

@Preview
@Composable
private fun RegisterScreenLoadingPreview() {
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
