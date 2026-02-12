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
        nextOffset: Int?
    ) {
        clearAll()
        deleteRemoteKeyByLabel(label)
        insertAll(conversations)
        if (nextOffset != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextOffset = nextOffset))
        }
    }

    @Transaction
    suspend fun append(
        label: String,
        conversations: List<ConversationEntity>,
        nextOffset: Int?
    ) {
        insertAll(conversations)
        if (nextOffset != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextOffset = nextOffset))
        } else {
            deleteRemoteKeyByLabel(label)
        }
    }

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getById(conversationId: String): ConversationEntity?

    @Query("UPDATE conversations SET unreadCount = :unreadCount WHERE id = :conversationId")
    suspend fun updateUnreadCount(conversationId: String, unreadCount: Int)

    @Query("SELECT MIN(orderIndex) FROM conversations")
    suspend fun getMinOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(conversation: ConversationEntity)
}
