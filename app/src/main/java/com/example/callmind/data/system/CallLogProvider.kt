package com.example.callmind.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** SIM slot (1 or 2) and the place the number is registered in, when the phone knows them. */
data class CallExtra(val simSlot: Int?, val location: String?)

@Singleton
class CallLogProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _changes = MutableStateFlow(0)

    /** Increases whenever the phone's call log changes (a call ended, an entry was deleted...). */
    val changes: StateFlow<Int> = _changes

    /** Extra per-call details for the recents list (SIM slot, where the number is from), keyed by [extraKey]. */
    private val _extras = MutableStateFlow<Map<String, CallExtra>>(emptyMap())
    val extras: StateFlow<Map<String, CallExtra>> = _extras

    init {
        // Screens refresh themselves from this instead of re-reading the log every time they appear.
        try {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        _changes.value = _changes.value + 1
                    }
                }
            )
        } catch (e: Exception) {
            Log.w("CallLogProvider", "Cannot observe the call log", e)
        }
    }

    /**
     * Newest calls first.
     * @param limit read at most this many rows (the provider stops early, which keeps Home fast)
     * @param since only calls at or after this time (epoch ms)
     */
    suspend fun getCallLogs(limit: Int? = null, since: Long? = null): List<CallRecordEntity> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w("CallLogProvider", "Permission READ_CALL_LOG not granted")
            return@withContext emptyList()
        }

        val callLogs = mutableListOf<CallRecordEntity>()
        val extras = HashMap<String, CallExtra>()
        try {
            val uri = if (limit != null) {
                CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
            } else CallLog.Calls.CONTENT_URI

            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.GEOCODED_LOCATION,
                    CallLog.Calls.PHONE_ACCOUNT_ID
                ),
                if (since != null) "${CallLog.Calls.DATE} >= ?" else null,
                if (since != null) arrayOf(since.toString()) else null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val geoIndex = it.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
                val accountIndex = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val slots = simSlots()

                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)
                    val name = it.getString(nameIndex)

                    val callType = when (it.getInt(typeIndex)) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE,
                        CallLog.Calls.REJECTED_TYPE,
                        CallLog.Calls.BLOCKED_TYPE -> CallType.MISSED
                        else -> CallType.INCOMING
                    }

                    val location = if (geoIndex >= 0) it.getString(geoIndex)?.takeIf { g -> g.isNotBlank() } else null
                    val slot = if (accountIndex >= 0) slots[it.getString(accountIndex)] else null
                    if (location != null || slot != null) {
                        extras[extraKey(number, it.getLong(dateIndex))] = CallExtra(slot, location)
                    }

                    callLogs.add(
                        CallRecordEntity(
                            phoneNumber = number?.takeIf { n -> n.isNotBlank() } ?: "Unknown",
                            contactName = name?.takeIf { n -> n.isNotBlank() },
                            durationSeconds = it.getLong(durationIndex),
                            timestamp = it.getLong(dateIndex),
                            type = callType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CallLogProvider", "Error querying call logs", e)
        }
        resolveMissingNames(callLogs)
        if (extras.isNotEmpty()) _extras.value = _extras.value + extras
        callLogs
    }

    /** SIM slot (1, 2) by the account id the call log stores; empty without the phone permission. */
    private fun simSlots(): Map<String, Int> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyMap()
        }
        return try {
            val manager = context.getSystemService(android.telephony.SubscriptionManager::class.java)
            val result = HashMap<String, Int>()
            for (info in manager.activeSubscriptionInfoList.orEmpty()) {
                val slot = info.simSlotIndex + 1
                result[info.subscriptionId.toString()] = slot
                info.iccId?.let { id -> result[id] = slot }
            }
            result
        } catch (e: SecurityException) {
            emptyMap()
        }
    }

    /** Removes these calls from the phone's call log (needs WRITE_CALL_LOG). Returns how many rows were deleted. */
    suspend fun delete(calls: List<CallRecordEntity>): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return@withContext 0
        }
        var deleted = 0
        try {
            for (call in calls) {
                val withheld = call.phoneNumber == "Unknown"
                val where = "${CallLog.Calls.DATE} = ? AND " +
                    if (withheld) "(${CallLog.Calls.NUMBER} IS NULL OR ${CallLog.Calls.NUMBER} = '')" else "${CallLog.Calls.NUMBER} = ?"
                val args = if (withheld) arrayOf(call.timestamp.toString()) else arrayOf(call.timestamp.toString(), call.phoneNumber)
                deleted += context.contentResolver.delete(CallLog.Calls.CONTENT_URI, where, args)
            }
        } catch (e: Exception) {
            Log.w("CallLogProvider", "Could not delete call log entries", e)
        }
        deleted
    }

    /**
     * The call log only remembers the name a number had when the call happened. Numbers saved (or
     * renamed) since then would show as bare numbers, so fill the gaps from the address book with one query.
     */
    private fun resolveMissingNames(logs: MutableList<CallRecordEntity>) {
        if (logs.none { it.contactName == null && it.phoneNumber != "Unknown" }) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val byTail = HashMap<String, String>()
            context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val tail = numberTail(cursor.getString(0))
                    val name = cursor.getString(1)
                    if (tail.length >= MIN_TAIL && !name.isNullOrBlank() && tail !in byTail) byTail[tail] = name
                }
            }
            for (i in logs.indices) {
                val entry = logs[i]
                if (entry.contactName != null || entry.phoneNumber == "Unknown") continue
                val name = byTail[numberTail(entry.phoneNumber)] ?: continue
                logs[i] = entry.copy(contactName = name)
            }
        } catch (e: Exception) {
            Log.w("CallLogProvider", "Could not look up contact names", e)
        }
    }

    /** Last 10 digits: "+91 98406 34544", "098406 34544" and "9840634544" are the same number. */
    private fun numberTail(number: String?): String = number.orEmpty().filter { it.isDigit() }.takeLast(10)

    companion object {
        private const val MIN_TAIL = 7

        fun extraKey(number: String?, timestamp: Long) = "${number.orEmpty()}|$timestamp"
    }
}
