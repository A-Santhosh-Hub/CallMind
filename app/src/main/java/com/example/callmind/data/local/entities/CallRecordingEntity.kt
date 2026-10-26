package com.example.callmind.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One call recording made by CallMind; the audio itself is the file at [filePath]. */
@Entity(tableName = "call_recordings")
data class CallRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val phoneNumber: String,
    val contactName: String? = null,
    val startedAt: Long,
    val durationMs: Long,
    val isIncoming: Boolean
)
