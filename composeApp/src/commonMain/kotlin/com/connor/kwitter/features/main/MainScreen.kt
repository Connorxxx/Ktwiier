package com.connor.kwitter.features.main

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.connor.kwitter.features.NavigationRoute
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.features.createpost.CreatePostAction
import com.connor.kwitter.features.createpost.CreatePostNavAction
import com.connor.kwitter.features.createpost.CreatePostScreen
import com.connor.kwitter.features.createpost.CreatePostViewModel
import com.connor.kwitter.features.editprofile.EditProfileAction
import com.connor.kwitter.features.editprofile.EditProfileNavAction
import com.connor.kwitter.features.editprofile.EditProfileScreen
import com.connor.kwitter.features.editprofile.EditProfileViewModel
import com.connor.kwitter.features.home.HomeAction
import com.connor.kwitter.features.home.HomeNavAction
import com.connor.kwitter.features.home.HomeScreen
import com.connor.kwitter.features.home.HomeViewModel
import com.connor.kwitter.features.search.SearchAction
import com.connor.kwitter.features.search.SearchNavAction
import com.connor.kwitter.features.search.SearchScreen
import com.connor.kwitter.features.search.SearchViewModel
import com.connor.kwitter.features.login.LoginAction
import com.connor.kwitter.features.login.LoginNavAction
import com.connor.kwitter.features.login.LoginScreen
import com.connor.kwitter.features.login.LoginViewModel
import com.connor.kwitter.features.auth.RegisterAction
import com.connor.kwitter.features.auth.RegisterNavAction
import com.connor.kwitter.features.auth.RegisterScreen
import com.connor.kwitter.features.auth.RegisterViewModel
import com.connor.kwitter.features.mediaviewer.MediaViewerAction
import com.connor.kwitter.features.mediaviewer.MediaViewerNavAction
import com.connor.kwitter.features.mediaviewer.MediaViewerScreen
import com.connor.kwitter.features.mediaviewer.MediaViewerViewModel
import com.connor.kwitter.features.postdetail.PostDetailAction
import com.connor.kwitter.features.postdetail.PostDetailNavAction
import com.connor.kwitter.features.postdetail.PostDetailScreen
import com.connor.kwitter.features.postdetail.PostDetailViewModel
import com.connor.kwitter.features.userprofile.UserProfileAction
import com.connor.kwitter.features.userprofile.UserProfileNavAction
import com.connor.kwitter.features.userprofile.UserProfileScreen
import com.connor.kwitter.features.userprofile.UserProfileViewModel
import com.connor.kwitter.features.userlist.UserListAction
import com.connor.kwitter.features.userlist.UserListNavAction
import com.connor.kwitter.features.userlist.UserListScreen
import com.connor.kwitter.features.userlist.UserListType
import com.connor.kwitter.features.userlist.UserListViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main Screen - 应用的主入口
 * 使用 Navigation 3 管理导航栈
 */
@Composable
fun MainScreen(
    mainVm: MainViewModel = koinViewModel()
) {
    val mainState by mainVm.state.collectAsStateWithLifecycle()
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Counter to trigger profile refresh after edit
    val profileRefreshKey = remember { mutableIntStateOf(0) }

    val navigateToMediaViewer: (List<PostMedia>, Int) -> Unit = remember(mainState) {
        { media, index ->
            val mediaJson = json.encodeToString(media)
            mainState.onNavigate(NavigationRoute.MediaViewer(mediaJson, index))
        }
    }

    LaunchedEffect(Unit) {
        mainVm.onAction(MainAction.Load)
    }

    NavDisplay(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        backStack = mainState.backStack,
        onBack = mainState.onBack,

        // 🎨 全局前进动画：缩放 + 淡入淡出 + 水平滑动
        transitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth / 3 }, // 从右侧 1/3 位置开始
                animationSpec = tween(400)
            ) + fadeIn(
                animationSpec = tween(400)
            ) + scaleIn(
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(400)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(400)
                ) + fadeOut(
                    animationSpec = tween(300)
                ) + scaleOut(
                    targetScale = 0.95f,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    animationSpec = tween(400)
                )
            )
        },

        // 🔙 全局后退动画：反向缩放 + 淡入淡出
        popTransitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(400)
            ) + fadeIn(
                animationSpec = tween(400)
            ) + scaleIn(
                initialScale = 0.95f,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
                animationSpec = tween(400)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 3 },
                    animationSpec = tween(400)
                ) + fadeOut(
                    animationSpec = tween(300)
                ) + scaleOut(
                    targetScale = 0.92f,
                    transformOrigin = TransformOrigin(0.5f, 0.5f),
                    animationSpec = tween(400)
                )
            )
        },

        // 📱 预测性返回手势（与 popTransitionSpec 保持一致）
        predictivePopTransitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(400)
            ) + fadeIn(
                animationSpec = tween(400)
            ) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(400)
            )) togetherWith (
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth / 3 },
                    animationSpec = tween(400)
                ) + fadeOut(
                    animationSpec = tween(300)
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(400)
                )
            )
        },

        entryProvider = entryProvider {
            entry<NavigationRoute.Register> {
                val vm: RegisterViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                RegisterScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is RegisterAction -> vm.onEvent(action)
                            is RegisterNavAction -> when (action) {
                                RegisterNavAction.OnRegisterSuccess -> mainState.onNavigate(NavigationRoute.Home)
                                // Login/Register 互相导航使用 replace，避免栈累积
                                RegisterNavAction.LoginClick -> mainState.onNavigateReplace(NavigationRoute.Login)
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Login> {
                val vm: LoginViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LoginScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is LoginAction -> vm.onEvent(action)
                            is LoginNavAction -> when (action) {
                                LoginNavAction.OnLoginSuccess -> mainState.onNavigate(NavigationRoute.Home)
                                // Login/Register 互相导航使用 replace，避免栈累积
                                LoginNavAction.RegisterClick -> mainState.onNavigateReplace(NavigationRoute.Register)
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Home> {
                val vm: HomeViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                HomeScreen(
                    state = state,
                    pagingFlow = vm.pagingFlow,
                    onAction = { action ->
                        when (action) {
                            is HomeAction -> vm.onEvent(action)
                            is HomeNavAction -> when (action) {
                                is HomeNavAction.PostClick -> mainState.onNavigate(
                                    NavigationRoute.PostDetail(action.postId)
                                )
                                HomeNavAction.CreatePostClick -> mainState.onNavigate(
                                    NavigationRoute.CreatePost()
                                )
                                is HomeNavAction.MediaClick -> navigateToMediaViewer(
                                    action.media, action.index
                                )
                                is HomeNavAction.AuthorClick -> mainState.onNavigate(
                                    NavigationRoute.UserProfile(userId = action.userId)
                                )
                                HomeNavAction.SearchClick -> mainState.onNavigate(
                                    NavigationRoute.Search
                                )
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.PostDetail> { route ->
                val vm: PostDetailViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(route.postId) {
                    vm.onEvent(PostDetailAction.Load(route.postId))
                }

                PostDetailScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is PostDetailAction -> vm.onEvent(action)
                            is PostDetailNavAction -> when (action) {
                                is PostDetailNavAction.ReplyClick -> mainState.onNavigate(
                                    NavigationRoute.CreatePost(
                                        parentId = action.postId,
                                        returnToPostId = route.postId,
                                        replyToAuthorName = action.authorName,
                                        replyToContent = action.content
                                    )
                                )
                                PostDetailNavAction.BackClick -> mainState.onBack()
                                is PostDetailNavAction.MediaClick -> navigateToMediaViewer(
                                    action.media, action.index
                                )
                                is PostDetailNavAction.AuthorClick -> mainState.onNavigate(
                                    NavigationRoute.UserProfile(action.userId)
                                )
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.CreatePost> { route ->
                val vm: CreatePostViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(route.parentId, route.replyToAuthorName, route.replyToContent) {
                    vm.onEvent(
                        CreatePostAction.SetReplyTarget(
                            parentId = route.parentId,
                            authorName = route.replyToAuthorName,
                            content = route.replyToContent
                        )
                    )
                }

                CreatePostScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is CreatePostAction -> vm.onEvent(action)
                            is CreatePostNavAction -> when (action) {
                                CreatePostNavAction.OnPostCreated -> mainState.onBack()
                                CreatePostNavAction.BackClick -> mainState.onBack()
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Splash> {
                SplashScreen()
            }

            entry<NavigationRoute.UserProfile> { route ->
                val vm: UserProfileViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(route.userId) {
                    vm.onEvent(UserProfileAction.Load(userId = route.userId))
                }

                // Refresh profile data when returning from EditProfile
                LaunchedEffect(profileRefreshKey.intValue) {
                    if (profileRefreshKey.intValue > 0) {
                        vm.onEvent(UserProfileAction.Refresh)
                    }
                }

                UserProfileScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is UserProfileAction -> vm.onEvent(action)
                            is UserProfileNavAction -> when (action) {
                                UserProfileNavAction.BackClick -> mainState.onBack()
                                is UserProfileNavAction.PostClick -> mainState.onNavigate(
                                    NavigationRoute.PostDetail(action.postId)
                                )
                                is UserProfileNavAction.MediaClick -> navigateToMediaViewer(
                                    action.media, action.index
                                )
                                is UserProfileNavAction.AuthorClick -> mainState.onNavigate(
                                    NavigationRoute.UserProfile(userId = action.userId)
                                )
                                UserProfileNavAction.EditProfileClick -> mainState.onNavigate(
                                    NavigationRoute.EditProfile(userId = route.userId)
                                )
                                UserProfileNavAction.FollowingClick -> {
                                    val displayName = state.profile?.displayName ?: ""
                                    mainState.onNavigate(
                                        NavigationRoute.UserFollowList(
                                            userId = route.userId,
                                            displayName = displayName,
                                            listType = "following"
                                        )
                                    )
                                }
                                UserProfileNavAction.FollowersClick -> {
                                    val displayName = state.profile?.displayName ?: ""
                                    mainState.onNavigate(
                                        NavigationRoute.UserFollowList(
                                            userId = route.userId,
                                            displayName = displayName,
                                            listType = "followers"
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.EditProfile> { route ->
                val vm: EditProfileViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(route.userId) {
                    vm.onEvent(EditProfileAction.Load(route.userId))
                }

                EditProfileScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is EditProfileAction -> vm.onEvent(action)
                            is EditProfileNavAction -> when (action) {
                                EditProfileNavAction.BackClick -> mainState.onBack()
                                EditProfileNavAction.SaveSuccess -> {
                                    profileRefreshKey.intValue++
                                    mainState.onBack()
                                }
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.UserFollowList> { route ->
                val vm: UserListViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val listType = when (route.listType) {
                    "followers" -> UserListType.FOLLOWERS
                    else -> UserListType.FOLLOWING
                }

                LaunchedEffect(route.userId, route.listType) {
                    vm.onEvent(
                        UserListAction.Load(
                            userId = route.userId,
                            displayName = route.displayName,
                            listType = listType
                        )
                    )
                }

                UserListScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is UserListAction -> vm.onEvent(action)
                            is UserListNavAction -> when (action) {
                                UserListNavAction.BackClick -> mainState.onBack()
                                is UserListNavAction.UserClick -> mainState.onNavigate(
                                    NavigationRoute.UserProfile(userId = action.userId)
                                )
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.MediaViewer> { route ->
                val vm: MediaViewerViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(route.mediaJson, route.initialIndex) {
                    val mediaList = json.decodeFromString<List<PostMedia>>(route.mediaJson)
                    vm.onEvent(MediaViewerAction.Initialize(mediaList, route.initialIndex))
                }

                MediaViewerScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is MediaViewerAction -> vm.onEvent(action)
                            is MediaViewerNavAction -> when (action) {
                                MediaViewerNavAction.BackClick -> mainState.onBack()
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Search> {
                val vm: SearchViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                SearchScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is SearchAction -> vm.onEvent(action)
                            is SearchNavAction -> when (action) {
                                SearchNavAction.BackClick -> mainState.onBack()
                                is SearchNavAction.PostClick -> mainState.onNavigate(
                                    NavigationRoute.PostDetail(action.postId)
                                )
                                is SearchNavAction.MediaClick -> navigateToMediaViewer(
                                    action.media, action.index
                                )
                                is SearchNavAction.AuthorClick -> mainState.onNavigate(
                                    NavigationRoute.UserProfile(userId = action.userId)
                                )
                                is SearchNavAction.UserClick -> mainState.onNavigate(
                                    NavigationRoute.UserProfile(userId = action.userId)
                                )
                            }
                        }
                    }
                )
            }
        }
    )
}
