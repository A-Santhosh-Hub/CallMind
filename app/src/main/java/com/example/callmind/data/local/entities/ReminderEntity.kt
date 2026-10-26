package com.example.callmind.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderType {
    CALL,
    POPUP_NOTE
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val scheduledTime: Long,
    val phoneNumber: String? = null,
    val contactId: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val type: ReminderType = ReminderType.CALL
)
