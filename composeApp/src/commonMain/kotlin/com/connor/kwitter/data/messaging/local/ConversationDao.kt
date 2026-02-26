package com.connor.kwitter.data.messaging.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.connor.kwitter.data.post.local.RemoteKeyEntity

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY orderIndex ASC")
    fun getPagingSource(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM remote_keys WHERE label = :label")
    suspend fun getRemoteKeyByLabel(label: String): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceRemoteKey(remoteKey: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE label = :label")
    suspend fun deleteRemoteKeyByLabel(label: String)

    @Transaction
    suspend fun replaceAll(
        label: String,
        conversations: List<ConversationEntity>,
        nextCursor: Long?
    ) {
        clearAll()
        deleteRemoteKeyByLabel(label)
        insertAll(conversations)
        if (nextCursor != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextCursor = nextCursor))
        }
    }

    @Transaction
    suspend fun append(
        label: String,
        conversations: List<ConversationEntity>,
        nextCursor: Long?
    ) {
        insertAll(conversations)
        if (nextCursor != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextCursor = nextCursor))
        } else {
            deleteRemoteKeyByLabel(label)
        }
    }

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getById(conversationId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE otherUserId = :otherUserId LIMIT 1")
    suspend fun getByOtherUserId(otherUserId: Long): ConversationEntity?

    @Query("UPDATE conversations SET unreadCount = :unreadCount WHERE id = :conversationId")
    suspend fun updateUnreadCount(conversationId: Long, unreadCount: Int)

    @Query("SELECT MIN(orderIndex) FROM conversations")
    suspend fun getMinOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastMessageRecalledAt = :recalledAt WHERE lastMessageId = :messageId")
    suspend fun updateLastMessageRecalled(messageId: Long, recalledAt: Long)

    @Query("UPDATE conversations SET lastMessageDeletedAt = :deletedAt WHERE lastMessageId = :messageId")
    suspend fun updateLastMessageDeleted(messageId: Long, deletedAt: Long)
}

