package com.example.callmind.features.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.blocking.BlockDecision
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.repository.RecordingItem
import com.example.callmind.data.repository.RecordingsRepository
import com.example.callmind.data.system.CallExtra
import com.example.callmind.data.system.CallLogProvider
import com.example.callmind.ui.theme.AccentRed
import com.example.callmind.ui.theme.contactAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** The calls with a person, read from the phone's call log (for the contact page). */
@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val callLogProvider: CallLogProvider
) : ViewModel() {
    private val _calls = MutableStateFlow<List<CallRecordEntity>>(emptyList())
    val calls: StateFlow<List<CallRecordEntity>> = _calls

    val extras = callLogProvider.extras

    private var numbers: List<String> = emptyList()

    fun load(numbers: List<String>) {
        this.numbers = numbers
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            if (numbers.isEmpty()) {
                _calls.value = emptyList()
                return@launch
            }
            _calls.value = callLogProvider.getCallLogs().filter { call ->
                numbers.any { BlockDecision.numbersMatch(it, call.phoneNumber) }
            }
        }
    }

    init {
        viewModelScope.launch { callLogProvider.changes.collect { reload() } }
    }
}

/** What the recordings column of a call row needs to show and play. */
class RecordingsUi(
    val items: List<RecordingItem>,
    val playingKey: String?,
    val progress: Float,
    val hasPhoneAccess: Boolean,
    val onPlay: (RecordingItem) -> Unit,
    val onRequestAccess: () -> Unit
)

fun callSummary(call: CallRecordEntity): String {
    val seconds = call.durationSeconds
    val duration = when {
        seconds <= 0 -> ""
        seconds < 60 -> "$seconds sec"
        else -> "${seconds / 60} min ${seconds % 60} sec"
    }
    return when (call.type) {
        CallType.MISSED -> "Missed"
        CallType.OUTGOING -> if (seconds <= 0) "Not connected" else "Outgoing $duration"
        CallType.INCOMING -> if (seconds <= 0) "Not connected" else "Incoming $duration"
    }
}

private fun dayLabel(timestamp: Long): String {
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (sameDay(now, then)) return "Today $time"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(now, then)) return "Yesterday $time"
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp)) + " " + time
}

/**
 * The "Call logs" list of the phone's own contact page: each call with its date, number, what happened and how
 * long it lasted - and a play button on the calls that were recorded.
 */
@Composable
fun CallHistoryRows(
    calls: List<CallRecordEntity>,
    extras: Map<String, CallExtra>,
    recordings: RecordingsUi
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!recordings.hasPhoneAccess && calls.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Call recordings made by your phone are not shown yet.",
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = recordings.onRequestAccess) {
                    Text("Allow", color = MaterialTheme.colorScheme.contactAccent)
                }
            }
        }
        calls.forEachIndexed { index, call ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            val recording = remember(call, recordings.items) { RecordingsRepository.forCall(call, recordings.items) }
            val extra = extras[CallLogProvider.extraKey(call.phoneNumber.takeIf { it != "Unknown" }, call.timestamp)]
            CallHistoryRow(call, extra, recording, recordings)
        }
    }
}

@Composable
private fun CallHistoryRow(call: CallRecordEntity, extra: CallExtra?, recording: RecordingItem?, recordings: RecordingsUi) {
    val missed = call.type == CallType.MISSED
    val playing = recording != null && recordings.playingKey == recording.key
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(dayLabel(call.timestamp), fontSize = 17.sp, color = if (missed) AccentRed else MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (extra?.simSlot != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .size(width = 18.dp, height = 16.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(extra.simSlot.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        call.phoneNumber.takeIf { it != "Unknown" } ?: "Unknown number",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (recording != null) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                            .clickable { recordings.onPlay(recording) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause recording" else "Play recording",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(callSummary(call), fontSize = 14.sp, color = if (missed) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (playing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { recordings.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.contactAccent
            )
        }
    }
}
