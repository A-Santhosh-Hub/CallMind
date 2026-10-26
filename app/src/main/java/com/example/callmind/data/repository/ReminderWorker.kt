package com.example.callmind.data.repository

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.callmind.core.notifications.NotificationHelper

/** Posts the notification when a scheduled reminder comes due. */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        NotificationHelper.showReminderNotification(
            context = applicationContext,
            reminderId = inputData.getLong(KEY_ID, 0L),
            title = inputData.getString(KEY_TITLE) ?: "CallMind reminder",
            description = inputData.getString(KEY_DESCRIPTION),
            phoneNumber = inputData.getString(KEY_PHONE_NUMBER)
        )
        return Result.success()
    }

    companion object {
        const val TAG = "callmind_reminder"
        const val KEY_ID = "EXTRA_ID"
        const val KEY_TITLE = "EXTRA_TITLE"
        const val KEY_DESCRIPTION = "EXTRA_DESCRIPTION"
        const val KEY_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
    }
}
