package com.connor.kwitter.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextAlign
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
import kwitter.composeapp.generated.resources.home_empty
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(HomeAction.ErrorDismissed)
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(onLogoutClick = { onAction(HomeAction.LogoutClick) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(HomeNavAction.CreatePostClick) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                PlusIcon(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(HomeAction.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.posts.isEmpty() -> {
                    EmptyTimelineState()
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(state.posts, key = { it.id }) { post ->
                            PostItem(
                                post = post,
                                onClick = { onAction(HomeNavAction.PostClick(post.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onLogoutClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CenterAlignedTopAppBar(
            title = {
                KwitterLogo(
                    modifier = Modifier.size(30.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            navigationIcon = {
                Box(modifier = Modifier.padding(start = 12.dp)) {
                    ProfilePlaceholder()
                }
            },
            actions = {
                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    LogoutIcon(
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
private fun EmptyTimelineState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                PlusIcon(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(Res.string.home_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun ProfilePlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainer
                    )
                ),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun PostItem(
    post: Post,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            AuthorAvatar(name = post.authorName)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis
                )

                if (post.replyCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${post.replyCount} replies",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
        )
    }
}

@Composable
private fun AuthorAvatar(
    name: String,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )

    Box(
        modifier = modifier
            .size(44.dp)
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
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun KwitterLogo(
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
        val left = size.width * 0.25f
        val top = size.height * 0.1f
        val bottom = size.height * 0.9f
        val midY = size.height * 0.5f
        val right = size.width * 0.78f
        // Vertical stroke
        drawLine(resolvedColor, Offset(left, top), Offset(left, bottom), stroke, cap = StrokeCap.Round)
        // Upper diagonal
        drawLine(resolvedColor, Offset(left, midY), Offset(right, top), stroke, cap = StrokeCap.Round)
        // Lower diagonal
        drawLine(resolvedColor, Offset(left, midY), Offset(right, bottom), stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun LogoutIcon(
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
        // Door frame (right side open)
        val left = size.width * 0.3f
        val top = size.height * 0.15f
        val bottom = size.height * 0.85f
        val right = size.width * 0.75f
        // Top line
        drawLine(resolvedColor, Offset(left, top), Offset(right, top), stroke, cap = StrokeCap.Round)
        // Bottom line
        drawLine(resolvedColor, Offset(left, bottom), Offset(right, bottom), stroke, cap = StrokeCap.Round)
        // Left line
        drawLine(resolvedColor, Offset(left, top), Offset(left, bottom), stroke, cap = StrokeCap.Round)
        // Arrow line (horizontal)
        val arrowY = size.height * 0.5f
        val arrowLeft = size.width * 0.1f
        val arrowRight = size.width * 0.65f
        drawLine(resolvedColor, Offset(arrowLeft, arrowY), Offset(arrowRight, arrowY), stroke, cap = StrokeCap.Round)
        // Arrow head
        val arrowSize = size.width * 0.15f
        drawLine(
            resolvedColor,
            Offset(arrowLeft, arrowY),
            Offset(arrowLeft + arrowSize, arrowY - arrowSize),
            stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            resolvedColor,
            Offset(arrowLeft, arrowY),
            Offset(arrowLeft + arrowSize, arrowY + arrowSize),
            stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlusIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val margin = size.minDimension * 0.2f
        // Horizontal line
        drawLine(
            resolvedColor,
            Offset(margin, center.y),
            Offset(size.width - margin, center.y),
            stroke,
            cap = StrokeCap.Round
        )
        // Vertical line
        drawLine(
            resolvedColor,
            Offset(center.x, margin),
            Offset(center.x, size.height - margin),
            stroke,
            cap = StrokeCap.Round
        )
    }
}

private val previewPosts = listOf(
    Post(
        id = "1",
        content = "Just shipped a new feature for the app! Really excited about how the UI turned out after all the iterations.",
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L,
        author = PostAuthor(id = "1", displayName = "Connor", email = "connor@example.com"),
        stats = PostStats(replyCount = 5, likeCount = 12, viewCount = 200)
    ),
    Post(
        id = "2",
        content = "Anyone else working on KMP projects? Would love to hear about your experience with Compose Multiplatform.",
        createdAt = 1700001000000L,
        updatedAt = 1700001000000L,
        author = PostAuthor(id = "2", displayName = "Alice", email = "alice@example.com"),
        stats = PostStats(replyCount = 0, likeCount = 3, viewCount = 50)
    ),
    Post(
        id = "3",
        content = "Good morning!",
        createdAt = 1700002000000L,
        updatedAt = 1700002000000L,
        author = PostAuthor(id = "3", displayName = "Bob", email = "bob@example.com"),
        stats = PostStats(replyCount = 2, likeCount = 1, viewCount = 30)
    )
)

@Preview
@Composable
private fun HomeScreenPreview() {
    KwitterTheme(darkTheme = false) {
        HomeScreen(
            state = HomeUiState(posts = previewPosts),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun HomeScreenDarkPreview() {
    KwitterTheme(darkTheme = true) {
        HomeScreen(
            state = HomeUiState(posts = previewPosts),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun HomeScreenEmptyPreview() {
    KwitterTheme(darkTheme = false) {
        HomeScreen(
            state = HomeUiState(),
            onAction = {}
        )
    }
}
