package com.connor.kwitter.data.messaging.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.connor.kwitter.data.post.local.RemoteKeyEntity

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY orderIndex ASC")
    fun getPagingSource(conversationId: String): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM remote_keys WHERE label = :label")
    suspend fun getRemoteKeyByLabel(label: String): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearByConversation(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceRemoteKey(remoteKey: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE label = :label")
    suspend fun deleteRemoteKeyByLabel(label: String)

    @Transaction
    suspend fun replaceMessages(
        conversationId: String,
        label: String,
        messages: List<MessageEntity>,
        nextOffset: Int?
    ) {
        clearByConversation(conversationId)
        deleteRemoteKeyByLabel(label)
        insertAll(messages)
        if (nextOffset != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextOffset = nextOffset))
        }
    }

    @Transaction
    suspend fun appendMessages(
        label: String,
        messages: List<MessageEntity>,
        nextOffset: Int?
    ) {
        insertAll(messages)
        if (nextOffset != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextOffset = nextOffset))
        } else {
            deleteRemoteKeyByLabel(label)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT MIN(orderIndex) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMinOrderIndex(conversationId: String): Int?

    @Query("UPDATE messages SET readAt = :readAt WHERE conversationId = :conversationId AND senderId = :senderId AND readAt IS NULL")
    suspend fun markSentMessagesAsRead(conversationId: String, senderId: String, readAt: Long)

    @Query("UPDATE messages SET recalledAt = :recalledAt WHERE id = :messageId")
    suspend fun markMessageAsRecalled(messageId: String, recalledAt: Long)

    @Query("UPDATE messages SET deletedAt = :deletedAt WHERE id = :messageId")
    suspend fun markMessageAsDeleted(messageId: String, deletedAt: Long)
}
