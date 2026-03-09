package com.connor.kwitter.data.messaging.datasource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import arrow.core.raise.fold
import com.connor.kwitter.data.messaging.local.ConversationDao
import com.connor.kwitter.data.messaging.local.ConversationEntity
import com.connor.kwitter.data.messaging.local.toEntity

internal const val CONVERSATIONS_LABEL = "conversations"
private const val PAGE_SIZE = 20

@OptIn(ExperimentalPagingApi::class)
class ConversationRemoteMediator(
    private val remoteDataSource: MessagingRemoteDataSource,
    private val conversationDao: ConversationDao
) : RemoteMediator<Int, ConversationEntity>() {

    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationEntity>
    ): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val remoteKey = conversationDao.getRemoteKeyByLabel(CONVERSATIONS_LABEL)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                remoteKey.nextCursor?.toInt()
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return fold(
            block = {
                remoteDataSource.getConversations(limit = PAGE_SIZE, offset = offset)
            },
            catch = { MediatorResult.Error(it) },
            recover = { error ->
                MediatorResult.Error(Exception(error.toString()))
            },
            transform = { conversationList ->

                val entities = conversationList.conversations.mapIndexed { index, conversation ->
                    conversation.toEntity(orderIndex = offset + index)
                }

                val endReached = conversationList.conversations.isEmpty() || !conversationList.hasMore
                val nextCursor = if (endReached) null else (offset + conversationList.conversations.size).toLong()

                when (loadType) {
                    LoadType.REFRESH -> conversationDao.replaceAll(
                        label = CONVERSATIONS_LABEL,
                        conversations = entities,
                        nextCursor = nextCursor
                    )
                    LoadType.APPEND -> conversationDao.append(
                        label = CONVERSATIONS_LABEL,
                        conversations = entities,
                        nextCursor = nextCursor
                    )
                    LoadType.PREPEND -> Unit
                }

                MediatorResult.Success(endOfPaginationReached = endReached)
            }
        )
    }
}


