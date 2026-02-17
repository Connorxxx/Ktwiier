package com.connor.kwitter.features.userprofile

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.connor.kwitter.core.theme.KwitterTheme
import com.connor.kwitter.core.ui.EditPenIcon
import com.connor.kwitter.core.ui.GlassTopBar
import com.connor.kwitter.core.ui.GlassTopBarBackButton
import com.connor.kwitter.core.ui.GlassTopBarIconContentColor
import com.connor.kwitter.core.ui.GlassTopBarIconButton
import com.connor.kwitter.core.ui.GlassTopBarInnerIconSize
import com.connor.kwitter.core.ui.GlassTopBarTitle
import com.connor.kwitter.core.ui.PostItem
import com.connor.kwitter.features.glass.NativeTopBarAction
import com.connor.kwitter.features.glass.NativeTopBarButtonAction
import com.connor.kwitter.features.glass.NativeTopBarButtons
import com.connor.kwitter.features.glass.NativeTopBarModel
import com.connor.kwitter.features.glass.NativeTopBarSlot
import com.connor.kwitter.features.glass.rememberNativeTopBarController
import com.connor.kwitter.domain.post.model.Post
import com.connor.kwitter.domain.post.model.PostAuthor
import com.connor.kwitter.domain.post.model.PostStats
import com.connor.kwitter.domain.user.model.UserProfile
import com.connor.kwitter.domain.user.model.UserStats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kwitter.composeapp.generated.resources.Res
import kwitter.composeapp.generated.resources.profile_edit
import kwitter.composeapp.generated.resources.profile_follow
import kwitter.composeapp.generated.resources.profile_followers
import kwitter.composeapp.generated.resources.profile_following
import kwitter.composeapp.generated.resources.profile_joined
import kwitter.composeapp.generated.resources.profile_likes
import kwitter.composeapp.generated.resources.profile_no_likes
import kwitter.composeapp.generated.resources.profile_no_posts
import kwitter.composeapp.generated.resources.profile_no_replies
import kwitter.composeapp.generated.resources.profile_posts
import kwitter.composeapp.generated.resources.profile_replies
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    state: UserProfileUiState,
    onNativeTopBarModel: (NativeTopBarModel) -> Unit = {},
    onAction: (UserProfileIntent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val nativeTopBarController = rememberNativeTopBarController()

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onAction(UserProfileAction.ErrorDismissed)
        }
    }

    LaunchedEffect(onNativeTopBarModel, state.profile?.displayName, state.isOwnProfile) {
        onNativeTopBarModel(
            NativeTopBarModel.Title(
                title = state.profile?.displayName.orEmpty(),
                leadingButton = NativeTopBarButtons.back(),
                trailingButton = if (state.isOwnProfile) NativeTopBarButtons.edit() else null
            )
        )
    }

    LaunchedEffect(nativeTopBarController) {
        nativeTopBarController?.actionEvents?.collect { action ->
            if (action !is NativeTopBarAction.ButtonClicked) return@collect
            when (action.action) {
                NativeTopBarButtonAction.Back -> onAction(UserProfileNavAction.BackClick)
                NativeTopBarButtonAction.Edit -> onAction(UserProfileNavAction.EditProfileClick)
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            NativeTopBarSlot(nativeTopBarController = nativeTopBarController) {
                ProfileTopBar(
                    displayName = state.profile?.displayName ?: "",
                    isOwnProfile = state.isOwnProfile,
                    onBackClick = { onAction(UserProfileNavAction.BackClick) },
                    onEditClick = { onAction(UserProfileNavAction.EditProfileClick) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val topOverlayPadding = paddingValues.calculateTopPadding()
        val bottomInsetPadding = paddingValues.calculateBottomPadding()

        when {
            state.isLoadingProfile && state.profile == null -> {
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

            state.profile != null -> {
                val currentTabPosts = when (state.selectedTab) {
                    ProfileTab.POSTS -> state.posts
                    ProfileTab.REPLIES -> state.replies
                    ProfileTab.LIKES -> state.likes
                }
                val listState = rememberLazyListState()

                // Infinite scroll detection
                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            ?: return@derivedStateOf false
                        lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
                    }
                }
                LaunchedEffect(state.selectedTab) {
                    snapshotFlow { shouldLoadMore.value }
                        .collect { shouldLoad ->
                            if (shouldLoad && !state.isLoadingMore && !state.isLoadingTab) {
                                onAction(UserProfileAction.LoadMore)
                            }
                        }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = topOverlayPadding,
                        end = 16.dp,
                        bottom = bottomInsetPadding
                    )
                ) {
                    // Profile Header
                    item(key = "profile_header") {
                        ProfileHeader(
                            profile = state.profile,
                            isOwnProfile = state.isOwnProfile,
                            isFollowLoading = state.isFollowLoading,
                            onEditClick = { onAction(UserProfileNavAction.EditProfileClick) },
                            onFollowClick = { onAction(UserProfileAction.ToggleFollow) },
                            onFollowingClick = { onAction(UserProfileNavAction.FollowingClick) },
                            onFollowersClick = { onAction(UserProfileNavAction.FollowersClick) },
                            onMessageClick = {
                                onAction(
                                    UserProfileNavAction.MessageClick(
                                        userId = state.profile.id,
                                        displayName = state.profile.displayName
                                    )
                                )
                            }
                        )
                    }

                    // Tab Row
                    item(key = "tab_row") {
                        ProfileTabRow(
                            selectedTab = state.selectedTab,
                            onTabSelected = { tab ->
                                onAction(UserProfileAction.SelectTab(tab))
                            }
                        )
                    }

                    // Tab loading state
                    if (state.isLoadingTab) {
                        item(key = "tab_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (currentTabPosts.isEmpty()) {
                        item(key = "empty_tab") {
                            EmptyTabState(tab = state.selectedTab)
                        }
                    } else {
                        items(currentTabPosts, key = { it.id }) { post ->
                            PostItem(
                                post = post,
                                onClick = {
                                    onAction(UserProfileNavAction.PostClick(post.id))
                                },
                                onLikeClick = {
                                    onAction(
                                        UserProfileAction.ToggleLike(
                                            postId = post.id,
                                            isCurrentlyLiked = post.isLikedByCurrentUser == true,
                                            currentLikeCount = post.stats.likeCount
                                        )
                                    )
                                },
                                onBookmarkClick = {
                                    onAction(
                                        UserProfileAction.ToggleBookmark(
                                            postId = post.id,
                                            isCurrentlyBookmarked = post.isBookmarkedByCurrentUser == true
                                        )
                                    )
                                },
                                onMediaClick = { index ->
                                    onAction(
                                        UserProfileNavAction.MediaClick(post.media, index)
                                    )
                                },
                                onAuthorClick = {
                                    onAction(
                                        UserProfileNavAction.AuthorClick(post.author.id)
                                    )
                                }
                            )
                        }

                        if (state.isLoadingMore) {
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

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(
    displayName: String,
    isOwnProfile: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit
) {
    GlassTopBar {
        val actionIconColor = GlassTopBarIconContentColor()
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
            actions = {
                if (isOwnProfile) {
                    GlassTopBarIconButton(
                        onClick = onEditClick,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        EditPenIcon(
                            modifier = Modifier.size(GlassTopBarInnerIconSize),
                            color = actionIconColor
                        )
                    }
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
private fun ProfileHeader(
    profile: UserProfile,
    isOwnProfile: Boolean,
    isFollowLoading: Boolean,
    onEditClick: () -> Unit,
    onFollowClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp)
    ) {
        // Avatar
        ProfileAvatar(
            name = profile.displayName,
            avatarUrl = profile.avatarUrl,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Display name
        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // @username
        Text(
            text = "@${profile.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Bio
        if (profile.bio.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Join date
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CalendarIcon(
                modifier = Modifier.size(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(Res.string.profile_joined, formatJoinDate(profile.createdAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Stats row
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(
                count = profile.stats.followingCount,
                label = stringResource(Res.string.profile_following),
                onClick = onFollowingClick
            )
            StatItem(
                count = profile.stats.followersCount,
                label = stringResource(Res.string.profile_followers),
                onClick = onFollowersClick
            )
        }

        if (isOwnProfile) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(Res.string.profile_edit),
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Follow/Unfollow + Message buttons
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isFollowing = profile.isFollowedByCurrentUser == true
                if (isFollowing) {
                    OutlinedButton(
                        onClick = onFollowClick,
                        enabled = !isFollowLoading,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(Res.string.profile_following),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = onFollowClick,
                        enabled = !isFollowLoading,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(Res.string.profile_follow),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onMessageClick,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Message",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun StatItem(
    count: Int,
    label: String,
    onClick: () -> Unit = {}
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileTabRow(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit
) {
    val tabs = ProfileTab.entries
    val tabLabels = listOf(
        stringResource(Res.string.profile_posts),
        stringResource(Res.string.profile_replies),
        stringResource(Res.string.profile_likes)
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
private fun EmptyTabState(tab: ProfileTab) {
    val message = when (tab) {
        ProfileTab.POSTS -> stringResource(Res.string.profile_no_posts)
        ProfileTab.REPLIES -> stringResource(Res.string.profile_no_replies)
        ProfileTab.LIKES -> stringResource(Res.string.profile_no_likes)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        color
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f
        val left = size.width * 0.1f
        val right = size.width * 0.9f
        val top = size.height * 0.2f
        val bottom = size.height * 0.9f
        val headerBottom = size.height * 0.4f
        // Outer rect
        drawRect(
            color = resolvedColor,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        // Header line
        drawLine(
            resolvedColor,
            start = androidx.compose.ui.geometry.Offset(left, headerBottom),
            end = androidx.compose.ui.geometry.Offset(right, headerBottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Top handles
        val handle1X = size.width * 0.35f
        val handle2X = size.width * 0.65f
        val handleTop = size.height * 0.08f
        drawLine(resolvedColor, androidx.compose.ui.geometry.Offset(handle1X, handleTop), androidx.compose.ui.geometry.Offset(handle1X, top + stroke), stroke, cap = StrokeCap.Round)
        drawLine(resolvedColor, androidx.compose.ui.geometry.Offset(handle2X, handleTop), androidx.compose.ui.geometry.Offset(handle2X, top + stroke), stroke, cap = StrokeCap.Round)
    }
}

private fun formatJoinDate(timestamp: Long): String {
    val date = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$month ${date.year}"
}

private val previewProfile = UserProfile(
    id = "user_1",
    username = "connor",
    displayName = "Connor",
    bio = "Building Ktwiier with Kotlin Multiplatform and Compose.",
    avatarUrl = null,
    createdAt = 1700000000000L,
    stats = UserStats(
        followingCount = 128,
        followersCount = 342,
        postsCount = 57
    ),
    isFollowedByCurrentUser = true
)

private val previewPosts = listOf(
    Post(
        id = "post_1",
        content = "Finished the user profile screen polish today. The tab interactions feel much better now.",
        createdAt = 1700001000000L,
        updatedAt = 1700001000000L,
        author = PostAuthor(
            id = "user_1",
            displayName = "Connor",
            avatarUrl = null
        ),
        stats = PostStats(
            replyCount = 4,
            likeCount = 19,
            viewCount = 210
        ),
        isLikedByCurrentUser = true,
        isBookmarkedByCurrentUser = false
    ),
    Post(
        id = "post_2",
        content = "Anyone else using Molecule for MVI? Curious how you structure reducers across features.",
        createdAt = 1700002000000L,
        updatedAt = 1700002000000L,
        author = PostAuthor(
            id = "user_1",
            displayName = "Connor",
            avatarUrl = null
        ),
        stats = PostStats(
            replyCount = 2,
            likeCount = 8,
            viewCount = 96
        ),
        isLikedByCurrentUser = false,
        isBookmarkedByCurrentUser = true
    )
)

@Preview
@Composable
private fun UserProfileScreenPreview() {
    KwitterTheme(darkTheme = false) {
        UserProfileScreen(
            state = UserProfileUiState(
                profile = previewProfile,
                currentUserId = "viewer_1",
                posts = previewPosts,
                postsHasMore = true
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun UserProfileScreenOwnPreview() {
    val ownProfile = previewProfile.copy(id = "viewer_1", isFollowedByCurrentUser = null)

    KwitterTheme(darkTheme = true) {
        UserProfileScreen(
            state = UserProfileUiState(
                profile = ownProfile,
                currentUserId = "viewer_1",
                posts = previewPosts
            ),
            onAction = {}
        )
    }
}
