package com.connor.kwitter.features.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.connor.kwitter.core.result.errorOrNull
import com.connor.kwitter.core.ui.ErrorScreen
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.GlassTopBarIconButton
import com.connor.kwitter.core.ui.GlassTopBarIconContentColor
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.core.ui.ErrorStateCard
import com.connor.kwitter.core.ui.LoadingScreen
import com.connor.kwitter.core.util.formatPostTime
import com.connor.kwitter.core.util.resolveBackendUrl
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import com.connor.kwitter.features.glass.getNativeTopBarController
import com.connor.kwitter.domain.messaging.model.Message
import com.connor.kwitter.features.search.SearchIcon
import kotlinx.coroutines.flow.Flow
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.chat_delete
import kwitter.composeapp.generated.resources.chat_input_placeholder
import kwitter.composeapp.generated.resources.chat_load_failed
import kwitter.composeapp.generated.resources.chat_message_deleted
import kwitter.composeapp.generated.resources.chat_message_recalled
import kwitter.composeapp.generated.resources.chat_read_receipt
import kwitter.composeapp.generated.resources.chat_recall
import kwitter.composeapp.generated.resources.chat_reply
import kwitter.composeapp.generated.resources.chat_reply_message_unavailable
import kwitter.composeapp.generated.resources.chat_replying_to
import kwitter.composeapp.generated.resources.chat_start_conversation
import kwitter.composeapp.generated.resources.chat_typing
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    pagingFlow: Flow<PagingData<Message>>,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (ChatIntent) -> Unit
) {
    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val nativeTopBarController = remember { getNativeTopBarController() }
    val loadFailedText = stringResource(Res.string.chat_load_failed)
    val emptyHint = stringResource(Res.string.chat_start_conversation)
    val inputPlaceholder = stringResource(Res.string.chat_input_placeholder)
    val readReceiptText = stringResource(Res.string.chat_read_receipt)
    val deletedText = stringResource(Res.string.chat_message_deleted)
    val recalledText = stringResource(Res.string.chat_message_recalled)
    val replyText = stringResource(Res.string.chat_reply)
    val deleteText = stringResource(Res.string.chat_delete)
    val recallText = stringResource(Res.string.chat_recall)
    val typingText = stringResource(Res.string.chat_typing)
    val replyingToText = stringResource(Res.string.chat_replying_to)
    val replyMessageUnavailableText = stringResource(Res.string.chat_reply_message_unavailable)
    val operationErrorMessage = state.operationResult.errorOrNull()
    val refreshErrorMessage = (lazyPagingItems.loadState.refresh as? LoadState.Error)
        ?.error
        ?.message
        ?.takeIf { lazyPagingItems.itemCount > 0 }
        ?: if (lazyPagingItems.loadState.refresh is LoadState.Error && lazyPagingItems.itemCount > 0) {
            loadFailedText
        } else {
            null
        }
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus(force = true)
        nativeTopBarController?.dismissKeyboard()
    }
    val loadedMessages = lazyPagingItems.itemSnapshotList.items
    val loadedMessageMap = remember(loadedMessages) {
        loadedMessages.associateBy { it.id }
    }

    DisposableEffect(Unit) {
        onDispose {
            onAction(ChatAction.ScreenDisposed)
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

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.Title(
            title = state.otherUserDisplayName,
            leadingButton = NativeTopBarButtons.back(),
            trailingButton = NativeTopBarButtons.search()
        )
    )

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                ChatTopBar(
                    displayName = state.otherUserDisplayName,
                    avatarUrl = state.otherUserAvatarUrl,
                    onBackClick = {
                        dismissKeyboard()
                        onAction(ChatNavAction.BackClick)
                    },
                    onProfileClick = {
                        dismissKeyboard()
                        onAction(ChatNavAction.UserProfileClick(state.otherUserId))
                    },
                    onSearchClick = {
                        dismissKeyboard()
                        onAction(ChatNavAction.SearchClick)
                    }
                )
            }
        },
        bottomBar = {
            ChatInputBar(
                input = state.messageInput,
                inputPlaceholder = inputPlaceholder,
                isSending = state.isSending,
                replyingToMessage = state.replyingToMessage,
                replyingToText = replyingToText,
                onInputChange = { onAction(ChatAction.UpdateMessageInput(it)) },
                onSendClick = {
                    dismissKeyboard()
                    onAction(ChatAction.SendMessage)
                },
                onCancelReply = {
                    dismissKeyboard()
                    onAction(ChatAction.CancelReply)
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val refreshState = lazyPagingItems.loadState.refresh
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { dismissKeyboard() }
                }
        ) {
            when {
                refreshState is LoadState.Loading && lazyPagingItems.itemCount == 0 -> {
                    LoadingScreen(
                        contentPadding = PaddingValues(
                            top = topOverlayPadding,
                            bottom = bottomInsetPadding
                        )
                    )
                }

                refreshState is LoadState.Error && lazyPagingItems.itemCount == 0 -> {
                    ErrorScreen(
                        message = refreshState.error.message ?: loadFailedText,
                        contentPadding = PaddingValues(
                            top = topOverlayPadding,
                            bottom = bottomInsetPadding
                        ),
                        onRetry = { lazyPagingItems.refresh() }
                    )
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
                            text = emptyHint,
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Typing indicator at the "bottom" (index 0 in reverse layout)
                        if (state.isOtherUserTyping) {
                            item(key = "typing_indicator") {
                                TypingIndicator(
                                    displayName = state.otherUserDisplayName,
                                    avatarUrl = state.otherUserAvatarUrl,
                                    typingText = typingText
                                )
                            }
                        }

                        items(
                            count = lazyPagingItems.itemCount,
                            key = lazyPagingItems.itemKey { it.id }
                        ) { index ->
                            val message = lazyPagingItems[index] ?: return@items
                            val isSentByMe = message.senderId == state.currentUserId
                            val repliedMessage = message.replyToMessageId?.let { replyId ->
                                loadedMessageMap[replyId]
                            }
                            MessageBubble(
                                message = message,
                                repliedMessage = repliedMessage,
                                isSentByMe = isSentByMe,
                                otherUserDisplayName = state.otherUserDisplayName,
                                otherUserAvatarUrl = state.otherUserAvatarUrl,
                                readReceiptText = readReceiptText,
                                deletedText = deletedText,
                                recalledText = recalledText,
                                replyMessageUnavailableText = replyMessageUnavailableText,
                                replyText = replyText,
                                deleteText = deleteText,
                                recallText = recallText,
                                onInteraction = dismissKeyboard,
                                onOtherUserClick = {
                                    dismissKeyboard()
                                    onAction(ChatNavAction.UserProfileClick(state.otherUserId))
                                },
                                onReply = {
                                    dismissKeyboard()
                                    onAction(ChatAction.StartReply(message))
                                },
                                onDelete = {
                                    dismissKeyboard()
                                    onAction(ChatAction.DeleteMessage(message.id))
                                },
                                onRecall = {
                                    dismissKeyboard()
                                    onAction(ChatAction.RecallMessage(message.id))
                                }
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

        if (operationErrorMessage != null || refreshErrorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        top = topOverlayPadding + 8.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                operationErrorMessage?.let { message ->
                    ErrorStateCard(
                        message = message,
                        onDismiss = { onAction(ChatAction.ErrorDismissed) }
                    )
                }
                refreshErrorMessage?.let { message ->
                    ErrorStateCard(
                        message = message,
                        onRetry = { lazyPagingItems.refresh() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    displayName: String,
    avatarUrl: String?,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChatParticipantAvatar(
                        name = displayName,
                        avatarUrl = avatarUrl,
                        size = 34.dp,
                        onClick = onProfileClick
                    )
                    GlassTopBarTitle(
                        text = displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                GlassTopBarBackButton(
                    onClick = onBackClick,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            },
            actions = {
                GlassTopBarIconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    SearchIcon(
                        modifier = Modifier.size(18.dp),
                        color = GlassTopBarIconContentColor()
                    )
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun TypingIndicator(
    displayName: String,
    avatarUrl: String?,
    typingText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        ChatParticipantAvatar(
            name = displayName,
            avatarUrl = avatarUrl,
            size = 40.dp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 18.dp
                    )
                )
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(
                text = typingText,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    repliedMessage: Message?,
    isSentByMe: Boolean,
    otherUserDisplayName: String,
    otherUserAvatarUrl: String?,
    readReceiptText: String,
    deletedText: String,
    recalledText: String,
    replyMessageUnavailableText: String,
    replyText: String,
    deleteText: String,
    recallText: String,
    onInteraction: () -> Unit,
    onOtherUserClick: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onRecall: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSentByMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isSentByMe) {
            ChatParticipantAvatar(
                name = otherUserDisplayName,
                avatarUrl = otherUserAvatarUrl,
                size = 40.dp,
                modifier = Modifier.padding(bottom = 16.dp),
                onClick = onOtherUserClick
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            // Reply quote preview
            if (message.replyToMessageId != null && message.isNormalMessage) {
                val replyPreviewText = when {
                    repliedMessage == null -> replyMessageUnavailableText
                    repliedMessage.isDeleted -> deletedText
                    repliedMessage.isRecalled -> recalledText
                    repliedMessage.content.isBlank() -> replyMessageUnavailableText
                    else -> repliedMessage.content
                }
                Box(
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .padding(bottom = 2.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "\u21A9 $replyPreviewText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .let { mod ->
                            if (message.isNormalMessage) {
                                mod.combinedClickable(
                                    onClick = onInteraction,
                                    onLongClick = {
                                        onInteraction()
                                        showContextMenu = true
                                    }
                                )
                            } else {
                                mod
                            }
                        }
                        .background(
                            color = when {
                                !message.isNormalMessage -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                isSentByMe -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isSentByMe) 18.dp else 6.dp,
                                bottomEnd = if (isSentByMe) 6.dp else 18.dp
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = when {
                            message.isDeleted -> deletedText
                            message.isRecalled -> recalledText
                            else -> message.content
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = if (!message.isNormalMessage) FontStyle.Italic else FontStyle.Normal,
                        color = when {
                            !message.isNormalMessage -> MaterialTheme.colorScheme.onSurfaceVariant
                            isSentByMe -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Context menu
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(replyText) },
                        onClick = {
                            showContextMenu = false
                            onReply()
                        }
                    )
                    if (isSentByMe) {
                        DropdownMenuItem(
                            text = { Text(recallText) },
                            onClick = {
                                showContextMenu = false
                                onRecall()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    deleteText,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showContextMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = formatPostTime(message.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSentByMe && message.readAt != null && message.isNormalMessage) {
                    ReadReceiptBadge(text = readReceiptText)
                }
            }
        }
    }
}

@Composable
private fun ReadReceiptBadge(text: String) {
    val receiptColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = receiptColor
        )
    }
}

@Composable
private fun ChatParticipantAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarModifier = modifier
        .size(size)
        .clip(CircleShape)
        .let { base ->
            if (onClick != null) {
                base.clickable(onClick = onClick)
            } else {
                base
            }
        }

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = resolveBackendUrl(avatarUrl),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier
        )
    } else {
        Box(
            modifier = avatarModifier.background(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                ),
                shape = CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    inputPlaceholder: String,
    isSending: Boolean,
    replyingToMessage: Message?,
    replyingToText: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelReply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Reply preview bar
        AnimatedVisibility(
            visible = replyingToMessage != null,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            replyingToMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = replyingToText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onCancelReply,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            text = "\u2715",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .chatInputKeyboardAwarePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 38.dp, max = 108.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (input.isBlank()) {
                            Text(
                                text = inputPlaceholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            val canSend = input.isNotBlank() && !isSending
            val sendContainerColor = if (canSend || isSending) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(sendContainerColor)
                    .clickable(enabled = canSend, onClick = onSendClick),
                contentAlignment = Alignment.Center
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    SendIcon(
                        modifier = Modifier.size(15.dp),
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
