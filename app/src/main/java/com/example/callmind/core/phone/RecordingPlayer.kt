package com.example.callmind.core.phone

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One shared player for call recordings, so a recording started from the contact page, the call details or the
 * recordings list is always the only one playing. Plays a content:// URI or a file path.
 */
object RecordingPlayer {
    private val _playing = MutableStateFlow<String?>(null)

    /** Key of the recording that is playing, or null. */
    val playing: StateFlow<String?> = _playing

    private val _progress = MutableStateFlow(0f)

    /** 0..1 position in the recording that is playing. */
    val progress: StateFlow<Float> = _progress

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Starts [key]; if it is already playing, stops it. */
    fun toggle(context: Context, key: String, uri: String) {
        if (_playing.value == key) {
            stop()
            return
        }
        stop()
        val p = MediaPlayer()
        try {
            if (uri.startsWith("content://")) p.setDataSource(context.applicationContext, Uri.parse(uri))
            else p.setDataSource(uri)
            p.prepare()
            p.setOnCompletionListener { stop() }
            p.start()
        } catch (e: Exception) {
            Log.w("RecordingPlayer", "Cannot play $uri", e)
            p.release()
            Toast.makeText(context, "Could not play this recording", Toast.LENGTH_SHORT).show()
            return
        }
        player = p
        _playing.value = key
        ticker = scope.launch {
            while (isActive) {
                val current = player ?: break
                _progress.value = current.currentPosition.toFloat() / current.duration.coerceAtLeast(1)
                delay(250)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        player = null
        _playing.value = null
        _progress.value = 0f
    }
}
