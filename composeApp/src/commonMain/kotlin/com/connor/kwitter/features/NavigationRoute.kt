package com.connor.kwitter.features

import kotlinx.serialization.Serializable

/**
 * 导航路由定义
 * 使用 sealed interface + @Serializable 实现类型安全的导航
 */
@Serializable
sealed interface NavigationRoute {
    @Serializable
    data object Splash : NavigationRoute

    @Serializable
    data object Login : NavigationRoute

    @Serializable
    data object Register : NavigationRoute

    @Serializable
    data object Home : NavigationRoute

    @Serializable
    data class PostDetail(val postId: String) : NavigationRoute

    @Serializable
    data class CreatePost(
        val parentId: String? = null,
        val returnToPostId: String? = null,
        val replyToAuthorName: String? = null,
        val replyToContent: String? = null,
        val replyToAvatarUrl: String? = null
    ) : NavigationRoute

    @Serializable
    data class MediaViewer(
        val mediaJson: String,
        val initialIndex: Int
    ) : NavigationRoute

    @Serializable
    data class UserProfile(
        val userId: String
    ) : NavigationRoute

    @Serializable
    data class EditProfile(
        val userId: String
    ) : NavigationRoute

    @Serializable
    data class UserFollowList(
        val userId: String,
        val displayName: String,
        val listType: String // "following" or "followers"
    ) : NavigationRoute

    @Serializable
    data object Search : NavigationRoute

    @Serializable
    data object ConversationList : NavigationRoute

    @Serializable
    data object Settings : NavigationRoute

    @Serializable
    data class Chat(
        val conversationId: String? = null,
        val otherUserId: String,
        val otherUserDisplayName: String,
        val otherUserAvatarUrl: String? = null
    ) : NavigationRoute

    @Serializable
    data class MessageSearch(
        val conversationId: String,
        val otherUserDisplayName: String
    ) : NavigationRoute
}
