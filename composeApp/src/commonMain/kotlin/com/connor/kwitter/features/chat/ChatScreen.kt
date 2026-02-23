package com.connor.kwitter.features.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.core.util.formatPostTime
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import com.connor.kwitter.domain.messaging.model.Message
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    pagingFlow: Flow<PagingData<Message>>,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (ChatIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(ChatAction.ErrorDismissed)
        }
    }

    // Scroll to bottom when new messages arrive, only if user is near bottom
    val isNearBottom = remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            firstVisibleIndex <= 1
        }
    }

    LaunchedEffect(lazyPagingItems.itemCount) {
        if (lazyPagingItems.itemCount > 0 && isNearBottom.value) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(lazyPagingItems.loadState.refresh) {
        val refreshState = lazyPagingItems.loadState.refresh
        if (refreshState is LoadState.Error && lazyPagingItems.itemCount > 0) {
            snackbarHostState.showSnackbar(
                refreshState.error.message ?: "Failed to load messages"
            )
        }
    }

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.Title(
            title = state.otherUserDisplayName,
            leadingButton = NativeTopBarButtons.back()
        )
    )

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                ChatTopBar(
                    displayName = state.otherUserDisplayName,
                    onBackClick = { onAction(ChatNavAction.BackClick) }
                )
            }
        },
        bottomBar = {
            ChatInputBar(
                input = state.messageInput,
                isSending = state.isSending,
                onInputChange = { onAction(ChatAction.UpdateMessageInput(it)) },
                onSendClick = { onAction(ChatAction.SendMessage) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val refreshState = lazyPagingItems.loadState.refresh
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()

        when {
            refreshState is LoadState.Loading && lazyPagingItems.itemCount == 0 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = topOverlayPadding,
                            bottom = bottomInsetPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            lazyPagingItems.itemCount == 0 && refreshState !is LoadState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = topOverlayPadding,
                            bottom = bottomInsetPadding
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start a conversation",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomInsetPadding),
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = topOverlayPadding + 8.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        count = lazyPagingItems.itemCount,
                        key = lazyPagingItems.itemKey { it.id }
                    ) { index ->
                        val message = lazyPagingItems[index] ?: return@items
                        val isSentByMe = message.senderId == state.currentUserId
                        MessageBubble(
                            message = message,
                            isSentByMe = isSentByMe
                        )
                    }

                    if (lazyPagingItems.loadState.append is LoadState.Loading) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    displayName: String,
    onBackClick: () -> Unit
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                GlassTopBarTitle(
                    text = displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                GlassTopBarBackButton(
                    onClick = onBackClick,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isSentByMe: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (isSentByMe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isSentByMe) 16.dp else 4.dp,
                        bottomEnd = if (isSentByMe) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSentByMe) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatPostTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSentByMe && message.readAt != null) {
                ReadReceiptIcon(
                    modifier = Modifier.size(12.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .imePadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.width(8.dp))

            val canSend = input.isNotBlank() && !isSending
            IconButton(
                onClick = onSendClick,
                enabled = canSend,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    SendIcon(
                        modifier = Modifier.size(18.dp),
                        color = if (canSend) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SendIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        // Arrow pointing right
        val left = size.width * 0.15f
        val right = size.width * 0.85f
        val centerY = size.height * 0.5f
        val arrowTop = size.height * 0.25f
        val arrowBottom = size.height * 0.75f

        // Main line
        drawLine(resolvedColor, Offset(left, centerY), Offset(right, centerY), stroke, cap = StrokeCap.Round)
        // Arrow head top
        drawLine(resolvedColor, Offset(right, centerY), Offset(right - size.width * 0.25f, arrowTop), stroke, cap = StrokeCap.Round)
        // Arrow head bottom
        drawLine(resolvedColor, Offset(right, centerY), Offset(right - size.width * 0.25f, arrowBottom), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun ReadReceiptIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        // Double checkmark
        val y1 = size.height * 0.6f
        val y2 = size.height * 0.35f
        // First check
        drawLine(resolvedColor, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.35f, y1), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(size.width * 0.35f, y1), Offset(size.width * 0.6f, y2), stroke, cap = StrokeCap.Round)
        // Second check (offset)
        drawLine(resolvedColor, Offset(size.width * 0.3f, size.height * 0.5f), Offset(size.width * 0.55f, y1), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(size.width * 0.55f, y1), Offset(size.width * 0.85f, y2), stroke, cap = StrokeCap.Round)
    }
}
