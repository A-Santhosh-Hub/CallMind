package com.example.callmind.core.util

import com.example.callmind.data.local.entities.CallLogDisplayItem
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.GroupedCallLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun isSameDay(t1: Long, t2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

/**
 * Groups a newest-first call log into day headers followed by runs of consecutive calls
 * to/from the same number.
 */
fun groupCallLogs(logs: List<CallRecordEntity>): List<CallLogDisplayItem> {
    if (logs.isEmpty()) return emptyList()

    val now = System.currentTimeMillis()
    val yesterday = now - 24L * 60 * 60 * 1000
    val headerFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())

    fun headerFor(timestamp: Long): String = when {
        isSameDay(timestamp, now) -> "Today"
        isSameDay(timestamp, yesterday) -> "Yesterday"
        else -> headerFormat.format(Date(timestamp))
    }

    val result = mutableListOf<CallLogDisplayItem>()
    var currentHeader: String? = null
    var currentGroup = mutableListOf<CallRecordEntity>()

    fun flush() {
        if (currentGroup.isNotEmpty()) {
            result.add(CallLogDisplayItem.CallGroup(GroupedCallLog(currentGroup.first(), currentGroup.toList())))
            currentGroup = mutableListOf()
        }
    }

    for (log in logs) {
        val header = headerFor(log.timestamp)
        if (header != currentHeader) {
            flush()
            result.add(CallLogDisplayItem.Header(header))
            currentHeader = header
        }
        if (currentGroup.isNotEmpty() && currentGroup.last().phoneNumber != log.phoneNumber) {
            flush()
        }
        currentGroup.add(log)
    }
    flush()

    return result
}

fun formatTalkTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** "8 min ago", "2h ago", "Yesterday" ... */
fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 24 * 3_600_000L && isSameDay(timestamp, now) -> "${diff / 3_600_000L}h ago"
        isSameDay(timestamp, now - 24L * 3_600_000L) -> "Yesterday"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}

/** "In 2h 4m", "In 35m", "Overdue" */
fun formatTimeUntil(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = timestamp - now
    if (diff <= 0) return "Due now"
    val totalMinutes = diff / 60_000L
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "In ${days}d ${hours}h"
        hours > 0 -> "In ${hours}h ${minutes}m"
        else -> "In ${maxOf(minutes, 1)}m"
    }
}

/** "Today • 8:30 PM", "Tomorrow • 9:00 AM", "Oct 12 • 9:00 AM" */
fun formatScheduledLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    val day = when {
        isSameDay(timestamp, now) -> "Today"
        isSameDay(timestamp, now + 24L * 3_600_000L) -> "Tomorrow"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
    return "$day • $time"
}

/** Midnight at the start of the current day (epoch ms). */
fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
