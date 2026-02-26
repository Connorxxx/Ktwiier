package com.connor.kwitter.data.messaging.datasource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.connor.kwitter.data.messaging.local.MessageDao
import com.connor.kwitter.data.messaging.local.MessageEntity
import com.connor.kwitter.data.messaging.local.toEntity
import kotlinx.coroutines.CancellationException

private const val PAGE_SIZE = 50

internal fun messagesLabel(conversationId: Long): String = "messages_$conversationId"

@OptIn(ExperimentalPagingApi::class)
class MessageRemoteMediator(
    private val conversationId: Long,
    private val remoteDataSource: MessagingRemoteDataSource,
    private val messageDao: MessageDao
) : RemoteMediator<Int, MessageEntity>() {

    private val label = messagesLabel(conversationId)

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MessageEntity>
    ): MediatorResult {
        val cursor: Long? = when (loadType) {
            LoadType.REFRESH -> null
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val remoteKey = messageDao.getRemoteKeyByLabel(label)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                remoteKey.nextCursor ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return try {
            val result = remoteDataSource.getMessages(
                conversationId = conversationId,
                limit = PAGE_SIZE,
                beforeId = cursor
            )

            result.fold(
                ifLeft = { error ->
                    MediatorResult.Error(Exception(error.toString()))
                },
                ifRight = { messageList ->
                    // API returns DESC (newest first). We store with orderIndex where
                    // lower index = newer message. With reversed LazyColumn,
                    // ORDER BY orderIndex ASC puts newest (index 0) at bottom.
                    val entities = messageList.messages.mapIndexed { index, message ->
                        message.toEntity(orderIndex = index)
                    }

                    val endReached = messageList.messages.isEmpty() || !messageList.hasMore
                    val nextCursor = if (endReached) null else messageList.nextCursor

                    when (loadType) {
                        LoadType.REFRESH -> messageDao.replaceMessages(
                            conversationId = conversationId,
                            label = label,
                            messages = entities,
                            nextCursor = nextCursor
                        )
                        LoadType.APPEND -> messageDao.appendMessages(
                            label = label,
                            messages = entities,
                            nextCursor = nextCursor
                        )
                        LoadType.PREPEND -> Unit
                    }

                    MediatorResult.Success(endOfPaginationReached = endReached)
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}


