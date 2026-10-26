package com.example.callmind.core.phone

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** When CallMind records calls on its own. */
enum class AutoRecordMode(val label: String) {
    OFF("Off"),
    UNSAVED("Unsaved numbers only"),
    ALL("All calls")
}

/**
 * The two editions of CallMind, plus the phone-mode options.
 *
 * - Office mode (default): CallMind sits beside the phone's own dialer; "Call" hands over to it.
 * - Phone mode: CallMind is the phone app - dial pad, recents, in-call screen, call recording.
 *
 * Kept in SharedPreferences (read synchronously) so services and the first Compose frame know the
 * mode immediately, like [com.example.callmind.ui.theme.ThemeModeStore].
 */
object PhoneModeStore {
    private const val PREFS = "callmind_phone"
    private const val KEY_ENABLED = "phone_mode"
    private const val KEY_AUTO_RECORD = "auto_record"
    private const val KEY_CONSENT = "record_consent"
    private const val KEY_DIAL_TONES = "dial_tones"
    private const val KEY_POST_CALL_CARD = "post_call_card"
    private const val KEY_REC_BLOCKED = "recording_blocked"

    private var prefs: SharedPreferences? = null

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    private val _autoRecord = MutableStateFlow(AutoRecordMode.OFF)
    val autoRecord: StateFlow<AutoRecordMode> = _autoRecord

    private val _dialTones = MutableStateFlow(true)
    val dialTones: StateFlow<Boolean> = _dialTones

    private val _postCallCard = MutableStateFlow(false)

    private val _recordingBlocked = MutableStateFlow(false)

    /** True once the phone has refused to let CallMind record a call (the audio came back silent). */
    val recordingBlocked: StateFlow<Boolean> = _recordingBlocked

    /** In phone mode: also show the "Call ended" note/reminder card after each call. */
    val postCallCard: StateFlow<Boolean> = _postCallCard

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _enabled.value = p.getBoolean(KEY_ENABLED, false)
        _autoRecord.value = p.getString(KEY_AUTO_RECORD, null)
            ?.let { name -> AutoRecordMode.entries.firstOrNull { it.name == name } }
            ?: AutoRecordMode.OFF
        _dialTones.value = p.getBoolean(KEY_DIAL_TONES, true)
        _postCallCard.value = p.getBoolean(KEY_POST_CALL_CARD, false)
        _recordingBlocked.value = p.getBoolean(KEY_REC_BLOCKED, false)
    }

    fun isEnabled(context: Context): Boolean {
        init(context)
        return _enabled.value
    }

    fun setEnabled(context: Context, value: Boolean) {
        init(context)
        // commit(): turning phone mode off makes Android take the phone-app permissions away, which
        // restarts the app process, so the choice must already be on disk.
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.commit()
        _enabled.value = value
    }

    fun setAutoRecord(context: Context, mode: AutoRecordMode) {
        init(context)
        prefs?.edit()?.putString(KEY_AUTO_RECORD, mode.name)?.apply()
        _autoRecord.value = mode
    }

    fun setDialTones(context: Context, value: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_DIAL_TONES, value)?.apply()
        _dialTones.value = value
    }

    fun setPostCallCard(context: Context, value: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_POST_CALL_CARD, value)?.apply()
        _postCallCard.value = value
    }

    fun setRecordingBlocked(context: Context, blocked: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_REC_BLOCKED, blocked)?.apply()
        _recordingBlocked.value = blocked
    }

    /** Whether the user has accepted the call-recording notice (asked once). */
    fun hasRecordConsent(context: Context): Boolean {
        init(context)
        return prefs?.getBoolean(KEY_CONSENT, false) == true
    }

    fun setRecordConsent(context: Context) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_CONSENT, true)?.apply()
    }
}
