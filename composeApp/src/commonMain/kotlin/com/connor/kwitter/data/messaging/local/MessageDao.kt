package com.connor.kwitter.data.messaging.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.RoomRawQuery
import com.connor.kwitter.data.post.local.RemoteKeyEntity

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY orderIndex ASC")
    fun getPagingSource(conversationId: Long): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM remote_keys WHERE label = :label")
    suspend fun getRemoteKeyByLabel(label: String): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearByConversation(conversationId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceRemoteKey(remoteKey: RemoteKeyEntity)

    @Query("DELETE FROM remote_keys WHERE label = :label")
    suspend fun deleteRemoteKeyByLabel(label: String)

    @Transaction
    suspend fun replaceMessages(
        conversationId: Long,
        label: String,
        messages: List<MessageEntity>,
        nextCursor: Long?
    ) {
        clearByConversation(conversationId)
        deleteRemoteKeyByLabel(label)
        insertAll(messages)
        if (nextCursor != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextCursor = nextCursor))
        }
    }

    @Transaction
    suspend fun appendMessages(
        label: String,
        messages: List<MessageEntity>,
        nextCursor: Long?
    ) {
        insertAll(messages)
        if (nextCursor != null) {
            insertOrReplaceRemoteKey(RemoteKeyEntity(label = label, nextCursor = nextCursor))
        } else {
            deleteRemoteKeyByLabel(label)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT MIN(orderIndex) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMinOrderIndex(conversationId: Long): Int?

    @Query("UPDATE messages SET readAt = :readAt WHERE conversationId = :conversationId AND senderId != :readByUserId AND readAt IS NULL")
    suspend fun markOutgoingMessagesAsReadByPeer(
        conversationId: Long,
        readByUserId: Long,
        readAt: Long
    )

    @Query("UPDATE messages SET readAt = :readAt WHERE conversationId = :conversationId AND senderId = :senderId AND readAt IS NULL")
    suspend fun markMessagesAsReadFromSender(
        conversationId: Long,
        senderId: Long,
        readAt: Long
    )

    @Query("UPDATE messages SET recalledAt = :recalledAt WHERE id = :messageId")
    suspend fun markMessageAsRecalled(messageId: Long, recalledAt: Long)

    @Query("UPDATE messages SET deletedAt = :deletedAt WHERE id = :messageId")
    suspend fun markMessageAsDeleted(messageId: Long, deletedAt: Long)

    @RawQuery
    suspend fun searchMessagesRaw(query: RoomRawQuery): List<MessageSearchResultEntity>

    suspend fun searchMessages(
        conversationId: Long,
        query: String,
        limit: Int = 50
    ): List<MessageSearchResultEntity> {
        val sql = """
            SELECT m.id, m.conversationId, m.senderId, m.content, m.createdAt,
                   highlight(messages_fts, 0, '<mark>', '</mark>') AS highlightedContent
            FROM messages_fts fts
            JOIN messages m ON fts.rowid = m.rowid
            WHERE fts.content MATCH ?
              AND m.conversationId = ?
              AND m.deletedAt IS NULL
              AND m.recalledAt IS NULL
            ORDER BY fts.rank
            LIMIT ?
        """.trimIndent()
        val roomRawQuery = RoomRawQuery(sql) { statement ->
            statement.bindText(1, query)
            statement.bindLong(2, conversationId)
            statement.bindLong(3, limit.toLong())
        }
        return searchMessagesRaw(roomRawQuery)
    }

    suspend fun searchMessagesLike(
        conversationId: Long,
        query: String,
        limit: Int = 50
    ): List<MessageSearchResultEntity> {
        val escaped = query.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val pattern = "%$escaped%"
        val sql = """
            SELECT id, conversationId, senderId, content, createdAt, content AS highlightedContent
            FROM messages
            WHERE conversationId = ?
              AND content LIKE ? ESCAPE '\'
              AND deletedAt IS NULL
              AND recalledAt IS NULL
            ORDER BY createdAt DESC, CAST(id AS INTEGER) DESC
            LIMIT ?
        """.trimIndent()
        val roomRawQuery = RoomRawQuery(sql) { statement ->
            statement.bindLong(1, conversationId)
            statement.bindText(2, pattern)
            statement.bindLong(3, limit.toLong())
        }
        return searchMessagesRaw(roomRawQuery)
    }
}


