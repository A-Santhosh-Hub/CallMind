package com.example.callmind.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.callmind.data.local.entities.CallRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordingDao {
    @Query("SELECT * FROM call_recordings ORDER BY startedAt DESC")
    fun getAll(): Flow<List<CallRecordingEntity>>

    @Query("SELECT * FROM call_recordings WHERE id = :id")
    suspend fun get(id: Long): CallRecordingEntity?

    @Insert
    suspend fun insert(recording: CallRecordingEntity): Long

    @Query("DELETE FROM call_recordings WHERE id = :id")
    suspend fun delete(id: Long)
}
