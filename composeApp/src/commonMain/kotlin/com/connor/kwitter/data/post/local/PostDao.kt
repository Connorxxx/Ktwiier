package com.connor.kwitter.data.post.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PostDao {

    @Query("SELECT * FROM posts ORDER BY timelineIndex ASC")
    fun getTimelinePagingSource(): PagingSource<Int, PostEntity>

    @Query("SELECT * FROM remote_keys WHERE label = :label")
    suspend fun getRemoteKeyByLabel(label: String): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceRemoteKey(remoteKey: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE label = :label")
    suspend fun deleteRemoteKeyByLabel(label: String)

    @Transaction
    suspend fun replaceTimeline(
        label: String,
        posts: List<PostEntity>,
        nextOffset: Int?
    ) {
        clearAll()
        deleteRemoteKeyByLabel(label)
        insertAll(posts)
        if (nextOffset != null) {
            insertOrReplaceRemoteKey(
                RemoteKeyEntity(
                    label = label,
                    nextOffset = nextOffset
                )
            )
        }
    }

    @Transaction
    suspend fun appendTimeline(
        label: String,
        posts: List<PostEntity>,
        nextOffset: Int?
    ) {
        insertAll(posts)
        if (nextOffset != null) {
            insertOrReplaceRemoteKey(
                RemoteKeyEntity(
                    label = label,
                    nextOffset = nextOffset
                )
            )
        } else {
            deleteRemoteKeyByLabel(label)
        }
    }

    @Query("SELECT * FROM posts WHERE id = :postId")
    suspend fun getPostById(postId: String): PostEntity?

    @Query("UPDATE posts SET isLikedByCurrentUser = :isLiked, likeCount = :likeCount WHERE id = :postId")
    suspend fun updateLikeState(postId: String, isLiked: Boolean, likeCount: Int)

    @Query("UPDATE posts SET isBookmarkedByCurrentUser = :isBookmarked WHERE id = :postId")
    suspend fun updateBookmarkState(postId: String, isBookmarked: Boolean)
}
