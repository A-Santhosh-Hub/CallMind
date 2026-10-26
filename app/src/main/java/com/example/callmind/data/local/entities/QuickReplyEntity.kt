package com.example.callmind.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved WhatsApp message template, e.g. title "Classes" + the full message about class details. */
@Entity(tableName = "quick_replies")
data class QuickReplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)
