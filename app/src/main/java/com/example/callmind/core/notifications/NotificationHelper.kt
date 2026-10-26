package com.example.callmind.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.callmind.MainActivity
import com.example.callmind.R
import com.example.callmind.data.local.entities.BlockReason
import com.example.callmind.features.postcall.PostCallActivity

object NotificationHelper {
    private const val POST_CALL_CHANNEL = "post_call_channel"
    private const val REMINDER_CHANNEL = "reminder_channel"
    private const val BLOCKED_CHANNEL = "blocked_calls_channel"
    private const val POST_CALL_NOTIFICATION_ID = 1
    private const val BLOCKED_NOTIFICATION_BASE = 100_000

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context, id: String, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun showPostCallNotification(context: Context, phoneNumber: String?) {
        if (!canNotify(context)) return
        ensureChannel(context, POST_CALL_CHANNEL, "Post-Call Experience")

        val intent = Intent(context, PostCallActivity::class.java).apply {
            putExtra(PostCallActivity.EXTRA_PHONE_NUMBER, phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, POST_CALL_NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (phoneNumber.isNullOrBlank()) {
            "Tap to add a note or schedule a follow-up"
        } else {
            "Tap to organize next steps for $phoneNumber"
        }
        val notification = NotificationCompat.Builder(context, POST_CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_callmind)
            .setContentTitle("Call ended")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notify(context, POST_CALL_NOTIFICATION_ID, notification)
    }

    fun showReminderNotification(
        context: Context,
        reminderId: Long,
        title: String,
        description: String?,
        phoneNumber: String?
    ) {
        if (!canNotify(context)) return
        ensureChannel(context, REMINDER_CHANNEL, "Reminders")

        val intent = if (!phoneNumber.isNullOrBlank()) {
            Intent(context, PostCallActivity::class.java).apply {
                putExtra(PostCallActivity.EXTRA_PHONE_NUMBER, phoneNumber)
            }
        } else {
            Intent(context, MainActivity::class.java)
        }.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }

        val notificationId = (reminderId % Int.MAX_VALUE).toInt() + 1000
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_callmind)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        val body = description?.takeIf { it.isNotBlank() } ?: phoneNumber?.takeIf { it.isNotBlank() }
        if (body != null) {
            builder.setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        notify(context, notificationId, builder.build())
    }

    fun showBlockedCallNotification(context: Context, phoneNumber: String, reason: BlockReason) {
        if (!canNotify(context)) return
        ensureChannel(context, BLOCKED_CHANNEL, "Blocked calls")

        val intent = Intent(context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val notificationId = BLOCKED_NOTIFICATION_BASE + (phoneNumber.hashCode() and 0xFFFF)
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val why = when (reason) {
            BlockReason.BLOCKLIST -> "On your blocklist"
            BlockReason.UNSAVED -> "Not in your contacts"
            BlockReason.UNKNOWN_NUMBER -> "Unknown number"
        }
        val notification = NotificationCompat.Builder(context, BLOCKED_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_callmind)
            .setContentTitle("Blocked a call")
            .setContentText("${phoneNumber.ifBlank { "Unknown number" }} • $why")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notify(context, notificationId, notification)
    }

    @Suppress("MissingPermission") // guarded by canNotify()
    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}
