package com.example.callmind.data.local.daos

import androidx.room.*
import com.example.callmind.data.local.entities.QuickReplyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickReplyDao {
    @Query("SELECT * FROM quick_replies ORDER BY createdAt ASC")
    fun getAll(): Flow<List<QuickReplyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reply: QuickReplyEntity)

    @Update
    suspend fun update(reply: QuickReplyEntity)

    @Delete
    suspend fun delete(reply: QuickReplyEntity)
}
