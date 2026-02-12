package com.connor.kwitter.data.post.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.connor.kwitter.data.messaging.local.ConversationDao
import com.connor.kwitter.data.messaging.local.ConversationEntity
import com.connor.kwitter.data.messaging.local.MessageDao
import com.connor.kwitter.data.messaging.local.MessageEntity

@Database(
    entities = [
        PostEntity::class,
        RemoteKeyEntity::class,
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 2
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
