package com.example.callmind.core.phone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.widget.RemoteViews
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.callmind.R
import com.example.callmind.features.phone.InCallActivity

/** The incoming-call and ongoing-call notifications (heads-up / full-screen answer prompt, hang-up button). */
object CallNotifications {
    private const val INCOMING_CHANNEL = "incoming_call_channel"
    private const val ONGOING_CHANNEL = "ongoing_call_channel"
    private const val NOTIFICATION_ID = 7001

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun update(context: Context, calls: List<CallInfo>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val call = calls.firstOrNull { it.state == CallUiState.RINGING } ?: calls.firstOrNull()
        if (call == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!canNotify(context)) return
        ensureChannels(manager)

        val ringing = call.state == CallUiState.RINGING
        val open = PendingIntent.getActivity(
            context, 0, InCallActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val person = Person.Builder().setName(call.displayName).setImportant(true).build()

        val builder = NotificationCompat.Builder(context, if (ringing) INCOMING_CHANNEL else ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_callmind)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (ringing) {
            // Floating card like the phone's own: avatar, name + number/place, round decline and answer buttons.
            val card = RemoteViews(context.packageName, R.layout.notification_incoming_call).apply {
                setTextViewText(R.id.call_name, call.displayName)
                val detail = listOfNotNull(
                    call.number.takeIf { call.name != null && it.isNotBlank() },
                    call.location,
                    call.simLabel
                ).joinToString("  ")
                setTextViewText(R.id.call_detail, detail.ifBlank { "Incoming call" })
                setImageViewBitmap(R.id.call_avatar, avatarBitmap(context, call))
                setOnClickPendingIntent(R.id.call_decline, action(context, CallActionReceiver.ACTION_DECLINE))
                setOnClickPendingIntent(R.id.call_answer, action(context, CallActionReceiver.ACTION_ANSWER))
            }
            builder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(open, true)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(card)
                .setCustomHeadsUpContentView(card)
                .setCustomBigContentView(card)
                .setContentTitle(call.displayName)
                .setContentText("Incoming call")
        } else {
            // Not CallStyle: Android 14+ only accepts it for a foreground service or a full-screen intent.
            builder
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentTitle(call.displayName)
                .addAction(R.drawable.ic_stat_callmind, "Hang up", action(context, CallActionReceiver.ACTION_HANG_UP))
                .setContentText(
                    when (call.state) {
                        CallUiState.DIALING -> "Calling…"
                        CallUiState.HOLDING -> "On hold"
                        else -> "Ongoing call"
                    }
                )
            if (call.state == CallUiState.ACTIVE && call.connectTimeMillis > 0) {
                builder.setUsesChronometer(true).setWhen(call.connectTimeMillis)
            }
        }
        // A notification problem must never take the call screen down with it.
        try {
            @Suppress("MissingPermission") // guarded by canNotify()
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.w("CallNotifications", "Could not post the call notification", e)
        }
    }

    /** Round avatar for the floating card: the contact photo, or the first letter on a soft colour. */
    private fun avatarBitmap(context: Context, call: CallInfo): Bitmap {
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val photo = call.photoUri?.let {
            try {
                context.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream) }
            } catch (e: Exception) {
                null
            }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (photo != null) {
            val scaled = Bitmap.createScaledBitmap(photo, size, size, true)
            paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        } else {
            paint.color = 0xFF5B7BD5.toInt()
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            val letter = call.displayName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
            if (letter != null) {
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = size * 0.46f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(letter, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2f, text)
            }
        }
        return out
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun action(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun ensureChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // The ringtone is played by the system, so the notification itself stays silent.
        manager.createNotificationChannel(
            NotificationChannel(INCOMING_CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ONGOING_CHANNEL, "Ongoing calls", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
