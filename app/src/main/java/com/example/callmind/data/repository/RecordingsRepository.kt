package com.example.callmind.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.callmind.data.local.daos.CallRecordingDao
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallRecordingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One call recording, wherever it came from. */
data class RecordingItem(
    /** Stable id for lists and the player: "app:<id>" or "phone:<id>". */
    val key: String,
    /** A content:// URI or a file path. */
    val uri: String,
    /** Who the call was with: a contact name or a number. */
    val label: String,
    val number: String?,
    val startedAt: Long,
    val durationMs: Long,
    /** True for recordings made by the phone's own call recorder (read-only for CallMind). */
    val fromPhone: Boolean,
    val isIncoming: Boolean? = null,
    val appId: Long? = null
)

/** Where the phone's own call recorder keeps its files (vivo / OriginOS). */
const val PHONE_RECORDER_FOLDER = "Recordings/Record/Call"

/** Where CallMind keeps the recordings it makes itself. */
const val CALLMIND_RECORDING_FOLDER = "Recordings/CallMind"

/**
 * All call recordings in one list: the ones CallMind recorded itself (database) and the ones the phone's
 * own call recorder saved in `Recordings/Record/Call` (read from the media library, needs the
 * "music and audio" permission). The phone's recorder works from the phone's own call app, which is
 * allowed to capture the other person; ordinary apps are not.
 */
@Singleton
class RecordingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CallRecordingDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val phone = MutableStateFlow<List<RecordingItem>>(emptyList())
    private var scanJob: Job? = null
    private var observing = false

    /** Newest first. */
    val all: StateFlow<List<RecordingItem>> = combine(dao.getAll(), phone) { own, fromPhone ->
        (own.map { it.toItem() } + fromPhone).sortedByDescending { it.startedAt }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun hasPhoneAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, accessPermission()) == PackageManager.PERMISSION_GRANTED

    /** Reads the phone recorder's folder again (also called automatically when audio files change). */
    fun refresh() {
        startObserving()
        scanJob?.cancel()
        scanJob = scope.launch {
            delay(150) // several changes often arrive together
            phone.value = if (hasPhoneAccess()) scanPhoneRecordings() else emptyList()
        }
    }

    private fun startObserving() {
        if (observing) return
        observing = true
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = refresh()
                }
            )
        } catch (e: Exception) {
            Log.w("RecordingsRepository", "Cannot watch the media library", e)
        }
    }

    private fun scanPhoneRecordings(): List<RecordingItem> {
        val result = ArrayList<RecordingItem>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.DURATION
                    ),
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
                    arrayOf("$PHONE_RECORDER_FOLDER%"),
                    null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val item = phoneItem(
                            key = "phone:$id",
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                            displayName = c.getString(1).orEmpty(),
                            durationMs = c.getLong(2)
                        )
                        if (item != null) result.add(item)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(android.os.Environment.getExternalStorageDirectory(), PHONE_RECORDER_FOLDER)
                dir.listFiles()?.forEach { f ->
                    phoneItem("phone:${f.absolutePath}", f.absolutePath, f.name, 0L)?.let(result::add)
                }
            }
        } catch (e: Exception) {
            Log.w("RecordingsRepository", "Could not read the phone's call recordings", e)
        }
        return result
    }

    private fun phoneItem(key: String, uri: String, displayName: String, durationMs: Long): RecordingItem? {
        val (label, startedAt) = parsePhoneRecordingName(displayName) ?: return null
        return RecordingItem(
            key = key,
            uri = uri,
            label = label,
            number = label.takeIf { l -> l.any { it.isDigit() } && l.none { it.isLetter() } },
            startedAt = startedAt,
            durationMs = durationMs,
            fromPhone = true
        )
    }

    private fun CallRecordingEntity.toItem() = RecordingItem(
        key = "app:$id",
        uri = filePath,
        label = contactName ?: phoneNumber.ifBlank { "Unknown number" },
        number = phoneNumber.ifBlank { null },
        startedAt = startedAt,
        durationMs = durationMs,
        fromPhone = false,
        isIncoming = isIncoming,
        appId = id
    )

    suspend fun delete(item: RecordingItem) {
        val id = item.appId ?: return
        deleteFile(item.uri)
        dao.delete(id)
    }

    /** Removes every recording CallMind made itself (used by "Clear all CallMind data"). */
    suspend fun deleteAllOwn() {
        all.value.filter { !it.fromPhone }.forEach { deleteFile(it.uri) }
    }

    private fun deleteFile(uri: String) {
        try {
            if (uri.startsWith("content://")) context.contentResolver.delete(Uri.parse(uri), null, null)
            else File(uri).delete()
        } catch (e: Exception) {
            Log.w("RecordingsRepository", "Could not delete $uri", e)
        }
    }

    companion object {
        /** The permission that lets an app read audio files it did not create. */
        fun accessPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        private val NAME = Regex("""^(.*) (\d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2})\.\w+$""")

        /** "Alex #office 2026-10-26 17-15-22.m4a" -> ("Alex #office", start time). */
        fun parsePhoneRecordingName(displayName: String): Pair<String, Long>? {
            val m = NAME.matchEntire(displayName.trim()) ?: return null
            val time = try {
                SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).parse(m.groupValues[2])?.time
            } catch (e: Exception) {
                null
            } ?: return null
            return m.groupValues[1].trim() to time
        }

        private fun tail(number: String?) = number.orEmpty().filter { it.isDigit() }.takeLast(10)

        /**
         * Is [rec] the recording of [call]? The recording starts after the call was answered, so it must start
         * between the call's start and its end (plus ringing time); and it must be filed under the same
         * number, or under the same contact name.
         */
        fun matches(call: CallRecordEntity, rec: RecordingItem): Boolean {
            val start = call.timestamp
            val end = start + (call.durationSeconds + 90) * 1000
            if (rec.startedAt < start - 10_000 || rec.startedAt > end + 30_000) return false
            if (rec.number != null) {
                val a = tail(rec.number)
                return a.length >= 7 && a == tail(call.phoneNumber)
            }
            val name = call.contactName ?: return true // no name to compare: the time window decides
            return rec.label.contains(name, ignoreCase = true) || name.contains(rec.label, ignoreCase = true)
        }

        /** The recording of [call]; the phone recorder's wins over CallMind's, which may be silent on a mobile call. */
        fun forCall(call: CallRecordEntity, recordings: List<RecordingItem>): RecordingItem? {
            val found = recordings.filter { matches(call, it) }
            return found.firstOrNull { it.fromPhone } ?: found.firstOrNull()
        }
    }
}
