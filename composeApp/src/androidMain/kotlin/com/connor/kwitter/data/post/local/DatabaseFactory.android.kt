package com.connor.kwitter.data.post.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual fun createDatabaseBuilder(context: Any?): RoomDatabase.Builder<AppDatabase> {
    val androidContext = context as? Context
        ?: error("Android DatabaseFactory requires a non-null Context")
    val dbFile = androidContext.getDatabasePath(DATABASE_NAME)
    return Room.databaseBuilder<AppDatabase>(
        context = androidContext,
        name = dbFile.absolutePath
    )
}
