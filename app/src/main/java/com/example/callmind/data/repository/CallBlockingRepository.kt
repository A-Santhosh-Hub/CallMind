package com.example.callmind.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.callmind.core.blocking.BlockDecision
import com.example.callmind.core.blocking.BlockingSettingsStore
import com.example.callmind.core.blocking.Decision
import com.example.callmind.data.local.daos.BlockedCallDao
import com.example.callmind.data.local.daos.NumberRuleDao
import com.example.callmind.data.local.entities.BlockedCallEntity
import com.example.callmind.data.local.entities.NumberRuleEntity
import com.example.callmind.data.local.entities.RuleType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallBlockingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleDao: NumberRuleDao,
    private val blockedCallDao: BlockedCallDao,
    val settingsStore: BlockingSettingsStore
) {
    fun observeRules(type: RuleType): Flow<List<NumberRuleEntity>> = ruleDao.observe(type)

    fun observeBlockedCalls(): Flow<List<BlockedCallEntity>> = blockedCallDao.observeAll()

    /** Returns false when the number is unusable or already on that list. */
    suspend fun addRule(type: RuleType, rawNumber: String, name: String?): Boolean {
        val number = rawNumber.filter { it.isDigit() || it == '+' }
        if (BlockDecision.digits(number).length < MIN_NUMBER_DIGITS) return false
        val duplicate = ruleDao.getAll().any { it.type == type && BlockDecision.numbersMatch(it.phoneNumber, number) }
        if (duplicate) return false
        ruleDao.insert(NumberRuleEntity(phoneNumber = number, name = name?.trim()?.ifBlank { null }, type = type))
        return true
    }

    suspend fun removeRule(rule: NumberRuleEntity) = ruleDao.delete(rule)

    suspend fun clearHistory() = blockedCallDao.deleteAll()

    /** Decides about an incoming call and, if it is rejected, records it in the history. */
    suspend fun screen(rawNumber: String): Decision {
        val rules = ruleDao.getAll()
        val now = System.currentTimeMillis()
        val decision = BlockDecision.decide(
            number = rawNumber,
            settings = settingsStore.settings.value,
            blockList = rules.filter { it.type == RuleType.BLOCK }.map { it.phoneNumber },
            trustList = rules.filter { it.type == RuleType.TRUST }.map { it.phoneNumber },
            isSavedContact = isSavedContact(rawNumber),
            recentlyBlocked = blockedCallDao.since(now - BlockDecision.REPEAT_WINDOW_MS).map { it.phoneNumber }
        )
        if (decision.block && decision.reason != null) {
            blockedCallDao.insert(BlockedCallEntity(phoneNumber = rawNumber, timestamp = now, reason = decision.reason))
            blockedCallDao.trim(HISTORY_LIMIT)
        }
        return decision
    }

    /** null when it cannot be checked (no contacts permission). */
    private fun isSavedContact(number: String): Boolean? {
        if (BlockDecision.digits(number).isEmpty()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MIN_NUMBER_DIGITS = 3
        const val HISTORY_LIMIT = 200
    }
}
