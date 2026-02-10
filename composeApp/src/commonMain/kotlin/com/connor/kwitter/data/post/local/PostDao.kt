package com.connor.kwitter.data.post.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PostDao {

    @Query("SELECT * FROM posts ORDER BY timelineIndex ASC")
    fun getTimelinePagingSource(): PagingSource<Int, PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts")
    suspend fun clearAll()

    @Query("SELECT * FROM posts WHERE id = :postId")
    suspend fun getPostById(postId: String): PostEntity?

    @Query("UPDATE posts SET isLikedByCurrentUser = :isLiked, likeCount = :likeCount WHERE id = :postId")
    suspend fun updateLikeState(postId: String, isLiked: Boolean, likeCount: Int)

    @Query("UPDATE posts SET isBookmarkedByCurrentUser = :isBookmarked WHERE id = :postId")
    suspend fun updateBookmarkState(postId: String, isBookmarked: Boolean)
}
