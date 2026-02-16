package com.connor.kwitter.features.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.PostItem
import com.connor.kwitter.features.glass.NativeTopBarAction
import com.connor.kwitter.features.glass.NativeTopBarButtonAction
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.rememberNativeTopBarController
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.user.model.UserListItem
import kotlinx.coroutines.flow.Flow
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.profile_follow
import kwitter.composeapp.generated.resources.profile_following
import kwitter.composeapp.generated.resources.search_empty_hint
import kwitter.composeapp.generated.resources.search_no_results
import kwitter.composeapp.generated.resources.search_placeholder
import kwitter.composeapp.generated.resources.search_posts
import kwitter.composeapp.generated.resources.search_replies
import kwitter.composeapp.generated.resources.search_sort_best_match
import kwitter.composeapp.generated.resources.search_sort_latest
import kwitter.composeapp.generated.resources.search_users
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    postsPaging: Flow<PagingData<Post>>,
    repliesPaging: Flow<PagingData<Post>>,
    usersPaging: Flow<PagingData<UserListItem>>,
    onAction: (SearchIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var queryText by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val nativeTopBarController = rememberNativeTopBarController()
    val nativeSearchPlaceholder = stringResource(Res.string.search_placeholder)

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(SearchAction.ErrorDismissed)
        }
    }

    LaunchedEffect(nativeTopBarController) {
        if (nativeTopBarController == null) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(nativeTopBarController, nativeSearchPlaceholder) {
        nativeTopBarController?.setModel(
            NativeTopBarModel.Search(
                query = queryText,
                placeholder = nativeSearchPlaceholder,
                leadingButton = NativeTopBarButtons.back()
            )
        )
    }

    LaunchedEffect(nativeTopBarController) {
        nativeTopBarController?.actionEvents?.collect { action ->
            when (action) {
                is NativeTopBarAction.ButtonClicked -> {
                    if (action.action == NativeTopBarButtonAction.Back) {
                        onAction(SearchNavAction.BackClick)
                    }
                }

                is NativeTopBarAction.SearchQueryChanged -> {
                    queryText = action.query
                }

                NativeTopBarAction.SearchSubmitted -> {
                    onAction(SearchAction.Search(queryText))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarController = nativeTopBarController) {
                SearchTopBar(
                    queryText = queryText,
                    onQueryChange = { queryText = it },
                    onSearch = { onAction(SearchAction.Search(queryText)) },
                    onBackClick = { onAction(SearchNavAction.BackClick) },
                    focusRequester = focusRequester
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInsetPadding)
        ) {
            Spacer(modifier = Modifier.height(topOverlayPadding))

            if (state.hasSearched) {
                SearchTabRow(
                    selectedTab = state.selectedTab,
                    onTabSelected = { onAction(SearchAction.SelectTab(it)) }
                )

                if (state.selectedTab != SearchTab.USERS) {
                    SortChips(
                        selectedSort = state.sortOrder,
                        onSortSelected = { onAction(SearchAction.SetSortOrder(it)) }
                    )
                }

                when (state.selectedTab) {
                    SearchTab.POSTS -> PostPagingList(
                        pagingFlow = postsPaging,
                        onAction = onAction
                    )
                    SearchTab.REPLIES -> PostPagingList(
                        pagingFlow = repliesPaging,
                        onAction = onAction
                    )
                    SearchTab.USERS -> UserPagingList(
                        pagingFlow = usersPaging,
                        onAction = onAction
                    )
                }
            } else {
                SearchEmptyHint()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    queryText: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBackClick: () -> Unit,
    focusRequester: FocusRequester
) {
    GlassTopBar {
        CenterAlignedTopAppBar(
            title = {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(focusRequester)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTabRow(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit
) {
    val tabs = SearchTab.entries
    val tabLabels = listOf(
        stringResource(Res.string.search_posts),
        stringResource(Res.string.search_replies),
        stringResource(Res.string.search_users)
    )

    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            )
        }
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tabLabels[index],
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SortChips(
    selectedSort: String,
    onSortSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedSort == "best_match",
            onClick = { onSortSelected("best_match") },
            label = { Text(stringResource(Res.string.search_sort_best_match)) }
        )
        FilterChip(
            selected = selectedSort == "latest",
            onClick = { onSortSelected("latest") },
            label = { Text(stringResource(Res.string.search_sort_latest)) }
        )
    }
}

@Composable
private fun PostPagingList(
    pagingFlow: Flow<PagingData<Post>>,
    onAction: (SearchIntent) -> Unit
) {
    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val refreshState = lazyPagingItems.loadState.refresh

    when {
        refreshState is LoadState.Loading && lazyPagingItems.itemCount == 0 -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        refreshState is LoadState.Error && lazyPagingItems.itemCount == 0 -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = refreshState.error.message ?: "Search failed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        refreshState is LoadState.NotLoading && lazyPagingItems.itemCount == 0 -> {
            SearchNoResults()
        }

        else -> {
            PostPagingContent(lazyPagingItems, onAction)
        }
    }
}

@Composable
private fun PostPagingContent(
    lazyPagingItems: LazyPagingItems<Post>,
    onAction: (SearchIntent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.id }
        ) { index ->
            val post = lazyPagingItems[index] ?: return@items
            PostItem(
                post = post,
                onClick = { onAction(SearchNavAction.PostClick(post.id)) },
                onLikeClick = {
                    onAction(
                        SearchAction.ToggleLike(
                            postId = post.id,
                            isCurrentlyLiked = post.isLikedByCurrentUser == true,
                            currentLikeCount = post.stats.likeCount
                        )
                    )
                },
                onBookmarkClick = {
                    onAction(
                        SearchAction.ToggleBookmark(
                            postId = post.id,
                            isCurrentlyBookmarked = post.isBookmarkedByCurrentUser == true
                        )
                    )
                },
                onMediaClick = { index ->
                    onAction(SearchNavAction.MediaClick(post.media, index))
                },
                onAuthorClick = {
                    onAction(SearchNavAction.AuthorClick(post.author.id))
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

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UserPagingList(
    pagingFlow: Flow<PagingData<UserListItem>>,
    onAction: (SearchIntent) -> Unit
) {
    val lazyPagingItems = pagingFlow.collectAsLazyPagingItems()
    val refreshState = lazyPagingItems.loadState.refresh

    when {
        refreshState is LoadState.Loading && lazyPagingItems.itemCount == 0 -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        refreshState is LoadState.Error && lazyPagingItems.itemCount == 0 -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = refreshState.error.message ?: "Search failed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        refreshState is LoadState.NotLoading && lazyPagingItems.itemCount == 0 -> {
            SearchNoResults()
        }

        else -> {
            UserPagingContent(lazyPagingItems, onAction)
        }
    }
}

@Composable
private fun UserPagingContent(
    lazyPagingItems: LazyPagingItems<UserListItem>,
    onAction: (SearchIntent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(
            count = lazyPagingItems.itemCount,
            key = lazyPagingItems.itemKey { it.id }
        ) { index ->
            val user = lazyPagingItems[index] ?: return@items
            SearchUserRow(
                user = user,
                onClick = { onAction(SearchNavAction.UserClick(user.id)) },
                onFollowClick = {
                    onAction(
                        SearchAction.ToggleFollow(
                            targetUserId = user.id,
                            isCurrentlyFollowing = user.isFollowedByCurrentUser == true
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

@Composable
private fun SearchUserRow(
    user: UserListItem,
    onClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchUserAvatar(
            name = user.displayName,
            avatarUrl = user.avatarUrl,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (user.bio.isNotBlank()) {
                Text(
                    text = user.bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (user.isFollowedByCurrentUser != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val isFollowing = user.isFollowedByCurrentUser == true
            if (isFollowing) {
                OutlinedButton(
                    onClick = onFollowClick,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.profile_following),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = onFollowClick,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.profile_follow),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchUserAvatar(
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
            model = avatarUrl,
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

@Composable
private fun SearchEmptyHint() {
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
                SearchIcon(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(Res.string.search_empty_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun SearchNoResults() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.search_no_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun SearchIcon(
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
        val cx = size.width * 0.42f
        val cy = size.height * 0.42f
        val radius = size.minDimension * 0.28f

        drawCircle(
            color = resolvedColor,
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        val handleStart = androidx.compose.ui.geometry.Offset(
            cx + radius * 0.7f,
            cy + radius * 0.7f
        )
        val handleEnd = androidx.compose.ui.geometry.Offset(
            size.width * 0.85f,
            size.height * 0.85f
        )
        drawLine(
            color = resolvedColor,
            start = handleStart,
            end = handleEnd,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
