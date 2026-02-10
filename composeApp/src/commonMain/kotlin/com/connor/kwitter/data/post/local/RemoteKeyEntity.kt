package com.connor.kwitter.data.post.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey
    val label: String,
    val nextOffset: Int
)
