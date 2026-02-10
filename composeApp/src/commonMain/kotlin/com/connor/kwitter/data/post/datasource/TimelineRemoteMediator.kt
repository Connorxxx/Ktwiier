package com.connor.kwitter.data.post.datasource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.connor.kwitter.data.post.local.AppDatabase
import com.connor.kwitter.data.post.local.PostEntity
import com.connor.kwitter.data.post.local.RemoteKeyEntity
import com.connor.kwitter.data.post.local.toEntity
import com.connor.kwitter.domain.post.model.PostPageQuery

private const val TIMELINE_LABEL = "timeline"
private const val PAGE_SIZE = 20

@OptIn(ExperimentalPagingApi::class)
class TimelineRemoteMediator(
    private val remoteDataSource: PostRemoteDataSource,
    private val database: AppDatabase,
    private val getToken: suspend () -> String?
) : RemoteMediator<Int, PostEntity>() {

    private val postDao = database.postDao()
    private val remoteKeyDao = database.remoteKeyDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>
    ): MediatorResult {
        val offset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val remoteKey = remoteKeyDao.getByLabel(TIMELINE_LABEL)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                remoteKey.nextOffset
            }
        }

        return try {
            val token = getToken()
            val result = remoteDataSource.getTimeline(
                query = PostPageQuery(limit = PAGE_SIZE, offset = offset),
                token = token
            )

            result.fold(
                ifLeft = { error ->
                    MediatorResult.Error(Exception(error.toString()))
                },
                ifRight = { postList ->
                    val baseIndex = if (loadType == LoadType.REFRESH) 0 else {
                        val key = remoteKeyDao.getByLabel(TIMELINE_LABEL)
                        key?.nextOffset ?: 0
                    }

                    val entities = postList.posts.mapIndexed { index, post ->
                        post.toEntity(timelineIndex = baseIndex + index)
                    }

                    if (loadType == LoadType.REFRESH) {
                        postDao.clearAll()
                        remoteKeyDao.deleteByLabel(TIMELINE_LABEL)
                    }

                    postDao.insertAll(entities)

                    val nextOffset = offset + postList.posts.size
                    if (postList.hasMore) {
                        remoteKeyDao.insertOrReplace(
                            RemoteKeyEntity(label = TIMELINE_LABEL, nextOffset = nextOffset)
                        )
                    } else {
                        remoteKeyDao.deleteByLabel(TIMELINE_LABEL)
                    }

                    MediatorResult.Success(endOfPaginationReached = !postList.hasMore)
                }
            )
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
