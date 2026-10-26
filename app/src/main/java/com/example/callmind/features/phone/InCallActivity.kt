@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.callmind.features.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.CallAudioState
import android.telephony.PhoneNumberUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.core.phone.CallInfo
import com.example.callmind.core.phone.CallManager
import com.example.callmind.core.phone.CallRecorder
import com.example.callmind.core.phone.CallUiState
import com.example.callmind.core.phone.EndedCall
import com.example.callmind.core.phone.PhoneModeStore
import com.example.callmind.data.local.entities.ReminderEntity
import com.example.callmind.data.local.entities.ReminderType
import com.example.callmind.features.contacts.ContactAvatar
import com.example.callmind.features.notes.AddNoteSheet
import com.example.callmind.features.notes.NoteViewModel
import com.example.callmind.features.reminders.ReminderViewModel
import com.example.callmind.ui.theme.CallMindTheme
import com.example.callmind.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val QUICK_DECLINE_MESSAGES = listOf(
    "Can't talk right now. I'll call you back.",
    "I'm in a meeting. I'll call you later.",
    "Please send me a message instead.",
    "I'm driving. I'll call you when I stop."
)

/** "Call me back" choices of the Reminder button on the incoming screen: label to minutes. */
private val REMIND_CHOICES = listOf("In 5 minutes" to 5, "In 15 minutes" to 15, "In 30 minutes" to 30, "In 1 hour" to 60)

/**
 * Colours of the call screens, taken from the phone's own call app: a near-black page in dark mode and a
 * pale grey-lilac page in light mode, white / 90 % black text, grey secondary text, one blue accent
 * for switched-on buttons, and a soft red "end call" key.
 */
private class CallColors(val dark: Boolean) {
    val page = if (dark) Color(0xFF0E0E10) else Color(0xFFECECF1)
    val text = if (dark) Color.White else Color(0xE6000000)
    val secondary = if (dark) Color(0xA6FFFFFF) else Color(0x99000000)
    val disabled = if (dark) Color(0x4DFFFFFF) else Color(0x4D000000)
    val accent = Color(0xFF0F77FF)
    val end = Color(0xFFF5524D)
    val answer = Color(0xFF00C566)
    val surface = if (dark) Color(0x26FFFFFF) else Color(0x14000000)
}

/** The in-call screen shown while CallMind is the phone app. */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {
    private val noteViewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen and wake the display, like any call screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            CallMindTheme {
                InCallScreen(noteViewModel = noteViewModel, onFinish = { finish() })
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    }
}

@Composable
private fun InCallScreen(noteViewModel: NoteViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.isDark
    val colors = remember(isDark) { CallColors(isDark) }
    val reminderViewModel: ReminderViewModel = hiltViewModel()
    val calls by CallManager.calls.collectAsState()
    val ended by CallManager.ended.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val canAddCall by CallManager.canAddCall.collectAsState()
    val recording by CallRecorder.state.collectAsState()
    val recordingFailure by CallRecorder.failure.collectAsState()

    val primary = calls.firstOrNull { it.state == CallUiState.RINGING }
        ?: calls.firstOrNull { it.state != CallUiState.HOLDING }
        ?: calls.firstOrNull()
    val other = calls.firstOrNull { it.id != primary?.id }
    val shown = primary ?: ended?.info

    var showKeypad by remember { mutableStateOf(false) }
    var showAddCall by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showDeclineMessages by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var showRoutes by remember { mutableStateOf(false) }

    // Close shortly after the last call ends, leaving a moment to read "Call ended".
    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            delay(if (ended != null) 1200 else 300)
            onFinish()
        }
    }

    // Keep the screen dark against the ear while talking on the earpiece.
    val proximityNeeded = primary != null &&
        (primary.state == CallUiState.ACTIVE || primary.state == CallUiState.DIALING) &&
        audio.route == CallAudioState.ROUTE_EARPIECE && !showKeypad && !showAddCall && !showNote
    ProximityScreenOff(proximityNeeded)

    // System bar icons follow the page colour.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !colors.dark
            isAppearanceLightNavigationBars = !colors.dark
        }
    }

    fun startRecording(call: CallInfo) {
        if (!CallRecorder.start(context, call)) {
            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val call = primary
        if (granted && call != null) startRecording(call)
        else Toast.makeText(context, "Allow the Microphone permission to record calls", Toast.LENGTH_LONG).show()
    }
    fun toggleRecording() {
        val call = primary ?: return
        when {
            recording.recording -> CallRecorder.stop(context)
            !PhoneModeStore.hasRecordConsent(context) -> showConsent = true
            !CallRecorder.hasMicPermission(context) -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> startRecording(call)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.page)) {
        // Vivid while a call is ringing; calmer once it is connected so the buttons stay readable.
        val ringing = primary?.state == CallUiState.RINGING
        RibbonBackground(colors.dark, intensity = if (ringing) 1f else if (colors.dark) 0.35f else 0.75f)

        if (shown != null) {
            val isRinging = primary?.state == CallUiState.RINGING
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                if (other != null && !isRinging) {
                    OtherCallCard(other, colors, onSwap = { CallManager.swapTo(other.id) })
                }
                CallHeader(shown, statusText(shown, primary == null, ended, recording.recording), colors)
                Spacer(modifier = Modifier.weight(1f))

                when {
                    primary == null -> Unit // "Call ended"
                    isRinging && other != null -> CallWaitingChoices(
                        colors = colors,
                        onHoldAndAccept = { CallManager.answer(primary.id) },
                        onHangUpAndAccept = {
                            CallManager.disconnect(other.id)
                            CallManager.answer(primary.id)
                        },
                        onDecline = { CallManager.reject(primary.id) }
                    )
                    isRinging -> IncomingControls(
                        colors = colors,
                        onAnswer = { CallManager.answer(primary.id) },
                        onDecline = { CallManager.reject(primary.id) },
                        onReminder = { showReminder = true },
                        onReply = { showDeclineMessages = true }
                    )
                    else -> ActiveControls(
                        colors = colors,
                        call = primary,
                        otherCall = other,
                        audio = audio,
                        recording = recording.recording,
                        canAddCall = canAddCall,
                        onMute = { CallManager.setMuted(!audio.muted) },
                        onKeypad = { showKeypad = true },
                        onSpeaker = {
                            if (audio.hasBluetooth || audio.hasWiredHeadset) showRoutes = true
                            else CallManager.setRoute(
                                if (audio.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
                            )
                        },
                        onAddCall = { showAddCall = true },
                        onHold = { if (primary.state == CallUiState.HOLDING) CallManager.unhold(primary.id) else CallManager.hold(primary.id) },
                        onRecord = ::toggleRecording,
                        onNotes = { showNote = true },
                        onMerge = { CallManager.merge(primary.id) },
                        onEnd = { CallManager.disconnect(primary.id) }
                    )
                }
            }
        }

        if (showKeypad && primary != null) {
            KeypadOverlay(
                colors = colors,
                onKey = { key ->
                    scope.launch {
                        CallManager.playDtmf(primary.id, key)
                        delay(140)
                        CallManager.stopDtmf(primary.id)
                    }
                },
                onClose = { showKeypad = false }
            )
        }
        if (showAddCall) {
            AddCallOverlay(colors = colors, onClose = { showAddCall = false }, onPlaced = { showAddCall = false })
        }
    }

    if (showNote && shown != null) {
        AddNoteSheet(
            phoneNumber = shown.number,
            onSave = { phone, content ->
                noteViewModel.saveNote(phone, content)
                Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                showNote = false
            },
            onDismiss = { showNote = false }
        )
    }

    if (showDeclineMessages && primary != null) {
        ModalBottomSheet(
            onDismissRequest = { showDeclineMessages = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Decline and send a message",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                QUICK_DECLINE_MESSAGES.forEach { text ->
                    Text(
                        text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                CallManager.reject(primary.id, text)
                                showDeclineMessages = false
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    if (showReminder && primary != null) {
        ModalBottomSheet(
            onDismissRequest = { showReminder = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Remind me to call back",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                REMIND_CHOICES.forEach { (label, minutes) ->
                    Text(
                        label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                reminderViewModel.insertReminder(
                                    ReminderEntity(
                                        title = "Call back ${primary.displayName}",
                                        scheduledTime = System.currentTimeMillis() + minutes * 60_000L,
                                        phoneNumber = primary.number.ifBlank { null },
                                        type = ReminderType.CALL
                                    )
                                )
                                Toast.makeText(context, "Reminder set: $label", Toast.LENGTH_SHORT).show()
                                CallManager.reject(primary.id)
                                showReminder = false
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    if (showRoutes) {
        ModalBottomSheet(
            onDismissRequest = { showRoutes = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                RouteRow(Icons.Default.Smartphone, "Phone", audio.route == CallAudioState.ROUTE_EARPIECE) {
                    CallManager.setRoute(CallAudioState.ROUTE_EARPIECE); showRoutes = false
                }
                RouteRow(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", audio.route == CallAudioState.ROUTE_SPEAKER) {
                    CallManager.setRoute(CallAudioState.ROUTE_SPEAKER); showRoutes = false
                }
                if (audio.hasWiredHeadset) {
                    RouteRow(Icons.Default.Headset, "Wired headset", audio.route == CallAudioState.ROUTE_WIRED_HEADSET) {
                        CallManager.setRoute(CallAudioState.ROUTE_WIRED_HEADSET); showRoutes = false
                    }
                }
                if (audio.hasBluetooth) {
                    RouteRow(Icons.Default.Bluetooth, "Bluetooth", audio.route == CallAudioState.ROUTE_BLUETOOTH) {
                        CallManager.setRoute(CallAudioState.ROUTE_BLUETOOTH); showRoutes = false
                    }
                }
            }
        }
    }

    recordingFailure?.let { message ->
        AlertDialog(
            onDismissRequest = { CallRecorder.clearFailure() },
            title = { Text("Recording is not possible here") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    CallRecorder.clearFailure()
                    openPhoneRecordingSettings(context)
                }) { Text("Phone's recording settings") }
            },
            dismissButton = { TextButton(onClick = { CallRecorder.clearFailure() }) { Text("OK") } }
        )
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Record this call?") },
            text = {
                Text(
                    "Recording phone calls may need the other person's consent where you live. " +
                        "CallMind records through the microphone, so the other person can be heard only when the call is on speaker."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    PhoneModeStore.setRecordConsent(context)
                    showConsent = false
                    toggleRecording()
                }) { Text("Record") }
            },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProximityScreenOff(needed: Boolean) {
    val context = LocalContext.current
    DisposableEffect(needed) {
        val power = context.getSystemService(PowerManager::class.java)
        val lock = if (needed && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "callmind:proximity").apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
        } else null
        onDispose { if (lock?.isHeld == true) lock.release() }
    }
}

/** "SIM 1 Incoming call", "SIM 1 Ringing", "SIM 1 00:12", "On hold", "Call ended - 01:48". */
@Composable
private fun statusText(call: CallInfo, isEnded: Boolean, ended: EndedCall?, recording: Boolean): String {
    val sim = call.simLabel?.let { "$it " }.orEmpty()
    if (isEnded && ended != null) {
        return if (ended.durationSeconds > 0) "${ended.reason} • ${formatDuration(ended.durationSeconds)}" else ended.reason
    }
    val core = when (call.state) {
        CallUiState.RINGING -> "Incoming call"
        CallUiState.DIALING -> "Ringing"
        CallUiState.DISCONNECTING -> "Ending…"
        CallUiState.HOLDING -> "On hold"
        else -> if (call.connectTimeMillis > 0) rememberElapsed(call.connectTimeMillis) else "Connecting…"
    }
    return sim + core
}

@Composable
private fun rememberElapsed(since: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return formatDuration(((now - since) / 1000).coerceAtLeast(0))
}

/** "+91 95143 15656" style, using the SIM's country so a plain local number is grouped the local way. */
private fun prettyNumber(context: Context, number: String): String = try {
    val telephony = context.getSystemService(android.telephony.TelephonyManager::class.java)
    val country = telephony?.simCountryIso?.takeIf { it.isNotBlank() }?.uppercase() ?: Locale.getDefault().country
    PhoneNumberUtils.formatNumber(number, country) ?: number
} catch (e: Exception) {
    number
}

/**
 * The block at the top of every call screen, as in the phone's own: the name in 36 sp medium, then the
 * number and where it is registered in 16 sp grey, then a small network tag and "SIM 1 <status>".
 */
@Composable
private fun CallHeader(call: CallInfo, status: String, colors: CallColors) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp, start = 26.dp, end = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            call.displayName,
            fontSize = if (call.displayName.length > 18) 28.sp else 36.sp,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        val showNumber = call.name != null && call.number.isNotBlank()
        if (showNumber || !call.location.isNullOrBlank()) {
            Row(modifier = Modifier.widthIn(max = 310.dp), horizontalArrangement = Arrangement.Center) {
                if (showNumber) {
                    Text(prettyNumber(LocalContext.current, call.number), fontSize = 16.sp, color = colors.secondary, maxLines = 1)
                }
                if (!call.location.isNullOrBlank()) {
                    if (showNumber) Spacer(modifier = Modifier.width(15.dp))
                    Text(
                        call.location,
                        fontSize = 16.sp,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tag = when {
                call.isWifi -> "VoWiFi"
                call.isHd -> "HD"
                else -> null
            }
            if (tag != null) {
                Text(
                    tag,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (colors.dark) Color.White else Color(0xFF2B2B2B))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (colors.dark) Color(0xFF111111) else Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(status, fontSize = 18.sp, color = colors.secondary)
        }
    }
}

/**
 * The soft three-band "ribbon" artwork behind the call, in the same colours as the phone's call
 * wallpaper idea: vivid purple and blue on black in dark mode, pale lilac on grey in light mode.
 * Drawn here as plain shapes.
 */
@Composable
private fun RibbonBackground(dark: Boolean, intensity: Float = 1f) {
    Canvas(modifier = Modifier.fillMaxSize().alpha(intensity)) {
        val u = size.width / 720f
        val v = size.height / 1608f
        val length = 580f * u
        val thickness = 285f * u
        fun band(centerY: Float, from: Color, to: Color) {
            val cx = size.width / 2f
            val cy = centerY * v
            val left = cx - length / 2f
            val top = cy - thickness / 2f
            rotate(30f, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(from, to),
                        start = Offset(left, top + thickness),
                        end = Offset(left + length, top)
                    ),
                    topLeft = Offset(left, top),
                    size = Size(length, thickness),
                    cornerRadius = CornerRadius(thickness / 2f)
                )
            }
        }
        if (dark) {
            band(540f, Color(0xFFB791E8), Color(0x00B791E8))
            band(810f, Color(0xFF5B4FD3), Color(0xFFB4A5EC))
            band(1060f, Color(0xFF0B1B52), Color(0xFF3550B8))
        } else {
            band(540f, Color(0xFFD9D4EE), Color(0x00D9D4EE))
            band(810f, Color(0xFFCFCFEA), Color(0xFFC3C5E6))
            band(1060f, Color(0xFFD6D6EA), Color(0xFFCACBE4))
        }
    }
}

@Composable
private fun OtherCallCard(other: CallInfo, colors: CallColors, onSwap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onSwap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Pause, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(other.displayName, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (other.state == CallUiState.HOLDING) "On hold • tap to swap" else "Tap to swap",
            color = colors.secondary,
            fontSize = 13.sp
        )
    }
}

// ---- incoming ------------------------------------------------------------------------------

/** Swipe the ring down to answer or up to decline (as the phone's own); Reminder and Reply sit below. */
@Composable
private fun IncomingControls(
    colors: CallColors,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onReminder: () -> Unit,
    onReply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        SwipeToAnswer(colors, onAnswer, onDecline)
        Spacer(modifier = Modifier.height(30.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
            QuickAction(Icons.Outlined.Alarm, "Reminder", colors, onReminder)
            Spacer(modifier = Modifier.weight(1f))
            QuickAction(Icons.Outlined.Sms, "Reply", colors, onReply)
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, colors: CallColors, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(99.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = colors.secondary, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 14.sp, color = colors.secondary)
    }
}

@Composable
private fun SwipeToAnswer(colors: CallColors, onAnswer: () -> Unit, onDecline: () -> Unit) {
    val density = LocalDensity.current
    val trigger = with(density) { 84.dp.toPx() }
    val limit = with(density) { 120.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pulse = rememberInfiniteTransition(label = "swipeHint")
    val hint by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "hint"
    )
    val toAnswer = (offset.value / trigger).coerceIn(0f, 1f)
    val toDecline = (-offset.value / trigger).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                offset.value >= trigger -> onAnswer()
                                offset.value <= -trigger -> onDecline()
                            }
                            offset.animateTo(0f, tween(180))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } }
                ) { change, dy ->
                    change.consume()
                    scope.launch { offset.snapTo((offset.value + dy).coerceIn(-limit, limit)) }
                }
            }
    ) {
        // Decline target (top) and answer target (bottom): the handset glyph with a short bar underneath.
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).scale(1f + 0.25f * toDecline),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CallEnd,
                contentDescription = "Decline",
                tint = colors.end,
                modifier = Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onDecline)
            )
            Box(modifier = Modifier.width(18.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.end))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).scale(1f + 0.25f * toAnswer),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Answer",
                tint = colors.answer,
                modifier = Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onAnswer)
            )
            Box(modifier = Modifier.width(18.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.answer))
        }

        // The ring, with a faint arc above and below that pulses to show where it can go.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .size(width = 70.dp, height = 110.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val arc = colors.secondary.copy(alpha = hint * (1f - abs(offset.value) / limit))
                val w = size.width * 0.5f
                drawArc(arc, 200f, 140f, false, Offset((size.width - w) / 2f, 0f), Size(w, w * 0.5f), style = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawArc(arc, 20f, 140f, false, Offset((size.width - w) / 2f, size.height - w * 0.5f), Size(w, w * 0.5f), style = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawCircle(colors.text, radius = 20.dp.toPx(), center = center, style = Stroke(4.dp.toPx()))
            }
        }
    }
}

/** Second call ringing during a call: the three choices of the phone's own call-waiting screen. */
@Composable
private fun CallWaitingChoices(colors: CallColors, onHoldAndAccept: () -> Unit, onHangUpAndAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        WaitingButton("Hold & accept", colors.text, colors.surface, onHoldAndAccept)
        WaitingButton("Hang up & accept", colors.text, colors.surface, onHangUpAndAccept)
        WaitingButton("Decline", Color.White, colors.end, onDecline)
    }
}

@Composable
private fun WaitingButton(label: String, textColor: Color, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 16.sp, color = textColor) }
}

// ---- on a call -----------------------------------------------------------------------------

/**
 * Six plain buttons (icon over label, no circles) in two rows of three, then keypad - big end key -
 * speaker along the bottom, as in the phone's own call screen.
 */
@Composable
private fun ActiveControls(
    colors: CallColors,
    call: CallInfo,
    otherCall: CallInfo?,
    audio: com.example.callmind.core.phone.AudioInfo,
    recording: Boolean,
    canAddCall: Boolean,
    onMute: () -> Unit,
    onKeypad: () -> Unit,
    onSpeaker: () -> Unit,
    onAddCall: () -> Unit,
    onHold: () -> Unit,
    onRecord: () -> Unit,
    onNotes: () -> Unit,
    onMerge: () -> Unit,
    onEnd: () -> Unit
) {
    val onHoldNow = call.state == CallUiState.HOLDING
    val speakerOn = audio.route == CallAudioState.ROUTE_SPEAKER
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 36.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp)) {
            ControlItem(Icons.Outlined.Description, "Notes", colors, onClick = onNotes)
            ControlItem(Icons.Outlined.AddCircleOutline, "Add call", colors, enabled = canAddCall, onClick = onAddCall)
            ControlItem(Icons.Outlined.MicOff, "Mute", colors, active = audio.muted, onClick = onMute)
        }
        Spacer(modifier = Modifier.height(30.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp)) {
            ControlItem(
                Icons.Default.Voicemail,
                if (recording) "Recording" else "Record",
                colors,
                active = recording,
                onClick = onRecord
            )
            if (otherCall != null && call.canMerge) {
                ControlItem(Icons.Default.CallMerge, "Merge", colors, onClick = onMerge)
            } else {
                ControlItem(Icons.Outlined.Videocam, "Video call", colors, enabled = false, onClick = {})
            }
            ControlItem(
                if (onHoldNow) Icons.Outlined.PlayCircleOutline else Icons.Outlined.PauseCircleOutline,
                if (onHoldNow) "Resume" else "Hold",
                colors,
                active = onHoldNow,
                enabled = call.canHold,
                onClick = onHold
            )
        }
        Spacer(modifier = Modifier.height(44.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).padding(start = 33.dp), contentAlignment = Alignment.CenterStart) {
                Icon(
                    Icons.Default.Dialpad,
                    contentDescription = "Keypad",
                    tint = colors.text,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onKeypad)
                )
            }
            Box(
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(22.dp)).background(colors.end).clickable(onClick = onEnd),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(30.dp)) }
            Box(modifier = Modifier.weight(1f).padding(end = 33.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    when (audio.route) {
                        CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.Bluetooth
                        CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Default.Headset
                        else -> Icons.AutoMirrored.Outlined.VolumeUp
                    },
                    contentDescription = "Speaker",
                    tint = if (speakerOn) colors.accent else colors.text,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onSpeaker)
                )
            }
        }
    }
}

@Composable
private fun RowScope.ControlItem(
    icon: ImageVector,
    label: String,
    colors: CallColors,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val tint = when {
        !enabled -> colors.disabled
        active -> colors.accent
        else -> colors.text
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 15.sp, color = tint, maxLines = 1)
    }
}

@Composable
private fun RouteRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val green = Color(0xFF2EC25B)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) green else MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.width(20.dp))
        Text(label, fontSize = 16.sp, color = if (selected) green else MaterialTheme.colorScheme.onSurface)
    }
}

/** DTMF keypad over the call: the digits go to the other side as tones. */
@Composable
private fun KeypadOverlay(colors: CallColors, onKey: (Char) -> Unit, onClose: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val playTone = rememberDialTones(PhoneModeStore.dialTones.value)
    Column(
        modifier = Modifier.fillMaxSize().background(colors.page).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            typed.ifEmpty { " " },
            fontSize = 32.sp,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.height(48.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        DialPad(
            onKey = { key ->
                if (key != '+') {
                    typed += key
                    playTone(key)
                    onKey(key)
                }
            },
            keyHeight = 70.dp,
            textColor = colors.text,
            subColor = colors.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onClose) { Text("Hide keypad", color = colors.text, fontSize = 16.sp) }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** Dial a second number while the first call is held by the phone. */
@Composable
private fun AddCallOverlay(colors: CallColors, onClose: () -> Unit, onPlaced: () -> Unit, viewModel: DialerViewModel = hiltViewModel()) {
    val placeCall = rememberPlaceCall()
    val digits by viewModel.digits.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val playTone = rememberDialTones(PhoneModeStore.dialTones.value)
    DisposableEffect(Unit) { onDispose { viewModel.clear() } }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.page).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Add call", color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            digits.ifEmpty { " " },
            fontSize = 32.sp,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.height(48.dp)
        )
        // Contacts matching what is typed (by name on the keypad letters, or by number).
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(matches, key = { it.contactId }) { match ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = match.number != null) { match.number?.let { viewModel.set(it) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(name = match.name, photoUri = match.photoUri, size = 40.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(match.name, color = colors.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(match.number.orEmpty(), color = colors.secondary, fontSize = 14.sp)
                    }
                }
            }
        }
        DialPad(
            onKey = { key ->
                playTone(key)
                viewModel.append(key)
            },
            keyHeight = 66.dp,
            textColor = colors.text,
            subColor = colors.secondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, modifier = Modifier.width(80.dp)) { Text("Cancel", color = colors.text) }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(68.dp).clip(CircleShape).background(colors.answer).clickable(enabled = digits.isNotEmpty()) {
                        placeCall(digits, null)
                        onPlaced()
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
            IconButton(onClick = { viewModel.backspace() }, enabled = digits.isNotEmpty(), modifier = Modifier.width(80.dp)) {
                if (digits.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = colors.text)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Opens the phone's own call-recording settings (vivo / OriginOS); says so when this phone has none. */
internal fun openPhoneRecordingSettings(context: Context) {
    try {
        context.startActivity(
            Intent("com.android.phone.VivoCallRecordSetting").addCategory(Intent.CATEGORY_DEFAULT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Open Phone > Settings > Call recording", Toast.LENGTH_LONG).show()
    }
}
