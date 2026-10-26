package com.example.callmind.features.postcall

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.core.util.formatDuration
import com.example.callmind.core.util.formatRelativeTime
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.local.entities.RuleType
import com.example.callmind.features.blocking.CallBlockingViewModel
import com.example.callmind.features.contacts.ContactAvatar
import com.example.callmind.features.notes.AddNoteSheet
import com.example.callmind.features.notes.NoteViewModel
import com.example.callmind.features.quickreply.QuickReplyPickerSheet
import com.example.callmind.features.quickreply.QuickReplyViewModel
import com.example.callmind.features.reminders.AddReminderSheet
import com.example.callmind.features.reminders.ReminderViewModel
import com.example.callmind.ui.theme.AccentSchedule
import com.example.callmind.ui.theme.AccentWhatsApp
import com.example.callmind.ui.theme.CallMindTheme
import com.example.callmind.ui.theme.contactAccent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PostCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val intentNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        setContent {
            val context = LocalContext.current
            val postCallViewModel: PostCallViewModel = hiltViewModel()
            val noteViewModel: NoteViewModel = hiltViewModel()
            val reminderViewModel: ReminderViewModel = hiltViewModel()
            val quickReplyViewModel: QuickReplyViewModel = hiltViewModel()
            val blockingViewModel: CallBlockingViewModel = hiltViewModel()
            val lastCall by postCallViewModel.lastCall.collectAsState()

            LaunchedEffect(intentNumber) { postCallViewModel.loadLastCall(intentNumber) }

            // Android rarely reports the number when a call ends, so fall back to the call log.
            val phoneNumber = intentNumber?.takeIf { it.isNotBlank() }
                ?: lastCall?.phoneNumber?.takeIf { it != "Unknown" }
                ?: ""

            // Hand over to another app (dialer, WhatsApp...) and close this card so it isn't waiting
            // behind that app when the user comes back.
            fun launchAndClose(action: () -> Unit) {
                action()
                finish()
            }

            CallMindTheme {
                PostCallContent(
                    phoneNumber = phoneNumber,
                    contactName = lastCall?.contactName,
                    lastCall = lastCall,
                    onDismiss = { finish() },
                    onCallAgain = { launchAndClose { postCallViewModel.callAgain(phoneNumber) } },
                    onWhatsApp = { launchAndClose { postCallViewModel.openWhatsApp(phoneNumber) } },
                    onTelegram = { launchAndClose { postCallViewModel.openTelegram(phoneNumber) } },
                    onSMS = { launchAndClose { postCallViewModel.openSMS(phoneNumber) } },
                    onGPay = { launchAndClose { postCallViewModel.openGPay() } },
                    onViewContact = { launchAndClose { postCallViewModel.openContact(phoneNumber) } },
                    onBlock = {
                        blockingViewModel.addRule(RuleType.BLOCK, phoneNumber, lastCall?.contactName) { added ->
                            Toast.makeText(
                                context,
                                if (added) "Added to blocklist" else "Already on your blocklist",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onSaveNote = { phone, content -> noteViewModel.saveNote(phone, content) },
                    reminderViewModel = reminderViewModel,
                    quickReplyViewModel = quickReplyViewModel,
                    onSendQuickReply = { message -> launchAndClose { postCallViewModel.openWhatsApp(phoneNumber, message) } }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
    }
}

/** "Missed call • 2 min ago", "Incoming • 05:31", "Not answered • Just now" ... */
private fun statusLine(call: CallRecordEntity?): String? {
    call ?: return null
    val whenText = formatRelativeTime(call.timestamp)
    return when {
        call.type == CallType.MISSED -> "Missed call • $whenText"
        call.durationSeconds <= 0L -> "Not answered • $whenText"
        call.type == CallType.OUTGOING -> "Outgoing • ${formatDuration(call.durationSeconds)}"
        else -> "Incoming • ${formatDuration(call.durationSeconds)}"
    }
}

/**
 * Compact "call ended" card that rises from the bottom. The four things you most often do right
 * after a call are big round buttons; everything else sits one tap away.
 */
@Composable
fun PostCallContent(
    phoneNumber: String,
    contactName: String?,
    lastCall: CallRecordEntity?,
    onDismiss: () -> Unit,
    onCallAgain: () -> Unit,
    onWhatsApp: () -> Unit,
    onTelegram: () -> Unit,
    onSMS: () -> Unit,
    onGPay: () -> Unit,
    onViewContact: () -> Unit,
    onBlock: () -> Unit,
    onSaveNote: (String, String) -> Unit,
    reminderViewModel: ReminderViewModel,
    quickReplyViewModel: QuickReplyViewModel,
    onSendQuickReply: (String) -> Unit
) {
    var showAddNote by remember { mutableStateOf(false) }
    var showAddReminder by remember { mutableStateOf(false) }
    var showQuickReply by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val quickReplies by quickReplyViewModel.replies.collectAsState()

    // Dimmed backdrop: tapping outside the card closes it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Swallow taps on the card so they don't reach the backdrop.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)
            ) {
                // Who, and how the call went
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactAvatar(name = contactName, photoUri = null, size = 52.dp, textSize = 22.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contactName ?: phoneNumber.ifBlank { "Unknown number" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = listOfNotNull(
                            phoneNumber.takeIf { contactName != null && it.isNotBlank() },
                            statusLine(lastCall)
                        ).joinToString("  ·  ")
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // The four most-used actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PrimaryAction(Icons.Default.Phone, "WhatsApp", fill = AccentWhatsApp, tint = Color.White, onClick = onWhatsApp)
                    PrimaryAction(Icons.Default.Star, "Quick reply", fill = AccentSchedule, tint = Color.White) { showQuickReply = true }
                    PrimaryAction(Icons.Default.Call, "Call", fill = MaterialTheme.colorScheme.background, tint = MaterialTheme.colorScheme.contactAccent, onClick = onCallAgain)
                    PrimaryAction(Icons.Default.Email, "Message", fill = MaterialTheme.colorScheme.background, tint = MaterialTheme.colorScheme.onSurface, onClick = onSMS)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Follow-ups, then everything else behind "More"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryChip(Icons.Default.Edit, "Add note", Modifier.weight(1f)) { showAddNote = true }
                    SecondaryChip(Icons.Default.Notifications, "Remind me", Modifier.weight(1f)) { showAddReminder = true }
                    Box {
                        SecondaryChip(Icons.Default.MoreVert, "More") { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("View contact") }, onClick = { menuOpen = false; onViewContact() })
                            DropdownMenuItem(text = { Text("Telegram") }, onClick = { menuOpen = false; onTelegram() })
                            DropdownMenuItem(text = { Text("Google Pay") }, onClick = { menuOpen = false; onGPay() })
                            DropdownMenuItem(
                                text = { Text("Block number", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; onBlock() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQuickReply) {
        QuickReplyPickerSheet(
            replies = quickReplies,
            recipientLabel = contactName ?: phoneNumber.ifBlank { "this number" },
            onSend = { reply ->
                onSendQuickReply(reply.message)
                showQuickReply = false
            },
            onSave = { id, title, message -> quickReplyViewModel.save(id, title, message) },
            onDelete = { quickReplyViewModel.delete(it) },
            onDismiss = { showQuickReply = false }
        )
    }

    if (showAddNote) {
        AddNoteSheet(
            phoneNumber = phoneNumber,
            onSave = { phone, content ->
                onSaveNote(phone, content)
                showAddNote = false
                onDismiss()
            },
            onDismiss = { showAddNote = false }
        )
    }

    if (showAddReminder) {
        AddReminderSheet(
            phoneNumber = phoneNumber,
            contactName = contactName,
            onReminderSaved = {
                showAddReminder = false
                onDismiss()
            },
            onDismiss = { showAddReminder = false },
            viewModel = reminderViewModel
        )
    }
}

/** Round button with its label underneath. */
@Composable
private fun PrimaryAction(icon: ImageVector, label: String, fill: Color, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(76.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(fill),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Small pill with an icon and a short label. */
@Composable
private fun SecondaryChip(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
            if (label != "More") {
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrimaryActionPreview() {
    CallMindTheme {
        Row { PrimaryAction(Icons.Default.Phone, "WhatsApp", AccentWhatsApp, Color.White) {} }
    }
}
