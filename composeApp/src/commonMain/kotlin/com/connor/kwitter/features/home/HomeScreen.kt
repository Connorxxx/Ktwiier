package com.connor.kwitter.features.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.theme.LocalIsDarkTheme
import com.connor.kwitter.core.ui.PostItem
import com.connor.kwitter.features.glass.NativeTopBarAction
import com.connor.kwitter.features.glass.getNativeTopBarController
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostAuthor
import com.connor.kwitter.domain.post.model.PostStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.home_empty
import org.jetbrains.compose.resources.stringResource

private val NativeHomeTopBarHeight = 116.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    pagingFlow: Flow<PagingData<Post>>,
    onAction: (HomeIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val nativeTopBarController = remember { getNativeTopBarController() }
    val onProfileClick = state.currentUserId?.let { userId ->
        { onAction(HomeNavAction.AuthorClick(userId)) }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(HomeAction.ErrorDismissed)
        }
    }

    LaunchedEffect(lazyPagingItems.loadState.refresh) {
        val refreshState = lazyPagingItems.loadState.refresh
        if (refreshState is LoadState.Error && lazyPagingItems.itemCount > 0) {
            snackbarHostState.showSnackbar(
                refreshState.error.message ?: "Failed to refresh timeline"
            )
        }
    }

    LaunchedEffect(nativeTopBarController, state.currentUserId) {
        nativeTopBarController?.actionEvents?.collect { action ->
            when (action) {
                NativeTopBarAction.CreatePost -> {
                    onAction(HomeNavAction.CreatePostClick)
                }

                NativeTopBarAction.Profile -> {
                    state.currentUserId?.let { userId ->
                        onAction(HomeNavAction.AuthorClick(userId))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (nativeTopBarController == null) {
                HomeTopBar(
                    onCreatePostClick = { onAction(HomeNavAction.CreatePostClick) },
                    onProfileClick = onProfileClick
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NativeHomeTopBarHeight)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val refreshState = lazyPagingItems.loadState.refresh
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()

        PullToRefreshBox(
            isRefreshing = refreshState is LoadState.Loading,
            onRefresh = { lazyPagingItems.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInsetPadding)
        ) {
            when (refreshState) {
                is LoadState.Loading if lazyPagingItems.itemCount == 0 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is LoadState.Error if lazyPagingItems.itemCount == 0 -> {
                    TimelineLoadErrorState(
                        message = refreshState.error.message ?: "Failed to load timeline",
                        onRetry = { lazyPagingItems.refresh() }
                    )
                }

                is LoadState.NotLoading if lazyPagingItems.itemCount == 0 -> {
                    EmptyTimelineState()
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = topOverlayPadding + 8.dp,
                                end = 16.dp,
                                bottom = bottomInsetPadding + 8.dp
                            ),
                            overscrollEffect = null
                        ) {
                            items(
                                count = lazyPagingItems.itemCount,
                                key = lazyPagingItems.itemKey { it.id }
                            ) { index ->
                                val post = lazyPagingItems[index] ?: return@items
                                PostItem(
                                    post = post,
                                    onClick = { onAction(HomeNavAction.PostClick(post.id)) },
                                    onLikeClick = {
                                        onAction(
                                            HomeAction.ToggleLike(
                                                postId = post.id,
                                                isCurrentlyLiked = post.isLikedByCurrentUser == true,
                                                currentLikeCount = post.stats.likeCount
                                            )
                                        )
                                    },
                                    onBookmarkClick = {
                                        onAction(
                                            HomeAction.ToggleBookmark(
                                                postId = post.id,
                                                isCurrentlyBookmarked = post.isBookmarkedByCurrentUser == true
                                            )
                                        )
                                    },
                                    onMediaClick = { mediaIndex ->
                                        onAction(HomeNavAction.MediaClick(post.media, mediaIndex))
                                    },
                                    onAuthorClick = {
                                        onAction(HomeNavAction.AuthorClick(post.author.id))
                                    }
                                )
                            }

                            if (lazyPagingItems.loadState.append is LoadState.Loading) {
                                item {
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

                        NewPostsBanner(
                            count = state.newPostCount,
                            onClick = {
                                onAction(HomeAction.NewPostsBannerClick)
                                lazyPagingItems.refresh()
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topOverlayPadding + 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPostsBanner(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = count > 0,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Text(
                text = if (count == 1) "1 new post" else "$count new posts",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    onCreatePostClick: () -> Unit,
    onProfileClick: (() -> Unit)?
) {
    val topBarShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

    GlassTopBar(
        shape = topBarShape
    ) {
        CenterAlignedTopAppBar(
            title = {
                HomeTopBarTitle()
            },
            navigationIcon = {
                Box(modifier = Modifier.padding(start = 14.dp)) {
                    ProfilePlaceholder(onClick = onProfileClick)
                }
            },
            actions = {
                Box(modifier = Modifier.padding(end = 14.dp)) {
                    CreatePostButton(onClick = onCreatePostClick)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun HomeTopBarTitle() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KwitterLogo(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Post",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CreatePostButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val containerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.92f else 1f)
    val contentColor = if (isDark) {
        Color.Black.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val strokeColor = if (isDark) {
        Color.White.copy(alpha = 0.24f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor, CircleShape)
            .border(1.dp, strokeColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        PlusIcon(
            modifier = Modifier.size(18.dp),
            color = contentColor
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
private fun TimelineLoadErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Timeline load failed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ProfilePlaceholder(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isEnabled = onClick != null
    val backgroundBrush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainer
        )
    )
    val innerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 0.2f else 0.1f)

    Box(
        modifier = modifier
            .size(40.dp)
            .background(
                brush = backgroundBrush,
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isEnabled) 0.72f else 0.45f),
                shape = CircleShape
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color = innerColor, shape = CircleShape)
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(16.dp)
                    .height(8.dp)
                    .background(
                        color = innerColor,
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 6.dp,
                            bottomEnd = 6.dp
                        )
                    )
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
        drawLine(
            resolvedColor,
            Offset(left, top),
            Offset(left, bottom),
            stroke,
            cap = StrokeCap.Round
        )
        // Upper diagonal
        drawLine(
            resolvedColor,
            Offset(left, midY),
            Offset(right, top),
            stroke,
            cap = StrokeCap.Round
        )
        // Lower diagonal
        drawLine(
            resolvedColor,
            Offset(left, midY),
            Offset(right, bottom),
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
        author = PostAuthor(id = "1", displayName = "Connor"),
        stats = PostStats(replyCount = 5, likeCount = 12, viewCount = 200)
    ),
    Post(
        id = "2",
        content = "Anyone else working on KMP projects? Would love to hear about your experience with Compose Multiplatform.",
        createdAt = 1700001000000L,
        updatedAt = 1700001000000L,
        author = PostAuthor(id = "2", displayName = "Alice"),
        stats = PostStats(replyCount = 0, likeCount = 3, viewCount = 50)
    ),
    Post(
        id = "3",
        content = "Good morning!",
        createdAt = 1700002000000L,
        updatedAt = 1700002000000L,
        author = PostAuthor(id = "3", displayName = "Bob"),
        stats = PostStats(replyCount = 2, likeCount = 1, viewCount = 30)
    )
)

@Preview
@Composable
private fun HomeScreenPreview() {
    KwitterTheme(darkTheme = false) {
        HomeScreen(
            state = HomeUiState(),
            pagingFlow = flowOf(PagingData.from(previewPosts)),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun HomeScreenDarkPreview() {
    KwitterTheme(darkTheme = true) {
        HomeScreen(
            state = HomeUiState(),
            pagingFlow = flowOf(PagingData.from(previewPosts)),
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
            pagingFlow = flowOf(PagingData.empty()),
            onAction = {}
        )
    }
}
