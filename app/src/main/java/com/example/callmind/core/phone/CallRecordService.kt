package com.example.callmind.core.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.callmind.R
import com.example.callmind.data.local.daos.CallRecordingDao
import com.example.callmind.data.local.entities.CallRecordingEntity
import com.example.callmind.data.repository.CALLMIND_RECORDING_FOLDER
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecordingEntryPoint {
    fun recordingDao(): CallRecordingDao
}

/**
 * Records the call audio into an .m4a file in the phone's shared "Recordings/CallMind" folder, so it shows up in
 * the Files app, and lists it in CallMind.
 *
 * Android only lets the phone's own call app capture the other person (and on some phones even blocks the
 * microphone during a mobile call). So after starting, the audio level is watched: if the microphone delivers
 * only silence, other microphone modes are tried, and if they are all silent the recording is thrown away and the
 * user is told, instead of keeping an empty file.
 */
class CallRecordService : Service() {
    /** Where the audio is going: a media-library entry (Android 10+) or a plain file. */
    private class Output(val uri: Uri?, val file: File?, val descriptor: ParcelFileDescriptor?)

    private var recorder: MediaRecorder? = null
    private var output: Output? = null
    private var startedAt = 0L
    private var number = ""
    private var name: String? = null
    private var incoming = false
    private var sourceIndex = 0
    private var monitor: Job? = null
    private var heardSound = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
                name = intent.getStringExtra(EXTRA_NAME)
                incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)
                startRecording()
            }
            ACTION_STOP -> stopRecording()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return
        startInForeground()
        startedAt = System.currentTimeMillis()
        sourceIndex = 0
        heardSound = false
        if (!beginWithSourcesFrom(0)) {
            giveUp("Could not start recording")
            return
        }
        CallRecorder.setState(CallRecorder.State(recording = true, startedAt = startedAt, number = number))
        watchAudioLevel()
    }

    /** Starts the first source from [from] on that works; false when none could start. */
    private fun beginWithSourcesFrom(from: Int): Boolean {
        for (i in from until SOURCES.size) {
            val out = createOutput() ?: return false
            val started = tryStart(SOURCES[i], out)
            if (started != null) {
                recorder = started
                output = out
                sourceIndex = i
                return true
            }
            discard(out)
        }
        return false
    }

    private fun createOutput(): Output? {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date())
        val label = (name ?: number.ifBlank { "Unknown number" }).replace(Regex("""[\\/:*?"<>|]"""), "_")
        val displayName = "$label $stamp.m4a"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(
                        MediaStore.Audio.Media.RELATIVE_PATH,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) CALLMIND_RECORDING_FOLDER else "Music/CallMind"
                    )
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                val pfd = contentResolver.openFileDescriptor(uri, "w")
                if (pfd == null) {
                    contentResolver.delete(uri, null, null)
                    null
                } else Output(uri, null, pfd)
            } else {
                val dir = getExternalFilesDir("Recordings") ?: File(filesDir, "Recordings")
                dir.mkdirs()
                Output(null, File(dir, displayName), null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot create the recording file", e)
            null
        }
    }

    private fun tryStart(source: Int, out: Output): MediaRecorder? {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // Same format as the phone's own call recorder: AAC, 48 kHz, 64 kbit/s, mono.
            r.setAudioChannels(1)
            r.setAudioSamplingRate(48_000)
            r.setAudioEncodingBitRate(64_000)
            if (out.descriptor != null) r.setOutputFile(out.descriptor.fileDescriptor) else r.setOutputFile(out.file!!.absolutePath)
            r.prepare()
            r.start()
            Log.w(TAG, "Recording source ${sourceName(source)} started")
            r
        } catch (e: Exception) {
            Log.w(TAG, "Recording source ${sourceName(source)} refused: ${e.javaClass.simpleName} ${e.message}")
            try { r.release() } catch (_: Exception) {}
            null
        }
    }

    /** Throws an unfinished recording away. */
    private fun discard(out: Output) {
        try { out.descriptor?.close() } catch (_: Exception) {}
        try {
            if (out.uri != null) contentResolver.delete(out.uri, null, null)
            out.file?.delete()
        } catch (_: Exception) {}
    }

    /**
     * A microphone that is blocked during the call delivers digital silence. Check the level once a second;
     * after four silent seconds try the next microphone mode, and when there is none left give up.
     */
    private fun watchAudioLevel() {
        monitor?.cancel()
        monitor = scope.launch {
            var silent = 0
            delay(1000)
            Log.w(TAG, "Watching the level of ${sourceName(SOURCES[sourceIndex])}")
            while (recorder != null) {
                val level = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
                Log.w(TAG, "Level of ${sourceName(SOURCES[sourceIndex])}: $level")
                if (level > 0) {
                    heardSound = true // sound is coming in: all good
                    return@launch
                }
                silent++
                if (silent >= 3) {
                    silent = 0
                    stopCurrent(discard = true)
                    if (!beginWithSourcesFrom(sourceIndex + 1)) {
                        giveUp("", blocked = true)
                        return@launch
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopCurrent(discard: Boolean): Pair<Output?, Boolean> {
        val r = recorder
        val out = output
        recorder = null
        output = null
        var saved = false
        if (r != null) {
            saved = try {
                r.stop()
                true
            } catch (e: RuntimeException) {
                false // stopped before any audio was captured
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        }
        if (out != null) {
            try { out.descriptor?.close() } catch (_: Exception) {}
            if (discard || !saved) discard(out)
        }
        return out to saved
    }

    private fun giveUp(message: String, blocked: Boolean = false) {
        monitor?.cancel()
        stopCurrent(discard = true)
        CallRecorder.setState(CallRecorder.State())
        if (blocked) {
            PhoneModeStore.setRecordingBlocked(this, true)
            CallRecorder.reportFailure(BLOCKED_MESSAGE)
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopRecording() {
        monitor?.cancel()
        val duration = System.currentTimeMillis() - startedAt
        // Several seconds of pure silence means the phone blocked the microphone: keep nothing.
        val blocked = !heardSound && duration >= 3_000
        val (out, saved) = stopCurrent(discard = blocked)
        CallRecorder.setState(CallRecorder.State())
        if (blocked) {
            PhoneModeStore.setRecordingBlocked(this, true)
            CallRecorder.reportFailure(BLOCKED_MESSAGE)
        } else if (out != null && saved) {
            val location = if (out.uri != null) {
                try {
                    contentResolver.update(out.uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not publish the recording", e)
                }
                out.uri.toString()
            } else out.file!!.absolutePath
            val entity = CallRecordingEntity(
                filePath = location,
                phoneNumber = number,
                contactName = name,
                startedAt = startedAt,
                durationMs = duration,
                isIncoming = incoming
            )
            val dao = EntryPointAccessors.fromApplication(applicationContext, RecordingEntryPoint::class.java).recordingDao()
            PhoneModeStore.setRecordingBlocked(this, false) // it worked, so the phone allows it
            ioScope.launch { dao.insert(entity) }
            Toast.makeText(this, "Recording saved in Recordings/CallMind", Toast.LENGTH_SHORT).show()
        }
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        // Safety net: never leave the microphone open.
        monitor?.cancel()
        recorder?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        recorder = null
        if (CallRecorder.state.value.recording) CallRecorder.setState(CallRecorder.State())
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, CallRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_callmind)
            .setContentTitle("Recording call")
            .setContentText(name ?: number.ifBlank { "Unknown number" })
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stop)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    companion object {
        const val ACTION_START = "com.example.callmind.action.RECORD_START"
        const val ACTION_STOP = "com.example.callmind.action.RECORD_STOP"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"
        const val EXTRA_INCOMING = "incoming"
        private const val CHANNEL = "call_recording_channel"
        private const val NOTIFICATION_ID = 7002
        private const val TAG = "CallRecordService"

        const val BLOCKED_MESSAGE =
            "Your phone does not let apps like CallMind record call audio: during a call Android silences every " +
                "app except the phone's own call app, so nothing was saved.\n\n" +
                "To record calls, use the phone's own call recording (Phone > Settings > Call recording > " +
                "Record all calls automatically). That needs the phone's own dialer to make the call - so turn " +
                "\"Use CallMind as my phone app\" off for those calls. CallMind then lists the recordings and " +
                "plays them in each call's details."

        private fun sourceName(source: Int) = when (source) {
            MediaRecorder.AudioSource.MIC -> "MIC"
            MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
            MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
            MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
            else -> "source $source"
        }

        /**
         * Audio sources to try, in order. The phone's own recorder uses VOICE_CALL (both sides of the call) -
         * normally reserved for system apps - so it is tried first; the microphone modes follow.
         */
        private val SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.MIC
        )
    }
}
