package com.example.callmind.features.phone

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.callmind.core.permissions.rememberPermissionState
import com.example.callmind.core.phone.CallPlacer
import com.example.callmind.core.phone.DialerRole
import com.example.callmind.core.phone.PhoneModeStore
import com.example.callmind.core.phone.SimAccount
import com.example.callmind.core.phone.T9
import com.example.callmind.data.local.entities.CallLogDisplayItem
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.local.entities.GroupedCallLog
import com.example.callmind.data.local.entities.ReminderType
import com.example.callmind.data.local.entities.RuleType
import com.example.callmind.data.repository.RecordingsRepository
import com.example.callmind.data.system.CallExtra
import com.example.callmind.data.system.CallLogProvider
import com.example.callmind.features.blocking.CallBlockingViewModel
import com.example.callmind.features.calls.CallFilter
import com.example.callmind.features.calls.CallViewModel
import com.example.callmind.features.contacts.ContactAvatar
import com.example.callmind.features.contacts.PermissionRequiredView
import com.example.callmind.features.notes.AddNoteSheet
import com.example.callmind.features.notes.NoteViewModel
import com.example.callmind.features.postcall.PostCallViewModel
import com.example.callmind.features.quickreply.QuickReplyPickerSheet
import com.example.callmind.features.quickreply.QuickReplyViewModel
import com.example.callmind.features.reminders.AddReminderSheet
import com.example.callmind.features.reminders.ReminderViewModel
import com.example.callmind.ui.components.EmptyState
import com.example.callmind.ui.theme.AccentRed
import com.example.callmind.ui.theme.contactAccent
import com.example.callmind.ui.theme.contactListBackground
import com.example.callmind.ui.theme.searchPill
import com.example.callmind.ui.theme.tertiaryText
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A number to put on the dial pad, e.g. from a "tel:" link opened with CallMind as the phone app. */
object DialRequests {
    val number = MutableStateFlow<String?>(null)
}

/** Lets the tab bar get out of the way while recents are being selected or searched. */
object DialUiState {
    val hideTabs = MutableStateFlow(false)
}

private fun groupKey(group: GroupedCallLog) = "${group.lastCall.phoneNumber}_${group.lastCall.timestamp}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    onOpenSettings: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenCallBlocking: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onAddContact: (String) -> Unit,
    viewModel: DialerViewModel = hiltViewModel(),
    callViewModel: CallViewModel = hiltViewModel(),
    postCallViewModel: PostCallViewModel = hiltViewModel(),
    reminderViewModel: ReminderViewModel = hiltViewModel(),
    noteViewModel: NoteViewModel = hiltViewModel(),
    quickReplyViewModel: QuickReplyViewModel = hiltViewModel(),
    blockingViewModel: CallBlockingViewModel = hiltViewModel(),
    recordingsViewModel: RecordingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val digits by viewModel.digits.collectAsState()
    val matches by viewModel.matches.collectAsState()
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val items by callViewModel.callLogs.collectAsState()
    val filter by callViewModel.currentFilter.collectAsState()
    val logsLoaded by callViewModel.loaded.collectAsState()
    val extras by callViewModel.extras.collectAsState()
    val quickReplies by quickReplyViewModel.replies.collectAsState()
    val recordingItems by recordingsViewModel.recordings.collectAsState()
    val playingKey by recordingsViewModel.playing.collectAsState()
    val playProgress by recordingsViewModel.progress.collectAsState()
    val audioAccess = rememberPermissionState(RecordingsRepository.accessPermission())
    LaunchedEffect(audioAccess.isGranted) { recordingsViewModel.refresh() }
    val dialTones by PhoneModeStore.dialTones.collectAsState()
    val callLogPermission = rememberPermissionState(Manifest.permission.READ_CALL_LOG)
    val setup = rememberPhoneModeSetup()
    val placeCall = rememberPlaceCall()
    val playTone = rememberDialTones(dialTones)

    var padOpen by rememberSaveable { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var sims by remember { mutableStateOf(CallPlacer.simAccounts(context)) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable { mutableStateOf(setOf<String>()) }
    var filterVisible by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<GroupedCallLog?>(null) }
    var noteFor by remember { mutableStateOf<CallRecordEntity?>(null) }
    var reminderFor by remember { mutableStateOf<CallRecordEntity?>(null) }
    var quickReplyFor by remember { mutableStateOf<CallRecordEntity?>(null) }
    var deleteFor by remember { mutableStateOf<List<GroupedCallLog>?>(null) }
    var pendingDelete by remember { mutableStateOf<List<GroupedCallLog>?>(null) }
    val recentsState = rememberLazyListState()
    val matchesState = rememberLazyListState()

    // Deleting needs the call-log write permission; ask once, then carry on with the delete.
    val writeLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val groups = pendingDelete
        pendingDelete = null
        if (granted && groups != null) {
            callViewModel.deleteCalls(groups.flatMap { it.calls })
            selecting = false
            selectedKeys = emptySet()
        } else Toast.makeText(context, "Allow call log access to delete calls", Toast.LENGTH_SHORT).show()
    }

    val groups = remember(items) { items.filterIsInstance<CallLogDisplayItem.CallGroup>().map { it.group } }
    // Rows that have a recording get a small tape icon.
    val recordedKeys = remember(groups, recordingItems) {
        if (recordingItems.isEmpty()) emptySet()
        else groups.filter { g -> g.calls.any { RecordingsRepository.forCall(it, recordingItems) != null } }.map { groupKey(it) }.toSet()
    }
    val recordingsUi = RecordingsUi(
        items = recordingItems,
        playingKey = playingKey,
        progress = playProgress,
        hasPhoneAccess = audioAccess.isGranted,
        onPlay = { recordingsViewModel.toggle(it) },
        onRequestAccess = { audioAccess.requestPermission() }
    )

    LaunchedEffect(selecting, searching) { DialUiState.hideTabs.value = selecting || searching }
    DisposableEffect(Unit) { onDispose { DialUiState.hideTabs.value = false } }

    // Like the phone's own dialer: scrolling a list slides the pad away (the pad button brings it back),
    // and the All / Missed tabs hide while scrolling down and return when scrolling up.
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (kotlin.math.abs(available.y) > 6f) padOpen = false
                    if (available.y < -6f) filterVisible = false else if (available.y > 6f) filterVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        sims = CallPlacer.simAccounts(context)
        // Opening the recents clears the "missed call" notification, as in any phone app.
        if (DialerRole.isHeld(context)) {
            try {
                context.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification()
            } catch (e: SecurityException) {
                // not allowed on this device; harmless
            }
        }
    }
    // The list follows the call log by itself (see CallViewModel); it only needs a nudge once the permission is given.
    val permissionAtStart = remember { callLogPermission.isGranted }
    LaunchedEffect(callLogPermission.isGranted) {
        if (callLogPermission.isGranted && !permissionAtStart) callViewModel.loadCallLogs()
    }

    // A "tel:" link opened with CallMind as the phone app lands on the pad with the number typed.
    val request by DialRequests.number.collectAsState()
    LaunchedEffect(request) {
        request?.let {
            viewModel.set(it)
            padOpen = true
            DialRequests.number.value = null
        }
    }

    fun dial(number: String, account: SimAccount?) = placeCall(number, account?.handle)
    fun dialTyped(account: SimAccount?) {
        if (digits.isNotEmpty()) {
            dial(digits, account)
        } else {
            // Like the phone's own app: Call with nothing typed brings back the last dialled number.
            groups.firstOrNull { it.lastCall.type == CallType.OUTGOING }?.lastCall?.phoneNumber
                ?.takeIf { it != "Unknown" }?.let { viewModel.set(it) }
        }
    }
    fun closeSearch() {
        searching = false
        viewModel.setQuery("")
    }
    fun closeSelect() {
        selecting = false
        selectedKeys = emptySet()
    }
    fun scrolledPast(): Boolean = recentsState.firstVisibleItemIndex > 0 || recentsState.firstVisibleItemScrollOffset > 24

    BackHandler(enabled = searching || selecting) {
        if (searching) closeSearch() else closeSelect()
    }

    val background = MaterialTheme.colorScheme.contactListBackground
    val padShown = padOpen && !selecting && !searching
    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            when {
                selecting -> SelectHeader(
                    selectedCount = selectedKeys.size,
                    total = groups.size,
                    onToggleAll = {
                        selectedKeys = if (selectedKeys.size == groups.size) emptySet() else groups.map { groupKey(it) }.toSet()
                    },
                    onClose = ::closeSelect
                )
                searching -> SearchHeader(query = query, onQueryChange = viewModel::setQuery, onCancel = ::closeSearch)
                else -> {
                    val small by animateFloatAsState(
                        targetValue = if (digits.isEmpty() && scrolledPast()) 22f else 32f,
                        label = "dialTitle"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Dial",
                            modifier = Modifier.weight(1f),
                            fontSize = small.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // While a number is being typed the header keeps only the menu, as in the phone's own app.
                        if (digits.isEmpty()) {
                            if (groups.isNotEmpty()) {
                                IconButton(onClick = { selecting = true; padOpen = false }) {
                                    Icon(Icons.Outlined.CheckBox, contentDescription = "Select")
                                }
                            }
                            IconButton(onClick = { searching = true; padOpen = false }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DropdownMenuItem(text = { Text("Call recordings") }, onClick = { menuOpen = false; onOpenRecordings() })
                                DropdownMenuItem(text = { Text("Notes") }, onClick = { menuOpen = false; onOpenNotes() })
                                DropdownMenuItem(text = { Text("Reminders") }, onClick = { menuOpen = false; onOpenReminders() })
                                DropdownMenuItem(text = { Text("Call blocking") }, onClick = { menuOpen = false; onOpenCallBlocking() })
                                DropdownMenuItem(text = { Text("Quick replies") }, onClick = { menuOpen = false; onOpenQuickReplies() })
                                DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                            }
                        }
                    }
                }
            }

            if (!setup.isDefault && !selecting && !searching) DefaultPhoneBanner(onSetDefault = setup.enable)

            AnimatedVisibility(
                visible = digits.isEmpty() && !selecting && !searching && filterVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SegmentedTabs(
                    allSelected = filter is CallFilter.All,
                    onAll = { callViewModel.setFilter(CallFilter.All) },
                    onMissed = { callViewModel.setFilter(CallFilter.Missed) }
                )
            }

            Box(modifier = Modifier.weight(1f).nestedScroll(scrollWatcher)) {
                when {
                    searching && query.isNotBlank() -> MatchList(
                        state = matchesState,
                        digits = "",
                        matches = searchResults,
                        query = query,
                        showActions = false,
                        onCall = { m -> onOpenContact(m.contactId) },
                        onInfo = { m -> onOpenContact(m.contactId) },
                        onAddContact = {},
                        onMessage = {}
                    )
                    digits.isNotEmpty() && !searching -> MatchList(
                        state = matchesState,
                        digits = digits,
                        matches = matches,
                        query = "",
                        showActions = true,
                        onCall = { m -> m.number?.let { dial(it, null) } ?: onOpenContact(m.contactId) },
                        onInfo = { m -> onOpenContact(m.contactId) },
                        onAddContact = { onAddContact(digits) },
                        onMessage = { postCallViewModel.openSMS(digits) }
                    )
                    !callLogPermission.isGranted -> PermissionRequiredView(
                        message = "Call Log permission is required to show your recent calls.",
                        onRequestPermission = { callLogPermission.requestPermission() }
                    )
                    groups.isEmpty() && !logsLoaded -> Unit // still reading the call log
                    groups.isEmpty() -> EmptyState(
                        title = if (filter is CallFilter.Missed) "No missed calls" else "No recent calls",
                        icon = {
                            Icon(
                                Icons.Default.Call,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                            )
                        }
                    )
                    else -> LazyColumn(
                        state = recentsState,
                        contentPadding = PaddingValues(bottom = if (padShown) 8.dp else 96.dp)
                    ) {
                        items(groups, key = { groupKey(it) }) { group ->
                            val key = groupKey(group)
                            val log = group.lastCall
                            RecentRow(
                                group = group,
                                extra = extras[CallLogProvider.extraKey(log.phoneNumber.takeIf { it != "Unknown" }, log.timestamp)],
                                selecting = selecting,
                                selected = key in selectedKeys,
                                hasRecording = key in recordedKeys,
                                onClick = {
                                    if (selecting) {
                                        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                                    } else if (log.phoneNumber != "Unknown") dial(log.phoneNumber, null)
                                },
                                onInfo = { detail = group },
                                onCopy = { postCallViewModel.copyNumber(log.phoneNumber) },
                                onMessage = { postCallViewModel.openSMS(log.phoneNumber) },
                                onEditBeforeCalling = { viewModel.set(log.phoneNumber); padOpen = true },
                                onBlock = {
                                    blockingViewModel.addRule(RuleType.BLOCK, log.phoneNumber, log.contactName) { added ->
                                        Toast.makeText(
                                            context,
                                            if (added) "${log.contactName ?: log.phoneNumber} added to blocklist" else "Already on your blocklist",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDelete = { deleteFor = listOf(group) }
                            )
                        }
                    }
                }

                // Dimmed recents while the search box is empty: a tap anywhere closes the search.
                if (searching && query.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.12f))
                            .clickable(onClick = ::closeSearch)
                    )
                }

                // Pad button: a small white tile at the bottom-left, shown whenever the pad is hidden.
                if (!padOpen && digits.isEmpty() && !selecting && !searching) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, bottom = 18.dp)
                            .shadow(6.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { padOpen = true }
                            .size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Dialpad,
                            contentDescription = "Open dial pad",
                            tint = MaterialTheme.colorScheme.contactAccent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // With the pad hidden, what has been typed stays visible; a tap brings the pad back.
            if (!padOpen && digits.isNotEmpty() && !selecting && !searching) {
                Column(modifier = Modifier.fillMaxWidth().background(background).clickable { padOpen = true }) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = digits,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 30.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (selecting) {
                Column(modifier = Modifier.fillMaxWidth().background(background)) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .clickable(enabled = selectedKeys.isNotEmpty()) {
                                deleteFor = groups.filter { groupKey(it) in selectedKeys }
                            }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (selectedKeys.isNotEmpty()) 1f else 0.3f)
                        Icon(Icons.Default.Delete, contentDescription = null, tint = tint)
                        Text("Delete", fontSize = 13.sp, color = tint)
                    }
                }
            }

            AnimatedVisibility(
                visible = padShown,
                enter = slideInVertically { it / 2 } + expandVertically() + fadeIn(),
                exit = slideOutVertically { it / 2 } + shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth().background(background)) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    if (digits.isNotEmpty()) {
                        Text(
                            text = digits,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                            textAlign = TextAlign.Center,
                            fontSize = when {
                                digits.length <= 11 -> 34.sp
                                digits.length <= 15 -> 28.sp
                                else -> 22.sp
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DialPad(
                        onKey = { key ->
                            playTone(key)
                            viewModel.append(key)
                        },
                        onLongKey = {
                            if (!CallPlacer.placeVoicemail(context)) {
                                Toast.makeText(context, "Allow the Phone permission to call voicemail", Toast.LENGTH_SHORT).show()
                            }
                        },
                        showVoicemailIcon = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        keyHeight = 60.dp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { padOpen = false }, modifier = Modifier.size(52.dp)) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Hide dial pad",
                                tint = MaterialTheme.colorScheme.tertiaryText,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CallButtons(sims = sims, onCall = { dialTyped(it) })
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = digits.isNotEmpty(),
                                    onClick = { viewModel.backspace() },
                                    onLongClick = { viewModel.clear() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (digits.isNotEmpty()) {
                                // The amber delete key of the phone's own pad.
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFFE08A))
                                        .size(width = 46.dp, height = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFF3A3000), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { group ->
        val log = group.lastCall
        CallDetailSheet(
            group = group,
            extras = extras,
            recordings = recordingsUi,
            onDismiss = { detail = null; recordingsViewModel.stopPlayback() },
            onCall = { dial(log.phoneNumber, null); detail = null },
            onMessage = { postCallViewModel.openSMS(log.phoneNumber) },
            onWhatsApp = { postCallViewModel.openWhatsApp(log.phoneNumber) },
            onQuickReply = { quickReplyFor = log; detail = null },
            onNote = { noteFor = log; detail = null },
            onRemind = { reminderFor = log; detail = null },
            onContact = {
                detail = null
                if (log.contactName != null) postCallViewModel.openContact(log.phoneNumber) else onAddContact(log.phoneNumber)
            },
            onCopy = { postCallViewModel.copyNumber(log.phoneNumber) },
            onBlock = {
                blockingViewModel.addRule(RuleType.BLOCK, log.phoneNumber, log.contactName) { added ->
                    Toast.makeText(
                        context,
                        if (added) "${log.contactName ?: log.phoneNumber} added to blocklist" else "Already on your blocklist",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                detail = null
            },
            onDelete = { deleteFor = listOf(group); detail = null }
        )
    }

    deleteFor?.let { targets ->
        val callCount = targets.sumOf { it.calls.size }
        val subject = if (targets.size == 1) {
            targets[0].lastCall.let { it.contactName ?: it.phoneNumber.takeIf { n -> n != "Unknown" } ?: "Unknown number" }
        } else "${targets.size} contacts"
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Delete from call log?") },
            text = {
                Text("$callCount call${if (callCount == 1) "" else "s"} with $subject will be removed from your phone's call log.")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteFor = null
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                        callViewModel.deleteCalls(targets.flatMap { it.calls })
                        closeSelect()
                    } else {
                        pendingDelete = targets
                        writeLogLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
                    }
                }) { Text("Delete", color = AccentRed) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } }
        )
    }

    quickReplyFor?.let { log ->
        QuickReplyPickerSheet(
            replies = quickReplies,
            recipientLabel = log.contactName ?: log.phoneNumber,
            onSend = { reply ->
                postCallViewModel.openWhatsApp(log.phoneNumber, reply.message)
                quickReplyFor = null
            },
            onSave = { id, title, message -> quickReplyViewModel.save(id, title, message) },
            onDelete = { quickReplyViewModel.delete(it) },
            onDismiss = { quickReplyFor = null }
        )
    }
    noteFor?.let { log ->
        AddNoteSheet(
            phoneNumber = log.phoneNumber,
            onSave = { phone, content ->
                noteViewModel.saveNote(phone, content)
                noteFor = null
            },
            onDismiss = { noteFor = null }
        )
    }
    reminderFor?.let { log ->
        AddReminderSheet(
            phoneNumber = log.phoneNumber,
            contactName = log.contactName,
            onReminderSaved = { reminderFor = null },
            onDismiss = { reminderFor = null },
            viewModel = reminderViewModel,
            initialType = ReminderType.CALL
        )
    }
}

@Composable
private fun DefaultPhoneBanner(onSetDefault: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CallMind is not your default phone app, so calls may open another screen.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onSetDefault) {
                Text("Set default", color = MaterialTheme.colorScheme.contactAccent)
            }
        }
    }
}

/** The phone's All / Missed switch: a soft pill with the chosen side in green. */
@Composable
private fun SegmentedTabs(allSelected: Boolean, onAll: () -> Unit, onMissed: () -> Unit) {
    val green = MaterialTheme.colorScheme.contactAccent
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.searchPill)
    ) {
        listOf("All" to allSelected, "Missed" to !allSelected).forEach { (label, selected) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) green.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable(onClick = if (label == "All") onAll else onMissed)
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) green else MaterialTheme.colorScheme.tertiaryText
                )
            }
        }
    }
}

@Composable
private fun SelectHeader(selectedCount: Int, total: Int, onToggleAll: () -> Unit, onClose: () -> Unit) {
    val green = MaterialTheme.colorScheme.contactAccent
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Select-all box: empty, a dash when some are chosen, a tick when all are.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selectedCount > 0) green else Color.Transparent)
                .then(
                    if (selectedCount == 0) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)) else Modifier
                )
                .clickable(onClick = onToggleAll),
            contentAlignment = Alignment.Center
        ) {
            if (selectedCount > 0) {
                Icon(
                    if (selectedCount == total) Icons.Default.Check else Icons.Default.Remove,
                    contentDescription = "Select all",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            if (selectedCount == 0) "Select items" else "$selectedCount selected",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cancel selection") }
    }
}

@Composable
private fun SearchHeader(query: String, onQueryChange: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.searchPill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.tertiaryText)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search contacts", fontSize = 17.sp, color = MaterialTheme.colorScheme.tertiaryText)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.contactAccent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        }
        TextButton(onClick = onCancel) {
            Text("Cancel", fontSize = 17.sp, color = MaterialTheme.colorScheme.contactAccent)
        }
    }
}

/**
 * One recents row, laid out like the phone's own: a call-direction icon at the left, the name (red when
 * missed), a small SIM badge with the number or the place it is registered in, the date, and an info button.
 * Long-press for quick actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    group: GroupedCallLog,
    extra: CallExtra?,
    selecting: Boolean,
    selected: Boolean,
    hasRecording: Boolean,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    onCopy: () -> Unit,
    onMessage: () -> Unit,
    onEditBeforeCalling: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit
) {
    val log = group.lastCall
    val missed = log.type == CallType.MISSED
    val hasNumber = log.phoneNumber != "Unknown"
    val title = (log.contactName ?: if (hasNumber) log.phoneNumber else "Unknown number") +
        if (group.calls.size > 1) " (${group.calls.size})" else ""
    val nameColor = if (missed) AccentRed else MaterialTheme.colorScheme.onBackground
    val green = MaterialTheme.colorScheme.contactAccent
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { if (!selecting && hasNumber) menu = true })
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selected) green else Color.Transparent)
                        .then(
                            if (!selected) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            // Direction column: only outgoing calls get an icon (missed ones are red instead), as in the phone's own list.
            Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                if (log.type == CallType.OUTGOING) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallMade,
                        contentDescription = "Outgoing",
                        tint = MaterialTheme.colorScheme.tertiaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, color = nameColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (extra?.simSlot != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .size(width = 18.dp, height = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(extra.simSlot.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = when {
                            log.contactName != null && hasNumber -> log.phoneNumber
                            !extra?.location.isNullOrBlank() -> extra?.location.orEmpty()
                            else -> typeLabel(log)
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasRecording) {
                Icon(
                    Icons.Default.Voicemail,
                    contentDescription = "Recorded",
                    tint = green,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp)
                )
            }
            Text(formatWhen(log.timestamp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!selecting) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .clickable(onClick = onInfo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Call details",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 44.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            DropdownMenuItem(text = { Text("Copy number") }, onClick = { menu = false; onCopy() })
            DropdownMenuItem(text = { Text("Send message") }, onClick = { menu = false; onMessage() })
            DropdownMenuItem(text = { Text("Edit before calling") }, onClick = { menu = false; onEditBeforeCalling() })
            DropdownMenuItem(text = { Text("Add to Blocklist") }, onClick = { menu = false; onBlock() })
            DropdownMenuItem(text = { Text("Delete", color = AccentRed) }, onClick = { menu = false; onDelete() })
        }
    }
}

private fun typeLabel(log: CallRecordEntity) = when (log.type) {
    CallType.INCOMING -> "Incoming"
    CallType.OUTGOING -> "Outgoing"
    CallType.MISSED -> "Missed"
}

private fun formatWhen(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "Yesterday"
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}

/** The green call button: one pill, or SIM 1 / SIM 2 side by side on a dual-SIM phone. */
@Composable
private fun CallButtons(sims: List<SimAccount>, onCall: (SimAccount?) -> Unit) {
    val green = MaterialTheme.colorScheme.contactAccent
    if (sims.size >= 2) {
        Row(modifier = Modifier.clip(RoundedCornerShape(50)).background(green).height(52.dp)) {
            sims.take(2).forEachIndexed { index, sim ->
                if (index > 0) {
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(Color.White.copy(alpha = 0.5f)))
                }
                Row(
                    modifier = Modifier.clickable { onCall(sim) }.padding(horizontal = 18.dp).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(sim.label, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(green)
                .clickable { onCall(sims.firstOrNull()) }
                .size(width = 132.dp, height = 52.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
        }
    }
}

/** [text] with the parts in [ranges] drawn in blue, the way the phone marks what you typed. */
@Composable
private fun highlighted(text: String, ranges: List<IntRange>?): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        if (ranges.isNullOrEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        val marked = BooleanArray(text.length)
        for (r in ranges) for (i in r) if (i in marked.indices) marked[i] = true
        var i = 0
        while (i < text.length) {
            val on = marked[i]
            var j = i
            while (j < text.length && marked[j] == on) j++
            if (on) withStyle(SpanStyle(color = color)) { append(text.substring(i, j)) } else append(text.substring(i, j))
            i = j
        }
    }
}

/** Where [typed] digits sit inside [number] (ignoring spaces and dashes), for highlighting. */
private fun numberRanges(number: String, typed: String): List<IntRange>? {
    val wanted = typed.filter { it.isDigit() || it == '+' }
    if (wanted.isEmpty()) return null
    val positions = number.indices.filter { number[it].isDigit() || number[it] == '+' }
    val flat = positions.map { number[it] }.joinToString("")
    val at = flat.indexOf(wanted)
    if (at < 0) return null
    return (at until at + wanted.length).map { positions[it].let { p -> p..p } }
}

@Composable
private fun MatchList(
    state: androidx.compose.foundation.lazy.LazyListState,
    digits: String,
    matches: List<DialMatch>,
    query: String,
    showActions: Boolean,
    onCall: (DialMatch) -> Unit,
    onInfo: (DialMatch) -> Unit,
    onAddContact: () -> Unit,
    onMessage: () -> Unit
) {
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 8.dp)) {
        items(matches, key = { it.contactId }) { m ->
            val nameRanges = if (digits.isNotEmpty()) T9.matchRanges(m.name, digits) else null
            val numberMarks = if (digits.isNotEmpty() && nameRanges == null) numberRanges(m.number.orEmpty(), digits) else null
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCall(m) }.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(name = m.name, photoUri = m.photoUri, size = 40.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val nameText = if (query.isNotEmpty()) {
                        val at = m.name.indexOf(query.trim(), ignoreCase = true)
                        highlighted(m.name, if (at >= 0) listOf(at until at + query.trim().length) else null)
                    } else highlighted(m.name, nameRanges)
                    Text(nameText, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        highlighted(m.number.orEmpty(), numberMarks),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .clickable { onInfo(m) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Contact",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
        if (showActions) {
            item {
                if (digits.length >= 3) {
                    ActionLine(Icons.Default.Person, "Add to contacts", onAddContact)
                    ActionLine(Icons.AutoMirrored.Filled.Message, "Send message", onMessage)
                }
            }
        }
    }
}

@Composable
private fun ActionLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.contactAccent)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}
