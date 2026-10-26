package com.example.callmind.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val androidContactId: Long? = null,
    val name: String?,
    val phoneNumber: String,
    val labels: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val lastInteractionAt: Long? = null
)
