package com.connor.kwitter.features.createpost

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connor.kwitter.core.theme.KwitterTheme
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val MAX_POST_LENGTH = 280

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    state: CreatePostUiState,
    onAction: (CreatePostIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isReply = state.parentId != null

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onAction(CreatePostNavAction.OnPostCreated)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(CreatePostAction.ErrorDismissed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isReply) {
                            stringResource(Res.string.create_post_reply_title)
                        } else {
                            stringResource(Res.string.create_post_title)
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(CreatePostNavAction.BackClick) }) {
                        CloseIcon(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val focusManager = LocalFocusManager.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isReply) {
                Text(
                    text = stringResource(Res.string.create_post_replying_to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = state.content,
                onValueChange = { input ->
                    if (input.length <= MAX_POST_LENGTH) {
                        onAction(CreatePostAction.ContentChanged(input))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 240.dp),
                placeholder = {
                    Text(
                        text = if (isReply) {
                            stringResource(Res.string.create_post_reply_placeholder)
                        } else {
                            stringResource(Res.string.create_post_placeholder)
                        }
                    )
                },
                enabled = !state.isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${state.content.length}/$MAX_POST_LENGTH",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.content.length >= MAX_POST_LENGTH) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                val isFormValid = state.content.isNotBlank()
                Button(
                    onClick = { onAction(CreatePostAction.SubmitClicked) },
                    enabled = !state.isLoading && isFormValid,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isReply) {
                                stringResource(Res.string.create_post_reply_button)
                            } else {
                                stringResource(Res.string.create_post_button)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CloseIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val margin = size.minDimension * 0.2f
        // X shape
        drawLine(resolvedColor, Offset(margin, margin), Offset(size.width - margin, size.height - margin), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(size.width - margin, margin), Offset(margin, size.height - margin), stroke, cap = StrokeCap.Round)
    }
}

@Preview
@Composable
private fun CreatePostScreenPreview() {
    KwitterTheme(darkTheme = false) {
        CreatePostScreen(
            state = CreatePostUiState(
                content = "Hello world!"
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun CreatePostReplyPreview() {
    KwitterTheme(darkTheme = true) {
        CreatePostScreen(
            state = CreatePostUiState(
                content = "",
                parentId = "123"
            ),
            onAction = {}
        )
    }
}
