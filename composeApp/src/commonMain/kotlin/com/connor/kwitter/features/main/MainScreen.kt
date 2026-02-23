package com.connor.kwitter.features.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.connor.kwitter.features.glass.NativeGlassBottomBar
import com.connor.kwitter.features.glass.NativeTopBarCoordinator
import com.connor.kwitter.features.glass.NativeTopBarAction
import com.connor.kwitter.features.glass.NativeTopBarButtonAction
import com.connor.kwitter.features.glass.SyncNativeTopBarController
import com.connor.kwitter.features.glass.getNativeTabBarController
import com.connor.kwitter.features.glass.getNativeTopBarController
import com.connor.kwitter.features.glass.rememberNativeTopBarBinding
import com.connor.kwitter.features.glass.rememberNativeTopBarCoordinator
import com.connor.kwitter.features.glass.supportsNativeGlassBars
import com.connor.kwitter.core.ui.GlassSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.connor.kwitter.features.NavigationRoute
import com.connor.kwitter.domain.post.model.PostMedia
import com.connor.kwitter.features.chat.ChatAction
import com.connor.kwitter.features.chat.ChatNavAction
import com.connor.kwitter.features.chat.ChatScreen
import com.connor.kwitter.features.chat.ChatViewModel
import com.connor.kwitter.features.conversationlist.ConversationListNavAction
import com.connor.kwitter.features.conversationlist.ConversationListScreen
import com.connor.kwitter.features.conversationlist.ConversationListViewModel
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
import com.connor.kwitter.features.settings.SettingsAction
import com.connor.kwitter.features.settings.SettingsScreen
import com.connor.kwitter.features.settings.SettingsViewModel
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
import com.connor.kwitter.core.theme.LocalIsDarkTheme
import kotlinx.serialization.json.Json
import org.koin.compose.viewmodel.koinViewModel

private val MainBottomElementBottomPadding = 26.dp
private val MainBottomHorizontalPadding = 22.dp
private val MainBottomBarHeight = 62.dp

private fun shouldShowMainBottomBar(route: NavigationRoute?): Boolean =
    route?.toBottomTabOrNull() != null

private fun routeUsesNativeTopBar(route: NavigationRoute?): Boolean = when (route) {
    NavigationRoute.Home,
    is NavigationRoute.PostDetail,
    is NavigationRoute.CreatePost,
    is NavigationRoute.UserProfile,
    is NavigationRoute.EditProfile,
    is NavigationRoute.UserFollowList,
    NavigationRoute.Search,
    NavigationRoute.ConversationList,
    is NavigationRoute.Chat -> true

    else -> false
}

private fun predictedBackTargetRoute(backStack: List<NavigationRoute>): NavigationRoute? {
    val current = backStack.lastOrNull() ?: return null
    return when {
        current.toBottomTabOrNull() == null -> backStack.getOrNull(backStack.lastIndex - 1)
        current != NavigationRoute.Home -> NavigationRoute.Home
        else -> null
    }
}

@Composable
fun MainScreen(
    mainVm: MainViewModel = koinViewModel()
) {
    val mainState by mainVm.state.collectAsStateWithLifecycle()
    val json = remember { Json { ignoreUnknownKeys = true } }

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

    val backStackSnapshot = mainState.backStack.toList()
    val currentRoute = backStackSnapshot.lastOrNull()
    val selectedTab = backStackSnapshot
        .asReversed()
        .firstNotNullOfOrNull { route -> route.toBottomTabOrNull() }
        ?: MainBottomTab.Home

    val showBottomBar = shouldShowMainBottomBar(currentRoute)

    // Native tab bar integration (iOS)
    val nativeTabController = remember { getNativeTabBarController() }
    val nativeTopBarController = remember { getNativeTopBarController() }
    val useNativeTopBar = nativeTopBarController != null
    val topBarCoordinator: NativeTopBarCoordinator =
        rememberNativeTopBarCoordinator(::routeUsesNativeTopBar)

    if (nativeTabController != null) {
        // Forward native tab selection -> Compose navigation
        LaunchedEffect(Unit) {
            nativeTabController.tabSelectionEvents.collect { index ->
                MainBottomTab.entries.getOrNull(index)?.let { tab ->
                    mainState.onNavigateRoot(tab.toRoute())
                }
            }
        }
        // Sync Compose tab selection -> native tab bar
        LaunchedEffect(selectedTab) {
            nativeTabController.syncSelectedIndex(MainBottomTab.entries.indexOf(selectedTab))
        }
        // Show/hide native tab bar based on route
        LaunchedEffect(showBottomBar) {
            nativeTabController.setTabBarVisible(showBottomBar)
        }
    }

    // Only show Compose bottom bar when NOT using native tab bar
    val showComposeBottomBar = showBottomBar && nativeTabController == null

    LaunchedEffect(backStackSnapshot) {
        topBarCoordinator.pruneTo(backStackSnapshot.toSet())
    }

    val previousRouteInBackStack = backStackSnapshot.getOrNull(backStackSnapshot.lastIndex - 1)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestFallbackRoute by rememberUpdatedState(previousRouteInBackStack)
    val latestActionHandlersByRoute by rememberUpdatedState(
        topBarCoordinator.actionHandlersSnapshot()
    )

    LaunchedEffect(nativeTopBarController) {
        nativeTopBarController?.actionEvents?.collect { action ->
            topBarCoordinator.resolveActionHandler(
                route = latestCurrentRoute,
                fallbackRoute = latestFallbackRoute,
                handlersByRoute = latestActionHandlersByRoute
            )?.invoke(action)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val entries = rememberDecoratedNavEntries(
            backStack = mainState.backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
            entry<NavigationRoute.Home>(metadata = mainTabSceneMetadata) {
                val vm: HomeViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = NavigationRoute.Home
                ) { action ->
                    if (action is NativeTopBarAction.ButtonClicked) {
                        when (action.action) {
                            NativeTopBarButtonAction.CreatePost -> {
                                mainState.onNavigate(NavigationRoute.CreatePost())
                            }

                            NativeTopBarButtonAction.Profile -> {
                                val userId = state.currentUserId
                                if (userId != null) {
                                    mainState.onNavigate(NavigationRoute.UserProfile(userId = userId))
                                }
                            }

                            else -> Unit
                        }
                    }
                }

                HomeScreen(
                    state = state,
                    pagingFlow = vm.pagingFlow,
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
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
                                HomeNavAction.SearchClick -> mainState.onNavigateRoot(
                                    NavigationRoute.Search
                                )
                                HomeNavAction.MessagesClick -> mainState.onNavigateRoot(
                                    NavigationRoute.ConversationList
                                )
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Splash> {
                SplashScreen()
            }

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
                                LoginNavAction.RegisterClick -> mainState.onNavigateReplace(NavigationRoute.Register)
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.PostDetail> { route ->
                val vm: PostDetailViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = route,
                    onBack = mainState.onBack
                )

                LaunchedEffect(route.postId) {
                    vm.onEvent(PostDetailAction.Load(route.postId))
                }

                PostDetailScreen(
                    state = state,
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
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
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = route,
                    onBack = mainState.onBack
                )

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
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
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

            entry<NavigationRoute.UserProfile> { route ->
                val vm: UserProfileViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = route,
                    onBack = mainState.onBack
                ) { action ->
                    if (action is NativeTopBarAction.ButtonClicked &&
                        action.action == NativeTopBarButtonAction.Edit) {
                        mainState.onNavigate(
                            NavigationRoute.EditProfile(userId = route.userId)
                        )
                    }
                }

                LaunchedEffect(route.userId) {
                    vm.onEvent(UserProfileAction.Load(userId = route.userId))
                }

                LaunchedEffect(profileRefreshKey.intValue) {
                    if (profileRefreshKey.intValue > 0) {
                        vm.onEvent(UserProfileAction.Refresh)
                    }
                }

                UserProfileScreen(
                    state = state,
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
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
                                is UserProfileNavAction.MessageClick -> mainState.onNavigate(
                                    NavigationRoute.Chat(
                                        conversationId = null,
                                        otherUserId = action.userId,
                                        otherUserDisplayName = action.displayName
                                    )
                                )
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.EditProfile> { route ->
                val vm: EditProfileViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = route
                ) { action ->
                    if (action is NativeTopBarAction.ButtonClicked) {
                        when (action.action) {
                            NativeTopBarButtonAction.Back -> {
                                if (state.pendingCropImageBytes != null) {
                                    vm.onEvent(EditProfileAction.AvatarCropCancelled)
                                } else {
                                    mainState.onBack()
                                }
                            }

                            NativeTopBarButtonAction.Save -> {
                                if (state.pendingCropImageBytes == null) {
                                    vm.onEvent(EditProfileAction.Save)
                                }
                            }

                            else -> Unit
                        }
                    }
                }

                LaunchedEffect(route.userId) {
                    vm.onEvent(EditProfileAction.Load(route.userId))
                }

                EditProfileScreen(
                    state = state,
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
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
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = route,
                    onBack = mainState.onBack
                )

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
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
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

            entry<NavigationRoute.Search>(metadata = mainTabSceneMetadata) {
                val vm: SearchViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = NavigationRoute.Search,
                    onBack = mainState.onBack
                ) { action ->
                    when (action) {
                        is NativeTopBarAction.SearchQueryChanged -> {
                            vm.onEvent(SearchAction.UpdateQuery(action.query))
                        }

                        NativeTopBarAction.SearchSubmitted -> {
                            vm.onEvent(SearchAction.SubmitSearch)
                        }

                        else -> {}
                    }
                }

                SearchScreen(
                    state = state,
                    postsPaging = vm.postsPaging,
                    repliesPaging = vm.repliesPaging,
                    usersPaging = vm.usersPaging,
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
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

            entry<NavigationRoute.Settings>(metadata = mainTabSceneMetadata) {
                val vm: SettingsViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()

                SettingsScreen(
                    state = state,
                    onAction = { action ->
                        if (action is SettingsAction) {
                            vm.onEvent(action)
                        }
                    }
                )
            }

            entry<NavigationRoute.ConversationList>(metadata = mainTabSceneMetadata) {
                val vm: ConversationListViewModel = koinViewModel()
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = NavigationRoute.ConversationList,
                    onBack = mainState.onBack
                )

                ConversationListScreen(
                    pagingFlow = vm.pagingFlow,
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
                    onAction = { action ->
                        when (action) {
                            is ConversationListNavAction -> when (action) {
                                ConversationListNavAction.BackClick -> mainState.onBack()
                                is ConversationListNavAction.ConversationClick -> mainState.onNavigate(
                                    NavigationRoute.Chat(
                                        conversationId = action.conversationId,
                                        otherUserId = action.otherUserId,
                                        otherUserDisplayName = action.otherUserDisplayName
                                    )
                                )
                            }
                        }
                    }
                )
            }

            entry<NavigationRoute.Chat> { route ->
                val vm: ChatViewModel = koinViewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val onNativeTopBarModel = rememberNativeTopBarBinding(
                    coordinator = topBarCoordinator,
                    route = route,
                    onBack = mainState.onBack
                )

                LaunchedEffect(route.conversationId, route.otherUserId) {
                    vm.onEvent(
                        ChatAction.Load(
                            conversationId = route.conversationId,
                            otherUserId = route.otherUserId,
                            otherUserDisplayName = route.otherUserDisplayName
                        )
                    )
                }

                ChatScreen(
                    state = state,
                    pagingFlow = vm.pagingFlow,
                    useNativeTopBar = useNativeTopBar,
                    onNativeTopBarModel = onNativeTopBarModel,
                    onAction = { action ->
                        when (action) {
                            is ChatAction -> vm.onEvent(action)
                            is ChatNavAction -> when (action) {
                                ChatNavAction.BackClick -> mainState.onBack()
                            }
                        }
                    }
                )
            }
        }
        )

        val sceneState = rememberSceneState(
            entries = entries,
            sceneStrategy = SinglePaneSceneStrategy(),
            onBack = mainState.onBack
        )
        val currentScene = sceneState.currentScene
        val navigationEventState = rememberNavigationEventState(
            currentInfo = SceneInfo(currentScene),
            backInfo = sceneState.previousScenes.map { SceneInfo(it) }
        )

        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = currentScene.previousEntries.isNotEmpty(),
            onBackCompleted = {
                repeat(entries.size - currentScene.previousEntries.size) {
                    mainState.onBack()
                }
            }
        )

        SyncNativeTopBarController(
            controller = nativeTopBarController,
            coordinator = topBarCoordinator,
            backStackSnapshot = backStackSnapshot,
            navigationTransitionState = navigationEventState.transitionState,
            predictBackTargetRoute = ::predictedBackTargetRoute
        )

        NavDisplay(
            sceneState = sceneState,
            navigationEventState = navigationEventState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (shouldUseMainTabCrossFade(initialState, targetState)) {
                    MainScreenTransitions.mainTabSwitch()
                } else {
                    MainScreenTransitions.push()
                }
            },
            popTransitionSpec = {
                if (shouldUseMainTabCrossFade(initialState, targetState)) {
                    MainScreenTransitions.mainTabSwitch()
                } else {
                    MainScreenTransitions.pop(includeTransformOrigin = true)
                }
            },
            predictivePopTransitionSpec = {
                if (shouldUseMainTabCrossFade(initialState, targetState)) {
                    MainScreenTransitions.mainTabSwitch()
                } else {
                    MainScreenTransitions.pop(includeTransformOrigin = false)
                }
            }
        )

        // Layer 3: Compose bottom bar (only when NOT using native tab bar)
        AnimatedVisibility(
            visible = showComposeBottomBar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = MainBottomHorizontalPadding,
                    end = MainBottomHorizontalPadding,
                    bottom = MainBottomElementBottomPadding
                ),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            GlassBottomBar(
                modifier = Modifier,
                selectedTab = selectedTab,
                onTabClick = { tab ->
                    mainState.onNavigateRoot(tab.toRoute())
                }
            )
        }
    }
}

@Composable
private fun GlassBottomBar(
    modifier: Modifier = Modifier,
    selectedTab: MainBottomTab,
    onTabClick: (MainBottomTab) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val barShape = RoundedCornerShape(50)
    val tabShape = RoundedCornerShape(20.dp)

    if (supportsNativeGlassBars()) {
        NativeGlassBottomBar(
            modifier = modifier
                .fillMaxWidth()
                .height(MainBottomBarHeight)
                .shadow(elevation = 16.dp, shape = barShape),
            isDarkTheme = isDark,
            tabLabels = MainBottomTab.entries.map { it.label },
            selectedIndex = MainBottomTab.entries.indexOf(selectedTab),
            onTabSelected = { tabIndex ->
                MainBottomTab.entries.getOrNull(tabIndex)?.let(onTabClick)
            }
        )
        return
    }

    val activeColor = if (isDark) {
        Color.White.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val inactiveColor = if (isDark) {
        Color.White.copy(alpha = 0.45f)
    } else {
        Color.Black.copy(alpha = 0.35f)
    }
    val selectedTabBg = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(MainBottomBarHeight)
            .shadow(elevation = 16.dp, shape = barShape),
        shape = barShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                val textColor by animateColorAsState(
                    targetValue = if (selected) activeColor else inactiveColor,
                    animationSpec = tween(200)
                )
                val tabBackgroundColor by animateColorAsState(
                    targetValue = if (selected) selectedTabBg else Color.Transparent,
                    animationSpec = tween(200)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(tabShape)
                        .background(tabBackgroundColor, tabShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabClick(tab) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
