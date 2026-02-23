package com.connor.kwitter.features.postdetail

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.ui.PostActionBar
import com.connor.kwitter.core.ui.PostMediaGrid
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.core.util.formatPostTime
import com.connor.kwitter.core.util.resolveBackendUrl
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostAuthor
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.domain.post.model.PostStats
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.post_detail_conversation_title
import kwitter.composeapp.generated.resources.post_detail_hide_replies
import kwitter.composeapp.generated.resources.post_detail_no_replies
import kwitter.composeapp.generated.resources.post_detail_reply_count
import kwitter.composeapp.generated.resources.post_detail_replies_header
import kwitter.composeapp.generated.resources.post_detail_show_replies
import kwitter.composeapp.generated.resources.post_detail_start_first_reply
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    state: PostDetailUiState,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (PostDetailIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val nativeSubtitle = if (state.threadReplies.isNotEmpty()) {
        stringResource(Res.string.post_detail_reply_count, state.threadReplies.size)
    } else {
        stringResource(Res.string.post_detail_start_first_reply)
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(PostDetailAction.ErrorDismissed)
        }
    }

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.Title(
            title = stringResource(Res.string.post_detail_conversation_title),
            subtitle = nativeSubtitle,
            leadingButton = NativeTopBarButtons.back()
        )
    )

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                ThreadTopBar(
                    replyCount = state.threadReplies.size,
                    onBackClick = { onAction(PostDetailNavAction.BackClick) }
                )
            }
        },
        floatingActionButton = {
            state.post?.let { post ->
                FloatingActionButton(
                    onClick = {
                        onAction(
                            PostDetailNavAction.ReplyClick(
                                postId = post.id,
                                authorName = post.authorName,
                                content = post.content,
                                avatarUrl = post.author.avatarUrl
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    ReplyIcon(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()

        when {
            state.isLoading -> {
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

            state.post != null -> {
                var expandedReplyIds by remember(state.post.id) {
                    mutableStateOf(emptySet<String>())
                }
                LaunchedEffect(state.threadReplies) {
                    val currentReplyIds = state.threadReplies.map { it.post.id }.toSet()
                    expandedReplyIds = expandedReplyIds.intersect(currentReplyIds)
                }
                val replyIdsWithChildren = remember(state.threadReplies) {
                    state.threadReplies.mapNotNull { it.post.parentId }.toSet()
                }
                val visibleReplies = remember(state.threadReplies, expandedReplyIds) {
                    visibleThreadReplies(
                        replies = state.threadReplies,
                        expandedReplyIds = expandedReplyIds
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomInsetPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = topOverlayPadding + 6.dp,
                        end = 16.dp,
                        bottom = 6.dp
                    )
                ) {
                    item {
                        RootPostItem(
                            post = state.post,
                            onReplyClick = { targetPost ->
                                onAction(
                                    PostDetailNavAction.ReplyClick(
                                        postId = targetPost.id,
                                        authorName = targetPost.authorName,
                                        content = targetPost.content,
                                        avatarUrl = targetPost.author.avatarUrl
                                    )
                                )
                            },
                            onLikeClick = {
                                onAction(PostDetailAction.ToggleLike(state.post.id))
                            },
                            onBookmarkClick = {
                                onAction(PostDetailAction.ToggleBookmark(state.post.id))
                            },
                            onMediaClick = { index ->
                                onAction(PostDetailNavAction.MediaClick(state.post.media, index))
                            },
                            onAuthorClick = {
                                onAction(PostDetailNavAction.AuthorClick(state.post.author.id))
                            }
                        )
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.post_detail_replies_header),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.threadReplies.isNotEmpty()) {
                                RepliesBadge(text = state.threadReplies.size.toString())
                            }
                        }
                    }

                    if (state.threadReplies.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.post_detail_no_replies),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    } else {
                        items(visibleReplies, key = { reply -> reply.post.id }) { reply ->
                            val hasNestedReplies = reply.post.id in replyIdsWithChildren
                            ReplyItem(
                                threadReply = reply,
                                hasNestedReplies = hasNestedReplies,
                                isRepliesExpanded = reply.post.id in expandedReplyIds,
                                onRepliesToggleClick = {
                                    expandedReplyIds = toggleReplyExpansion(
                                        currentExpandedReplyIds = expandedReplyIds,
                                        replyId = reply.post.id
                                    )
                                },
                                onReplyClick = { targetPost ->
                                    onAction(
                                        PostDetailNavAction.ReplyClick(
                                            postId = targetPost.id,
                                            authorName = targetPost.authorName,
                                            content = targetPost.content,
                                            avatarUrl = targetPost.author.avatarUrl
                                        )
                                    )
                                },
                                onLikeClick = {
                                    onAction(PostDetailAction.ToggleLike(reply.post.id))
                                },
                                onBookmarkClick = {
                                    onAction(PostDetailAction.ToggleBookmark(reply.post.id))
                                },
                                onMediaClick = { index ->
                                    onAction(PostDetailNavAction.MediaClick(reply.post.media, index))
                                },
                                onAuthorClick = {
                                    onAction(PostDetailNavAction.AuthorClick(reply.post.author.id))
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadTopBar(
    replyCount: Int,
    onBackClick: () -> Unit
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlassTopBarTitle(text = stringResource(Res.string.post_detail_conversation_title))
                    Text(
                        text = if (replyCount > 0) {
                            stringResource(Res.string.post_detail_reply_count, replyCount)
                        } else {
                            stringResource(Res.string.post_detail_start_first_reply)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
private fun RootPostItem(
    post: Post,
    onReplyClick: (Post) -> Unit,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onMediaClick: (Int) -> Unit,
    onAuthorClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        ThreadAvatar(
            name = post.authorName,
            avatarUrl = post.author.avatarUrl,
            size = 46.dp,
            gradient = listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            ),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onAuthorClick
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = post.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "\u00B7",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatPostTime(post.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 26.sp,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (post.media.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PostMediaGrid(
                    media = post.media,
                    onMediaClick = onMediaClick
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PostActionBar(
                replyCount = post.replyCount,
                likeCount = post.stats.likeCount,
                isLiked = post.isLikedByCurrentUser == true,
                isBookmarked = post.isBookmarkedByCurrentUser == true,
                onReplyClick = { onReplyClick(post) },
                onLikeClick = onLikeClick,
                onBookmarkClick = onBookmarkClick
            )
        }
    }
}

@Composable
private fun ReplyItem(
    threadReply: ThreadReplyItem,
    hasNestedReplies: Boolean,
    isRepliesExpanded: Boolean,
    onRepliesToggleClick: () -> Unit,
    onReplyClick: (Post) -> Unit,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onMediaClick: (Int) -> Unit,
    onAuthorClick: () -> Unit
) {
    val reply = threadReply.post
    val indentation = (threadReply.depth * 20).coerceAtMost(80).dp
    val replyAvatarSize = 36.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentation, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        ThreadAvatar(
            name = reply.authorName,
            avatarUrl = reply.author.avatarUrl,
            size = replyAvatarSize,
            gradient = listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            ),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onAuthorClick
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = reply.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "\u00B7",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatPostTime(reply.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )

            if (reply.media.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                PostMediaGrid(
                    media = reply.media,
                    onMediaClick = onMediaClick
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            PostActionBar(
                replyCount = reply.replyCount,
                likeCount = reply.stats.likeCount,
                isLiked = reply.isLikedByCurrentUser == true,
                isBookmarked = reply.isBookmarkedByCurrentUser == true,
                onReplyClick = { onReplyClick(reply) },
                onLikeClick = onLikeClick,
                onBookmarkClick = onBookmarkClick
            )

            if (hasNestedReplies) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        )
                        .clickable(onClick = onRepliesToggleClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isRepliesExpanded) {
                            stringResource(Res.string.post_detail_hide_replies)
                        } else {
                            stringResource(Res.string.post_detail_show_replies)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    ExpandCollapseChevron(
                        expanded = isRepliesExpanded,
                        modifier = Modifier.size(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadAvatar(
    name: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    gradient: List<Color>,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    val avatarModifier = modifier
        .size(size)
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick)
            else Modifier
        )

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = resolveBackendUrl(avatarUrl),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    } else {
        Box(
            modifier = avatarModifier.background(
                brush = Brush.linearGradient(gradient),
                shape = CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun RepliesBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReplyIcon(
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
        // Curved reply arrow
        val startX = size.width * 0.75f
        val startY = size.height * 0.7f
        val midX = size.width * 0.25f
        val midY = size.height * 0.7f
        val endX = size.width * 0.25f
        val endY = size.height * 0.3f
        // Horizontal line
        drawLine(resolvedColor, Offset(startX, startY), Offset(midX, midY), stroke, cap = StrokeCap.Round)
        // Vertical line
        drawLine(resolvedColor, Offset(midX, midY), Offset(endX, endY), stroke, cap = StrokeCap.Round)
        // Arrow head
        val arrowSize = size.width * 0.15f
        drawLine(resolvedColor, Offset(endX, endY), Offset(endX - arrowSize, endY + arrowSize), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(endX, endY), Offset(endX + arrowSize, endY + arrowSize), stroke, cap = StrokeCap.Round)
    }
}

private fun visibleThreadReplies(
    replies: List<ThreadReplyItem>,
    expandedReplyIds: Set<String>
): List<ThreadReplyItem> {
    if (replies.isEmpty()) return emptyList()

    val repliesById = replies.associateBy { it.post.id }
    return replies.filter { reply ->
        if (reply.depth == 0) return@filter true
        isReplyVisible(
            reply = reply,
            repliesById = repliesById,
            expandedReplyIds = expandedReplyIds
        )
    }
}

private fun isReplyVisible(
    reply: ThreadReplyItem,
    repliesById: Map<String, ThreadReplyItem>,
    expandedReplyIds: Set<String>
): Boolean {
    var currentParentId = reply.post.parentId ?: return false

    while (true) {
        val parent = repliesById[currentParentId] ?: return false
        if (parent.post.id !in expandedReplyIds) {
            return false
        }
        if (parent.depth == 0) {
            return true
        }
        currentParentId = parent.post.parentId ?: return false
    }
}

private fun toggleReplyExpansion(
    currentExpandedReplyIds: Set<String>,
    replyId: String
): Set<String> {
    return if (replyId in currentExpandedReplyIds) {
        currentExpandedReplyIds - replyId
    } else {
        currentExpandedReplyIds + replyId
    }
}

@Composable
private fun ExpandCollapseChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.18f
        val left = size.width * 0.2f
        val right = size.width * 0.8f
        val centerX = size.width * 0.5f
        val top = size.height * 0.3f
        val bottom = size.height * 0.7f

        if (expanded) {
            drawLine(
                color = resolvedColor,
                start = Offset(left, bottom),
                end = Offset(centerX, top),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = resolvedColor,
                start = Offset(centerX, top),
                end = Offset(right, bottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        } else {
            drawLine(
                color = resolvedColor,
                start = Offset(left, top),
                end = Offset(centerX, bottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = resolvedColor,
                start = Offset(centerX, bottom),
                end = Offset(right, top),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

private val previewAuthor = PostAuthor(
    id = "1",
    displayName = "Connor"
)

private val previewPost = Post(
    id = "1",
    content = "Hello, this is a sample post for preview purposes!",
    createdAt = 1700000000000L,
    updatedAt = 1700000000000L,
    author = previewAuthor,
    stats = PostStats(replyCount = 2, likeCount = 5, viewCount = 100)
)

private val previewReplies = listOf(
    ThreadReplyItem(
        post = Post(
            id = "2",
            content = "Great post!",
            parentId = "1",
            createdAt = 1700001000000L,
            updatedAt = 1700001000000L,
            author = PostAuthor(id = "2", displayName = "Alice"),
            stats = PostStats(replyCount = 1, likeCount = 1, viewCount = 10)
        ),
        depth = 0
    ),
    ThreadReplyItem(
        post = Post(
            id = "3",
            content = "Thanks for sharing.",
            parentId = "2",
            createdAt = 1700002000000L,
            updatedAt = 1700002000000L,
            author = PostAuthor(id = "3", displayName = "Bob"),
            stats = PostStats(replyCount = 0, likeCount = 0, viewCount = 5)
        ),
        depth = 1
    )
)

@Preview
@Composable
private fun PostDetailScreenPreview() {
    KwitterTheme(darkTheme = false) {
        PostDetailScreen(
            state = PostDetailUiState(
                post = previewPost,
                threadReplies = previewReplies
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun PostDetailScreenLoadingPreview() {
    KwitterTheme(darkTheme = true) {
        PostDetailScreen(
            state = PostDetailUiState(isLoading = true),
            onAction = {}
        )
    }
}
