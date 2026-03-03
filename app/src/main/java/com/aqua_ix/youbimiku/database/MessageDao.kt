package com.aqua_ix.youbimiku.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM MessageEntity")
    suspend fun getAll(): List<MessageEntity>

    @Query("DELETE FROM MessageEntity")
    suspend fun deleteAll()
}