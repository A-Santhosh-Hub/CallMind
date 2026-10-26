package com.example.callmind.data.local.daos

import androidx.room.*
import com.example.callmind.data.local.entities.NumberRuleEntity
import com.example.callmind.data.local.entities.RuleType
import kotlinx.coroutines.flow.Flow

@Dao
interface NumberRuleDao {
    @Query("SELECT * FROM number_rules WHERE type = :type ORDER BY createdAt DESC")
    fun observe(type: RuleType): Flow<List<NumberRuleEntity>>

    @Query("SELECT * FROM number_rules")
    suspend fun getAll(): List<NumberRuleEntity>

    @Insert
    suspend fun insert(rule: NumberRuleEntity)

    @Delete
    suspend fun delete(rule: NumberRuleEntity)
}
