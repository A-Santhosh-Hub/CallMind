package com.example.callmind.data.local.daos

import androidx.room.*
import com.example.callmind.data.local.entities.BlockedCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BlockedCallEntity>>

    @Query("SELECT * FROM blocked_calls WHERE timestamp >= :since")
    suspend fun since(since: Long): List<BlockedCallEntity>

    @Insert
    suspend fun insert(call: BlockedCallEntity)

    @Query("DELETE FROM blocked_calls")
    suspend fun deleteAll()

    /** Keeps the history from growing forever. */
    @Query("DELETE FROM blocked_calls WHERE id NOT IN (SELECT id FROM blocked_calls ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
