package com.connor.kwitter.data.post.local

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val DATABASE_NAME = "kwitter.db"

expect fun createDatabaseBuilder(context: Any? = null): RoomDatabase.Builder<AppDatabase>

fun createDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .fallbackToDestructiveMigration(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(Fts5SetupCallback)
        .build()
}

/**
 * Creates the FTS5 virtual table and sync triggers on every database open.
 * Uses `IF NOT EXISTS` / `IF NOT EXISTS` so it is idempotent.
 * Trigram tokenizer enables substring matching (important for CJK text).
 */
private object Fts5SetupCallback : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        super.onOpen(connection)

        // External-content FTS5 table backed by `messages`
        connection.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts
            USING fts5(content, content='messages', content_rowid=rowid, tokenize='trigram')
            """.trimIndent()
        )

        // Keep FTS in sync: INSERT trigger
        connection.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_fts_ai AFTER INSERT ON messages BEGIN
                INSERT INTO messages_fts(rowid, content) VALUES (NEW.rowid, NEW.content);
            END
            """.trimIndent()
        )

        // Keep FTS in sync: DELETE trigger
        connection.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_fts_ad AFTER DELETE ON messages BEGIN
                INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', OLD.rowid, OLD.content);
            END
            """.trimIndent()
        )

        // Keep FTS in sync: UPDATE trigger
        connection.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS messages_fts_au AFTER UPDATE ON messages BEGIN
                INSERT INTO messages_fts(messages_fts, rowid, content) VALUES('delete', OLD.rowid, OLD.content);
                INSERT INTO messages_fts(rowid, content) VALUES (NEW.rowid, NEW.content);
            END
            """.trimIndent()
        )

        // Rebuild FTS index to ensure consistency with existing data
        connection.execSQL(
            "INSERT INTO messages_fts(messages_fts) VALUES('rebuild')"
        )
    }
}
