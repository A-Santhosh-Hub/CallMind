package com.example.callmind.features.phone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.callmind.core.permissions.rememberPermissionState
import com.example.callmind.core.phone.AutoRecordMode
import com.example.callmind.core.phone.PhoneModeStore
import com.example.callmind.data.repository.CALLMIND_RECORDING_FOLDER
import com.example.callmind.data.repository.PHONE_RECORDER_FOLDER
import com.example.callmind.data.repository.RecordingItem
import com.example.callmind.data.repository.RecordingsRepository
import com.example.callmind.ui.components.NavRow
import com.example.callmind.ui.components.SectionHint
import com.example.callmind.ui.components.SectionTitle
import com.example.callmind.ui.components.SettingsDivider
import com.example.callmind.ui.components.SettingsGroup
import com.example.callmind.ui.theme.AccentRed
import com.example.callmind.ui.theme.contactAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(onBack: () -> Unit, viewModel: RecordingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsState()
    val playingKey by viewModel.playing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val autoMode by PhoneModeStore.autoRecord.collectAsState()
    val blocked by PhoneModeStore.recordingBlocked.collectAsState()
    val audioAccess = rememberPermissionState(RecordingsRepository.accessPermission())
    var pendingMode by remember { mutableStateOf<AutoRecordMode?>(null) }
    var toDelete by remember { mutableStateOf<RecordingItem?>(null) }

    DisposableEffect(Unit) { onDispose { viewModel.stopPlayback() } }
    LaunchedEffect(audioAccess.isGranted) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Call recordings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 8.dp)
        ) {
            if (blocked) {
                SettingsGroup {
                    Column(modifier = Modifier.padding(15.dp)) {
                        Text("CallMind could not record your last call", style = MaterialTheme.typography.bodyLarge, color = AccentRed)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            com.example.callmind.core.phone.CallRecordService.BLOCKED_MESSAGE,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { PhoneModeStore.setRecordingBlocked(context, false) }) {
                            Text("Try again on the next call", color = MaterialTheme.colorScheme.contactAccent)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            SectionTitle("Recordings (${recordings.size})")
            if (!audioAccess.isGranted) {
                SettingsGroup {
                    NavRow(
                        title = "Show the phone's own call recordings",
                        subtitle = "Allow access to music and audio so recordings made by the phone's call recorder appear here",
                        onClick = { audioAccess.requestPermission() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (recordings.isEmpty()) {
                SettingsGroup {
                    Text(
                        "No recordings yet.",
                        modifier = Modifier.padding(15.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SettingsGroup {
                    recordings.forEachIndexed { index, recording ->
                        if (index > 0) SettingsDivider()
                        RecordingRow(
                            recording = recording,
                            playing = playingKey == recording.key,
                            progress = if (playingKey == recording.key) progress else 0f,
                            onPlay = { viewModel.toggle(recording) },
                            onShare = {
                                val intent = viewModel.shareIntent(recording)
                                if (intent != null) context.startActivity(intent)
                                else Toast.makeText(context, "The audio file is missing", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = if (recording.fromPhone) null else ({ toDelete = recording })
                        )
                    }
                }
            }
            SectionHint(
                "Recordings made by the phone's own call recorder are saved in $PHONE_RECORDER_FOLDER and " +
                    "CallMind's own in $CALLMIND_RECORDING_FOLDER (open the Files app, then Recordings). " +
                    "Each call's recording also has a play button in that call's details and on the contact's page."
            )

            SectionTitle("Phone's call recorder")
            SettingsGroup {
                NavRow(
                    title = "Open the phone's call recording settings",
                    subtitle = "Turn on \"Record all calls automatically\" there - it records both sides of a call",
                    onClick = { openPhoneRecordingSettings(context) }
                )
            }
            SectionHint(
                "Android does not let apps other than the phone's own call app record the other person's voice, and " +
                    "many phones block the microphone for apps during a mobile call. CallMind's Record button " +
                    "therefore only works when the phone allows it - if it does not, nothing is saved."
            )

            SectionTitle("Record automatically (CallMind)")
            SettingsGroup {
                AutoRecordMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                if (mode != AutoRecordMode.OFF && !PhoneModeStore.hasRecordConsent(context)) {
                                    pendingMode = mode
                                } else {
                                    PhoneModeStore.setAutoRecord(context, mode)
                                }
                            }
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mode.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(
                            selected = autoMode == mode,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.contactAccent)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    pendingMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingMode = null },
            title = { Text("Record calls automatically?") },
            text = {
                Text(
                    "Recording phone calls may need the other person's consent where you live. " +
                        "You are responsible for using this feature lawfully."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    PhoneModeStore.setRecordConsent(context)
                    PhoneModeStore.setAutoRecord(context, mode)
                    pendingMode = null
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { pendingMode = null }) { Text("Cancel") } }
        )
    }

    toDelete?.let { recording ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete recording?") },
            text = { Text("The audio file will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(recording)
                    toDelete = null
                }) { Text("Delete", color = AccentRed) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RecordingRow(
    recording: RecordingItem,
    playing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val sdf = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    var menu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.contactAccent)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val duration = if (recording.durationMs > 0) " • ${formatDuration(recording.durationMs / 1000)}" else ""
                val direction = when (recording.isIncoming) {
                    true -> " • Incoming"
                    false -> " • Outgoing"
                    null -> ""
                }
                Text(
                    "${sdf.format(Date(recording.startedAt))}$duration$direction",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (recording.fromPhone) "Phone's call recorder" else "CallMind",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.contactAccent
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
                    if (onDelete != null) {
                        DropdownMenuItem(text = { Text("Delete", color = AccentRed) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        if (playing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.contactAccent
            )
        }
    }
}
