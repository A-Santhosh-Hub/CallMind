package com.example.callmind.features.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.blocking.BlockingSettings
import com.example.callmind.core.blocking.BlockingSettingsStore
import com.example.callmind.data.local.CallMindDatabase
import com.example.callmind.data.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: CallMindDatabase,
    private val reminderRepository: ReminderRepository,
    private val blockingSettings: BlockingSettingsStore,
    private val recordings: com.example.callmind.data.repository.RecordingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun clearAllData() {
        viewModelScope.launch {
            // Room refuses to clear tables on the main thread, and pending reminders must not fire afterwards.
            withContext(Dispatchers.IO) {
                recordings.deleteAllOwn() // the audio files belong to the rows removed next
                database.clearAllTables()
                // The call-recording audio files belong to the rows just removed.
                context.getExternalFilesDir("Recordings")?.deleteRecursively()
                java.io.File(context.filesDir, "Recordings").deleteRecursively()
            }
            reminderRepository.cancelAllReminders()
            blockingSettings.update { BlockingSettings() }
        }
    }
}
