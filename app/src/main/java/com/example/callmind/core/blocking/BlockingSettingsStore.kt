package com.example.callmind.core.blocking

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Call-blocking switches, kept in SharedPreferences so the screening service can read them instantly. */
@Singleton
class BlockingSettingsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<BlockingSettings> = _settings

    fun update(transform: (BlockingSettings) -> BlockingSettings) {
        val updated = transform(_settings.value)
        prefs.edit()
            .putBoolean(KEY_UNSAVED, updated.blockUnsaved)
            .putBoolean(KEY_UNKNOWN, updated.blockUnknown)
            .putBoolean(KEY_REPEAT, updated.allowRepeat)
            .putString(KEY_NOTIFY, updated.notifyMode.name)
            .apply()
        _settings.value = updated
    }

    private fun read() = BlockingSettings(
        blockUnsaved = prefs.getBoolean(KEY_UNSAVED, false),
        blockUnknown = prefs.getBoolean(KEY_UNKNOWN, false),
        allowRepeat = prefs.getBoolean(KEY_REPEAT, true),
        notifyMode = prefs.getString(KEY_NOTIFY, null)
            ?.let { name -> BlockNotifyMode.entries.firstOrNull { it.name == name } }
            ?: BlockNotifyMode.EXCEPT_BLOCKLIST
    )

    private companion object {
        const val PREFS = "callmind_blocking"
        const val KEY_UNSAVED = "block_unsaved"
        const val KEY_UNKNOWN = "block_unknown"
        const val KEY_REPEAT = "allow_repeat"
        const val KEY_NOTIFY = "notify_mode"
    }
}
