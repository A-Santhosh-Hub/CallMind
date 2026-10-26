package com.example.callmind.data.repository

import android.content.Context
import androidx.work.*
import com.example.callmind.data.local.daos.ReminderDao
import com.example.callmind.data.local.entities.ReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderDao: ReminderDao
) {
    fun getAllReminders(): Flow<List<ReminderEntity>> = reminderDao.getAllReminders()

    suspend fun insertReminder(reminder: ReminderEntity) {
        // Use the generated row id; every reminder used to share id 0, so each new one replaced the last.
        val id = reminderDao.insertReminder(reminder)
        scheduleReminder(reminder.copy(id = id))
    }

    suspend fun setCompleted(reminder: ReminderEntity, completed: Boolean) {
        val updated = reminder.copy(isCompleted = completed)
        reminderDao.updateReminder(updated)
        if (completed) cancelReminder(reminder.id) else scheduleReminder(updated)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminder(reminder)
        cancelReminder(reminder.id)
    }

    fun cancelAllReminders() {
        WorkManager.getInstance(context).cancelAllWorkByTag(ReminderWorker.TAG)
    }

    private fun cancelReminder(id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    private fun scheduleReminder(reminder: ReminderEntity) {
        val delay = reminder.scheduledTime - System.currentTimeMillis()
        if (delay <= 0) return

        val data = Data.Builder()
            .putLong(ReminderWorker.KEY_ID, reminder.id)
            .putString(ReminderWorker.KEY_TITLE, reminder.title)
            .putString(ReminderWorker.KEY_DESCRIPTION, reminder.description)
            .putString(ReminderWorker.KEY_PHONE_NUMBER, reminder.phoneNumber)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(ReminderWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun workName(id: Long) = "reminder_$id"
}
