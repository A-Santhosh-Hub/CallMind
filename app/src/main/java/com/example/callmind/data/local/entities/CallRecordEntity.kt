package com.example.callmind.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val contactName: String? = null,
    val contactId: Long? = null,
    val durationSeconds: Long,
    val timestamp: Long,
    val type: CallType
)

enum class CallType {
    INCOMING, OUTGOING, MISSED
}
