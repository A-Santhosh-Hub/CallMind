package com.example.callmind.core.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Starts and stops recording through [CallRecordService] and decides when to record automatically. */
object CallRecorder {
    data class State(
        val recording: Boolean = false,
        val startedAt: Long = 0,
        val number: String = ""
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private val _failure = MutableStateFlow<String?>(null)

    /** Set when a recording could not be made (e.g. the phone silences apps during calls); the screen shows it once. */
    val failure: StateFlow<String?> = _failure

    internal fun reportFailure(message: String) {
        _failure.value = message
    }

    fun clearFailure() {
        _failure.value = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal fun setState(state: State) {
        _state.value = state
    }

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Returns false when recording cannot start (no microphone permission). */
    fun start(context: Context, call: CallInfo): Boolean {
        if (_state.value.recording) return true
        if (!hasMicPermission(context)) return false
        val intent = Intent(context, CallRecordService::class.java)
            .setAction(CallRecordService.ACTION_START)
            .putExtra(CallRecordService.EXTRA_NUMBER, call.number)
            .putExtra(CallRecordService.EXTRA_NAME, call.name)
            .putExtra(CallRecordService.EXTRA_INCOMING, call.isIncoming)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop(context: Context) {
        if (!_state.value.recording) return
        context.startService(Intent(context, CallRecordService::class.java).setAction(CallRecordService.ACTION_STOP))
    }

    /** Called when a call is answered: starts recording if the user chose automatic recording. */
    fun onCallActive(context: Context, call: CallInfo?) {
        call ?: return
        if (_state.value.recording || call.isConference) return
        val mode = PhoneModeStore.autoRecord.value
        if (mode == AutoRecordMode.OFF || !PhoneModeStore.hasRecordConsent(context)) return
        if (PhoneModeStore.recordingBlocked.value) return // the phone already refused: do not try on every call
        scope.launch {
            val allowed = when (mode) {
                AutoRecordMode.ALL -> true
                AutoRecordMode.UNSAVED -> !isSavedContact(context, call.number)
                AutoRecordMode.OFF -> false
            }
            if (allowed) start(context, call)
        }
    }

    /** Called when a call disappears: the recording ends with the last call. */
    fun onCallEnded(context: Context) {
        if (CallManager.calls.value.isEmpty()) stop(context)
    }

    private fun isSavedContact(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        } catch (e: Exception) {
            false
        }
    }
}
