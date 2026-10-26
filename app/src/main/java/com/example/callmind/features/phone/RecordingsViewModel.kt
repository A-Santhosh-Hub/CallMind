package com.example.callmind.features.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.phone.RecordingPlayer
import com.example.callmind.data.repository.RecordingItem
import com.example.callmind.data.repository.RecordingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val repository: RecordingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Every call recording: CallMind's own and the phone recorder's, newest first. */
    val recordings: StateFlow<List<RecordingItem>> = repository.all

    val playing: StateFlow<String?> = RecordingPlayer.playing
    val progress: StateFlow<Float> = RecordingPlayer.progress

    init {
        repository.refresh()
    }

    fun hasPhoneAccess() = repository.hasPhoneAccess()

    /** Look at the phone recorder's folder again (after the permission was given, or the screen opened). */
    fun refresh() = repository.refresh()

    fun toggle(item: RecordingItem) = RecordingPlayer.toggle(context, item.key, item.uri)

    fun stopPlayback() = RecordingPlayer.stop()

    fun delete(item: RecordingItem) {
        if (RecordingPlayer.playing.value == item.key) RecordingPlayer.stop()
        viewModelScope.launch { repository.delete(item) }
    }

    fun shareIntent(item: RecordingItem): Intent? {
        val uri: Uri = if (item.uri.startsWith("content://")) {
            Uri.parse(item.uri)
        } else {
            val file = File(item.uri)
            if (!file.exists()) return null
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("audio/mp4")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share recording"
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
