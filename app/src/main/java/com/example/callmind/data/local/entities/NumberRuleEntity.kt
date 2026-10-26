package com.example.callmind.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RuleType { BLOCK, TRUST }

/** A phone number on the blocklist (always blocked) or the trustlist (never blocked). */
@Entity(tableName = "number_rules")
data class NumberRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val name: String? = null,
    val type: RuleType,
    val createdAt: Long = System.currentTimeMillis()
)
