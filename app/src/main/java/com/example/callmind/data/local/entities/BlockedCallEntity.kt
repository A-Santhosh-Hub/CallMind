package com.example.callmind.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BlockReason { BLOCKLIST, UNSAVED, UNKNOWN_NUMBER }

/** One call that CallMind rejected. */
@Entity(tableName = "blocked_calls")
data class BlockedCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: BlockReason
)
