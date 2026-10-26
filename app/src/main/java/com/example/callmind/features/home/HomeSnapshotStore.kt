package com.example.callmind.features.home

import android.content.Context
import com.example.callmind.core.util.startOfToday
import com.example.callmind.data.local.entities.CallLogDisplayItem
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.local.entities.GroupedCallLog
import com.example.callmind.data.local.entities.ReminderEntity
import com.example.callmind.data.local.entities.ReminderType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers what Home last showed, so the next launch paints real numbers on the very first frame
 * instead of zeros that jump a moment later. It is only a placeholder: fresh data replaces it
 * as soon as it has been read.
 */
class HomeSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(state: HomeState) {
        try {
            val calls = JSONArray()
            state.recentCalls.filterIsInstance<CallLogDisplayItem.CallGroup>().take(MAX_CALLS).forEach { item ->
                val c = item.group.lastCall
                calls.put(
                    JSONObject()
                        .put("n", c.phoneNumber).put("name", c.contactName ?: JSONObject.NULL)
                        .put("d", c.durationSeconds).put("t", c.timestamp).put("ty", c.type.name)
                        .put("size", item.group.calls.size)
                )
            }
            val reminders = JSONArray()
            state.upcomingReminders.take(MAX_REMINDERS).forEach { r ->
                reminders.put(
                    JSONObject()
                        .put("id", r.id).put("title", r.title).put("desc", r.description ?: JSONObject.NULL)
                        .put("time", r.scheduledTime).put("phone", r.phoneNumber ?: JSONObject.NULL)
                        .put("type", r.type.name)
                )
            }
            val json = JSONObject()
                .put("day", startOfToday())
                .put("calls", calls).put("reminders", reminders)
                .put("callCount", state.callCount).put("noteCount", state.noteCount)
                .put("scheduled", state.scheduledCount).put("talk", state.totalTalkTime)
            prefs.edit().putString(KEY, json.toString()).apply()
        } catch (e: Exception) {
            // The snapshot is only an optimisation.
        }
    }

    fun load(): HomeState? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val sameDay = json.optLong("day") == startOfToday()
            val now = System.currentTimeMillis()

            val calls = json.getJSONArray("calls")
            val groups = (0 until calls.length()).map { i ->
                val o = calls.getJSONObject(i)
                val record = CallRecordEntity(
                    phoneNumber = o.getString("n"),
                    contactName = if (o.isNull("name")) null else o.getString("name"),
                    durationSeconds = o.getLong("d"),
                    timestamp = o.getLong("t"),
                    type = CallType.valueOf(o.getString("ty"))
                )
                CallLogDisplayItem.CallGroup(GroupedCallLog(record, List(o.optInt("size", 1).coerceAtLeast(1)) { record }))
            }

            val reminderArray = json.getJSONArray("reminders")
            val reminders = (0 until reminderArray.length()).map { i ->
                val o = reminderArray.getJSONObject(i)
                ReminderEntity(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    description = if (o.isNull("desc")) null else o.getString("desc"),
                    scheduledTime = o.getLong("time"),
                    phoneNumber = if (o.isNull("phone")) null else o.getString("phone"),
                    type = ReminderType.valueOf(o.getString("type"))
                )
            }.filter { it.scheduledTime > now }

            HomeState(
                recentCalls = groups,
                upcomingReminders = reminders,
                // "Today" figures from another day would be wrong, so they start from zero.
                callCount = if (sameDay) json.optInt("callCount") else 0,
                noteCount = if (sameDay) json.optInt("noteCount") else 0,
                scheduledCount = json.optInt("scheduled"),
                totalTalkTime = if (sameDay) json.optString("talk", "0s") else "0s"
            )
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val PREFS = "callmind_home_cache"
        const val KEY = "snapshot"
        const val MAX_CALLS = 3
        const val MAX_REMINDERS = 2
    }
}
