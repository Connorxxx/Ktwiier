package com.connor.kwitter.features.postdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.util.formatPostTime
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostAuthor
import com.connor.kwitter.domain.post.model.PostStats
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.post_detail_no_replies
import kwitter.composeapp.generated.resources.post_detail_replies_header
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    state: PostDetailUiState,
    onAction: (PostDetailIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(PostDetailAction.ErrorDismissed)
        }
    }

    Scaffold(
        topBar = {
            ThreadTopBar(
                replyCount = state.replies.size,
                onBackClick = { onAction(PostDetailNavAction.BackClick) }
            )
        },
        floatingActionButton = {
            state.post?.let { post ->
                FloatingActionButton(
                    onClick = { onAction(PostDetailNavAction.ReplyClick(post.id)) },
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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.post != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    item {
                        RootPostItem(post = state.post)
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
                            if (state.replies.isNotEmpty()) {
                                RepliesBadge(text = state.replies.size.toString())
                            }
                        }
                    }

                    if (state.replies.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.post_detail_no_replies),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    } else {
                        itemsIndexed(state.replies, key = { _, reply -> reply.id }) { index, reply ->
                            ReplyItem(
                                reply = reply,
                                showTimelineConnector = index != state.replies.lastIndex
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
    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Conversation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (replyCount > 0) "$replyCount replies" else "Start the first reply",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    BackArrowIcon(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        )
    }
}

@Composable
private fun RootPostItem(post: Post) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        ThreadAvatar(
            name = post.authorName,
            size = 46.dp,
            gradient = listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            ),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RepliesBadge(text = "${post.replyCount}")
                Text(
                    text = "replies",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReplyItem(
    reply: Post,
    showTimelineConnector: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ThreadAvatar(
                name = reply.authorName,
                size = 36.dp,
                gradient = listOf(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer
                ),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            if (showTimelineConnector) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(1.dp)
                        .height(54.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp, bottom = 10.dp)
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

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun ThreadAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    gradient: List<Color>,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .background(
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
private fun BackArrowIcon(
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
        val centerY = size.height * 0.5f
        val left = size.width * 0.15f
        val right = size.width * 0.85f
        val arrowSize = size.width * 0.25f
        // Horizontal line
        drawLine(resolvedColor, Offset(left, centerY), Offset(right, centerY), stroke, cap = StrokeCap.Round)
        // Arrow head
        drawLine(resolvedColor, Offset(left, centerY), Offset(left + arrowSize, centerY - arrowSize), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, Offset(left, centerY), Offset(left + arrowSize, centerY + arrowSize), stroke, cap = StrokeCap.Round)
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

private val previewAuthor = PostAuthor(
    id = "1",
    displayName = "Connor",
    email = "connor@example.com"
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
    Post(
        id = "2",
        content = "Great post!",
        parentId = "1",
        createdAt = 1700001000000L,
        updatedAt = 1700001000000L,
        author = PostAuthor(id = "2", displayName = "Alice", email = "alice@example.com"),
        stats = PostStats(replyCount = 0, likeCount = 1, viewCount = 10)
    ),
    Post(
        id = "3",
        content = "Thanks for sharing.",
        parentId = "1",
        createdAt = 1700002000000L,
        updatedAt = 1700002000000L,
        author = PostAuthor(id = "3", displayName = "Bob", email = "bob@example.com"),
        stats = PostStats(replyCount = 0, likeCount = 0, viewCount = 5)
    )
)

@Preview
@Composable
private fun PostDetailScreenPreview() {
    KwitterTheme(darkTheme = false) {
        PostDetailScreen(
            state = PostDetailUiState(
                post = previewPost,
                replies = previewReplies
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
