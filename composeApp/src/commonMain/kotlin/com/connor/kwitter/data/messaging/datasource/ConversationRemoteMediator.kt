package com.connor.kwitter.data.messaging.datasource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.connor.kwitter.data.messaging.local.ConversationDao
import com.connor.kwitter.data.messaging.local.ConversationEntity
import com.connor.kwitter.data.messaging.local.toEntity
import kotlinx.coroutines.CancellationException

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
                remoteKey.nextOffset
            }
        }

        return try {
            val result = remoteDataSource.getConversations(limit = PAGE_SIZE, offset = offset)

            result.fold(
                ifLeft = { error ->
                    MediatorResult.Error(Exception(error.toString()))
                },
                ifRight = { conversationList ->
                    val baseIndex = offset

                    val entities = conversationList.conversations.mapIndexed { index, conversation ->
                        conversation.toEntity(orderIndex = baseIndex + index)
                    }

                    val endReached = conversationList.conversations.isEmpty() || !conversationList.hasMore
                    val nextOffset = if (endReached) null else offset + conversationList.conversations.size

                    when (loadType) {
                        LoadType.REFRESH -> conversationDao.replaceAll(
                            label = CONVERSATIONS_LABEL,
                            conversations = entities,
                            nextOffset = nextOffset
                        )
                        LoadType.APPEND -> conversationDao.append(
                            label = CONVERSATIONS_LABEL,
                            conversations = entities,
                            nextOffset = nextOffset
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
