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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.connor.kwitter.core.result.errorOrNull
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.ErrorScreen
import com.connor.kwitter.core.ui.ErrorStateCard
import com.connor.kwitter.core.ui.LoadingScreen
import com.connor.kwitter.core.theme.LocalIsDarkTheme
import com.connor.kwitter.core.ui.PostItem
import com.connor.kwitter.core.util.resolveBackendUrl
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.PublishNativeTopBar
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostAuthor
import com.connor.kwitter.domain.post.model.PostStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.home_empty
import kwitter.composeapp.generated.resources.home_load_failed
import kwitter.composeapp.generated.resources.home_new_post_multiple
import kwitter.composeapp.generated.resources.home_new_post_single
import kwitter.composeapp.generated.resources.home_refresh_failed
import kwitter.composeapp.generated.resources.home_top_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    pagingFlow: Flow<PagingData<Post>>,
    useNativeTopBar: Boolean = false,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (HomeIntent) -> Unit
) {
    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val refreshFailedText = stringResource(Res.string.home_refresh_failed)
    val loadFailedText = stringResource(Res.string.home_load_failed)
    val operationErrorMessage = state.operationResult.errorOrNull()
    val refreshErrorMessage = (lazyPagingItems.loadState.refresh as? LoadState.Error)
        ?.error
        ?.message
        ?.takeIf { lazyPagingItems.itemCount > 0 }
        ?: if (lazyPagingItems.loadState.refresh is LoadState.Error && lazyPagingItems.itemCount > 0) {
            refreshFailedText
        } else {
            null
        }

    val activeVideoPostKey by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - viewportCenter) }
                ?.key
        }
    }
    val onProfileClick = state.currentUserId?.let { userId ->
        { onAction(HomeNavAction.AuthorClick(userId)) }
    }

    PublishNativeTopBar(
        onNativeTopBarModel,
        NativeTopBarModel.HomeInteractive(
            avatarUrl = state.currentUserAvatarUrl?.trim()?.ifBlank { null }
        )
    )

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarEnabled = useNativeTopBar) {
                HomeTopBar(
                    avatarUrl = state.currentUserAvatarUrl,
                    onCreatePostClick = { onAction(HomeNavAction.CreatePostClick) },
                    onProfileClick = onProfileClick
                )
            }
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
                .padding(bottom = bottomInsetPadding)
        ) {
            PullToRefreshBox(
                isRefreshing = refreshState is LoadState.Loading,
                onRefresh = { lazyPagingItems.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when (refreshState) {
                    is LoadState.Loading if lazyPagingItems.itemCount == 0 -> {
                        LoadingScreen(
                            contentPadding = PaddingValues(
                                top = topOverlayPadding,
                                bottom = bottomInsetPadding
                            )
                        )
                    }

                    is LoadState.Error if lazyPagingItems.itemCount == 0 -> {
                        ErrorScreen(
                            message = refreshState.error.message ?: loadFailedText,
                            contentPadding = PaddingValues(
                                top = topOverlayPadding,
                                bottom = bottomInsetPadding
                            ),
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
                                        },
                                        isVideoPlaying = activeVideoPostKey == post.id
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

            if (operationErrorMessage != null || refreshErrorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
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
                            onDismiss = { onAction(HomeAction.ErrorDismissed) }
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
                text = if (count == 1) {
                    stringResource(Res.string.home_new_post_single)
                } else {
                    stringResource(Res.string.home_new_post_multiple, count)
                },
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
    avatarUrl: String?,
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
                    HomeProfileAvatar(
                        avatarUrl = avatarUrl,
                        onClick = onProfileClick
                    )
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
        Text(
            text = stringResource(Res.string.home_top_title),
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
private fun HomeProfileAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isEnabled = onClick != null
    val avatarModifier = modifier
        .size(40.dp)
        .clip(CircleShape)
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isEnabled) 0.72f else 0.45f),
            shape = CircleShape
        )
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick)
            else Modifier
        )

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = resolveBackendUrl(avatarUrl),
            contentDescription = "Home profile avatar",
            contentScale = ContentScale.Crop,
            modifier = avatarModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    } else {
        val backgroundBrush = Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainer
            )
        )
        val innerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 0.2f else 0.1f)

        Box(
            modifier = avatarModifier.background(
                brush = backgroundBrush,
                shape = CircleShape
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
