package com.connor.kwitter.features.conversationlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.connor.kwitter.core.ui.ErrorScreen
import com.connor.kwitter.core.ui.ErrorStateCard
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.core.ui.LoadingScreen
import com.connor.kwitter.core.util.formatPostTime
import com.connor.kwitter.core.util.resolveBackendUrl
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import com.connor.kwitter.domain.messaging.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.chat_message_deleted
import kwitter.composeapp.generated.resources.chat_message_recalled
import kwitter.composeapp.generated.resources.conversation_list_empty
import kwitter.composeapp.generated.resources.conversation_list_load_failed
import kwitter.composeapp.generated.resources.conversation_list_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    pagingFlow: Flow<PagingData<Conversation>>,
    onlineStatus: StateFlow<Map<String, Boolean>>,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (ConversationListIntent) -> Unit
) {
    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val onlineMap by onlineStatus.collectAsState()
    val listTitle = stringResource(Res.string.conversation_list_title)
    val loadFailedText = stringResource(Res.string.conversation_list_load_failed)
    val emptyText = stringResource(Res.string.conversation_list_empty)
    val deletedText = stringResource(Res.string.chat_message_deleted)
    val recalledText = stringResource(Res.string.chat_message_recalled)

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.Title(
            title = listTitle,
            leadingButton = NativeTopBarButtons.back()
        )
    )

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                ConversationListTopBar(
                    title = listTitle,
                    onBackClick = { onAction(ConversationListNavAction.BackClick) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val refreshState = lazyPagingItems.loadState.refresh
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()
        val refreshErrorMessage = (refreshState as? LoadState.Error)
            ?.error
            ?.message
            ?.takeIf { lazyPagingItems.itemCount > 0 }
            ?: if (refreshState is LoadState.Error && lazyPagingItems.itemCount > 0) {
                loadFailedText
            } else {
                null
            }

        PullToRefreshBox(
            isRefreshing = refreshState is LoadState.Loading && lazyPagingItems.itemCount > 0,
            onRefresh = { lazyPagingItems.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInsetPadding)
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

                refreshState is LoadState.NotLoading && lazyPagingItems.itemCount == 0 -> {
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
                            text = emptyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = topOverlayPadding + 8.dp,
                            bottom = 8.dp
                        )
                    ) {
                        items(
                            count = lazyPagingItems.itemCount,
                            key = lazyPagingItems.itemKey { it.id }
                        ) { index ->
                            val conversation = lazyPagingItems[index] ?: return@items
                            val isOnline = onlineMap[conversation.otherUser.id] ?: false
                            ConversationItemRow(
                                conversation = conversation,
                                isOnline = isOnline,
                                deletedText = deletedText,
                                recalledText = recalledText,
                                onClick = {
                                    onAction(
                                        ConversationListNavAction.ConversationClick(
                                            conversationId = conversation.id,
                                            otherUserId = conversation.otherUser.id,
                                            otherUserDisplayName = conversation.otherUser.displayName,
                                            otherUserAvatarUrl = conversation.otherUser.avatarUrl
                                        )
                                    )
                                }
                            )
                        }

                        if (lazyPagingItems.loadState.append is LoadState.Loading) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (refreshErrorMessage != null) {
            ErrorStateCard(
                message = refreshErrorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        top = topOverlayPadding + 8.dp,
                        end = 16.dp
                    ),
                onRetry = { lazyPagingItems.refresh() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                GlassTopBarTitle(text = title)
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
private fun ConversationItemRow(
    conversation: Conversation,
    isOnline: Boolean,
    deletedText: String,
    recalledText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online indicator
        Box {
            ConversationAvatar(
                name = conversation.otherUser.displayName,
                avatarUrl = conversation.otherUser.avatarUrl,
                modifier = Modifier.size(48.dp)
            )
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .size(14.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = CircleShape
                        )
                        .padding(2.dp)
                        .background(
                            color = Color(0xFF4CAF50),
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name + last message preview
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.otherUser.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                conversation.lastMessage?.let { message ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatPostTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (conversation.unreadCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lastMessage = conversation.lastMessage
                val previewText = when {
                    lastMessage == null -> ""
                    lastMessage.isDeleted -> deletedText
                    lastMessage.isRecalled -> recalledText
                    else -> lastMessage.content
                }
                val isSpecialMessage = lastMessage != null && !lastMessage.isNormalMessage

                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (isSpecialMessage) FontStyle.Italic else FontStyle.Normal,
                    color = if (isSpecialMessage) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (conversation.unreadCount > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (conversation.unreadCount > 0 && !isSpecialMessage) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatar(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = resolveBackendUrl(avatarUrl),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    } else {
        Box(
            modifier = modifier
                .background(
                    brush = Brush.linearGradient(gradient),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
