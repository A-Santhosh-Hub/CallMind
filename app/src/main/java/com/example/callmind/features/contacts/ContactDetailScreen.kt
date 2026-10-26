package com.example.callmind.features.contacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.ContactsContract
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.core.blocking.BlockDecision
import com.example.callmind.core.permissions.rememberPermissionState
import com.example.callmind.data.system.ContactNumber
import com.example.callmind.data.system.DeviceContact
import com.example.callmind.data.repository.RecordingsRepository
import com.example.callmind.features.notes.AddNoteSheet
import com.example.callmind.features.notes.NoteViewModel
import com.example.callmind.features.phone.CallHistoryRows
import com.example.callmind.features.phone.CallHistoryViewModel
import com.example.callmind.features.phone.RecordingsUi
import com.example.callmind.features.phone.RecordingsViewModel
import com.example.callmind.features.postcall.PostCallViewModel
import com.example.callmind.features.quickreply.QuickReplyPickerSheet
import com.example.callmind.features.quickreply.QuickReplyViewModel
import com.example.callmind.features.reminders.AddReminderSheet
import com.example.callmind.features.reminders.ReminderViewModel
import com.example.callmind.ui.components.NavRow
import com.example.callmind.ui.components.PageHorizontalPadding
import com.example.callmind.ui.components.SectionTitle
import com.example.callmind.ui.components.SettingsDivider
import com.example.callmind.ui.components.SettingsGroup
import com.example.callmind.ui.theme.AccentWhatsApp
import com.example.callmind.ui.theme.contactAccent
import com.example.callmind.ui.theme.tertiaryText

/** One person: call / message / WhatsApp buttons, plus CallMind's own tools (quick reply, notes, reminders, block). */
@Composable
fun ContactDetailScreen(
    contactId: Long,
    onBack: () -> Unit,
    viewModel: ContactViewModel = hiltViewModel(),
    postCallViewModel: PostCallViewModel = hiltViewModel(),
    noteViewModel: NoteViewModel = hiltViewModel(),
    reminderViewModel: ReminderViewModel = hiltViewModel(),
    quickReplyViewModel: QuickReplyViewModel = hiltViewModel(),
    callHistoryViewModel: CallHistoryViewModel = hiltViewModel(),
    recordingsViewModel: RecordingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val notes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    val quickReplies by quickReplyViewModel.replies.collectAsState()
    val writePermission = rememberPermissionState(Manifest.permission.WRITE_CONTACTS)
    val audioAccess = rememberPermissionState(RecordingsRepository.accessPermission())
    val recordingItems by recordingsViewModel.recordings.collectAsState()
    val playingKey by recordingsViewModel.playing.collectAsState()
    val playProgress by recordingsViewModel.progress.collectAsState()
    val calls by callHistoryViewModel.calls.collectAsState()
    val callExtras by callHistoryViewModel.extras.collectAsState()
    var showAllCalls by remember { mutableStateOf(false) }
    LaunchedEffect(audioAccess.isGranted) { recordingsViewModel.refresh() }
    DisposableEffect(Unit) { onDispose { recordingsViewModel.stopPlayback() } }

    val contact = contacts.firstOrNull { it.id == contactId }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showQuickReply by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(writePermission.isGranted) {
        if (writePermission.isGranted) {
            pendingAfterPermission?.invoke()
            pendingAfterPermission = null
        }
    }
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun requireWrite(action: () -> Unit) {
        if (writePermission.isGranted) action() else {
            pendingAfterPermission = action
            writePermission.requestPermission()
        }
    }

    // The contact was deleted (here or elsewhere): leave the screen.
    LaunchedEffect(loaded, contact) { if (loaded && contact == null) onBack() }
    if (contact == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    // Numbers are read when the page opens; the list itself does not carry them.
    val numbers by produceState(emptyList<ContactNumber>(), contact.id) { value = viewModel.numbersOf(contact.id) }
    val primaryNumber = numbers.firstOrNull()?.number.orEmpty()
    LaunchedEffect(numbers) { callHistoryViewModel.load(numbers.map { it.number }) }
    val contactNotes = notes.filter { note -> numbers.any { BlockDecision.numbersMatch(it.number, note.phoneNumber) } }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                requireWrite {
                    viewModel.setStarred(contact, !contact.starred) { ok ->
                        if (!ok) toast("Couldn't update favorites")
                    }
                }
            }) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = if (contact.starred) "Remove from favorites" else "Add to favorites",
                    tint = if (contact.starred) Color(0xFFFFB400) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            IconButton(onClick = {
                // Editing existing contacts is left to the phone's own editor, which handles every field.
                try {
                    context.startActivity(Intent(Intent.ACTION_EDIT).apply {
                        setDataAndType(viewModel.lookupUri(contact), ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    })
                } catch (e: ActivityNotFoundException) {
                    toast("No contacts editor found")
                }
            }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy number") },
                        enabled = primaryNumber.isNotEmpty(),
                        onClick = { menuOpen = false; postCallViewModel.copyNumber(primaryNumber) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete contact", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; confirmDelete = true }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 92.dp, textSize = 40.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                contact.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(20.dp))

            numbers.forEach { number ->
                NumberCard(
                    number = number,
                    onCall = { postCallViewModel.callAgain(number.number) },
                    onMessage = { postCallViewModel.openSMS(number.number) },
                    onWhatsApp = { postCallViewModel.openWhatsApp(number.number) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                // The calls with this person; recorded ones have a play button, as on the phone's own contact page.
                if (calls.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 17.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Call logs", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (calls.size > 8) {
                            TextButton(onClick = { showAllCalls = !showAllCalls }) {
                                Text(if (showAllCalls) "Less" else "All (${calls.size})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsGroup {
                        CallHistoryRows(
                            calls = if (showAllCalls) calls else calls.take(8),
                            extras = callExtras,
                            recordings = RecordingsUi(
                                items = recordingItems,
                                playingKey = playingKey,
                                progress = playProgress,
                                hasPhoneAccess = audioAccess.isGranted,
                                onPlay = { recordingsViewModel.toggle(it) },
                                onRequestAccess = { audioAccess.requestPermission() }
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                SettingsGroup {
                    NavRow(
                        title = "Quick reply on WhatsApp",
                        subtitle = "Send one of your saved messages",
                        onClick = { showQuickReply = true }
                    )
                    SettingsDivider()
                    NavRow(title = "Add note", onClick = { showNote = true })
                    SettingsDivider()
                    NavRow(title = "Remind me", onClick = { showReminder = true })
                    SettingsDivider()
                    NavRow(
                        title = "Block number",
                        titleColor = MaterialTheme.colorScheme.error,
                        showChevron = false,
                        onClick = {
                            viewModel.block(listOf(contact)) { added ->
                                toast(if (added > 0) "${contact.name} added to blocklist" else "Already on your blocklist")
                            }
                        }
                    )
                }

                if (contactNotes.isNotEmpty()) {
                    SectionTitle("Notes")
                    SettingsGroup {
                        contactNotes.forEachIndexed { index, note ->
                            if (index > 0) SettingsDivider()
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp)) {
                                Text(note.content, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(note.createdAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showQuickReply) {
        QuickReplyPickerSheet(
            replies = quickReplies,
            recipientLabel = contact.name,
            onSend = { reply ->
                postCallViewModel.openWhatsApp(primaryNumber, reply.message)
                showQuickReply = false
            },
            onSave = { id, title, message -> quickReplyViewModel.save(id, title, message) },
            onDelete = { quickReplyViewModel.delete(it) },
            onDismiss = { showQuickReply = false }
        )
    }
    if (showNote) {
        AddNoteSheet(
            phoneNumber = primaryNumber,
            onSave = { phone, content ->
                noteViewModel.saveNote(phone, content)
                showNote = false
            },
            onDismiss = { showNote = false }
        )
    }
    if (showReminder) {
        AddReminderSheet(
            phoneNumber = primaryNumber,
            contactName = contact.name,
            onReminderSaved = { showReminder = false },
            onDismiss = { showReminder = false },
            viewModel = reminderViewModel
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${contact.name}?") },
            text = { Text("This contact will be removed from your phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    requireWrite {
                        viewModel.delete(listOf(contact.id)) { removed ->
                            toast(if (removed > 0) "Contact deleted" else "Couldn't delete the contact")
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}


/** Number with its label and three round buttons: call, message, WhatsApp. */
@Composable
private fun NumberCard(number: ContactNumber, onCall: () -> Unit, onMessage: () -> Unit, onWhatsApp: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(number.number, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(number.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RoundAction(Icons.Default.Call, "Call", MaterialTheme.colorScheme.contactAccent, onCall)
            Spacer(modifier = Modifier.width(8.dp))
            RoundAction(Icons.Default.Email, "Message", MaterialTheme.colorScheme.onSurface, onMessage)
            Spacer(modifier = Modifier.width(8.dp))
            RoundAction(Icons.Default.Phone, "WhatsApp", Color.White, onWhatsApp, fill = AccentWhatsApp)
        }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    fill: Color? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill ?: MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}
