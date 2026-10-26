package com.example.callmind.features.contacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.callmind.core.permissions.rememberPermissionState
import com.example.callmind.data.local.entities.ContactEntity
import com.example.callmind.data.system.DeviceContact
import com.example.callmind.ui.components.CallMindCard
import com.example.callmind.ui.components.EmptyState
import com.example.callmind.ui.theme.CallMindTheme
import com.example.callmind.ui.theme.contactAccent
import com.example.callmind.ui.theme.contactAvatar
import com.example.callmind.ui.theme.contactListBackground
import com.example.callmind.ui.theme.searchPill
import com.example.callmind.ui.theme.tertiaryText
import kotlinx.coroutines.launch

private sealed interface ListEntry {
    data object Favorites : ListEntry
    data class Header(val letter: Char) : ListEntry
    data class Person(val contact: DeviceContact) : ListEntry
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onOpenContact: (Long) -> Unit = {},
    onNewContact: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    viewModel: ContactViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by viewModel.contacts.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val readPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val writePermission = rememberPermissionState(Manifest.permission.WRITE_CONTACTS)

    // Deleting needs WRITE_CONTACTS: ask once, then carry on with what the user wanted.
    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(writePermission.isGranted) {
        if (writePermission.isGranted) {
            pendingAfterPermission?.invoke()
            pendingAfterPermission = null
        }
    }

    // The list is loaded when the app starts and kept current by a change observer, so nothing is
    // reloaded when this tab opens. Only the very first grant of the permission needs a read.
    LaunchedEffect(readPermission.isGranted) {
        if (readPermission.isGranted && !viewModel.loaded.value) viewModel.refresh()
    }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val entries = remember(contacts) {
        buildList<ListEntry> {
            add(ListEntry.Favorites)
            var last: Char? = null
            contacts.forEach { contact ->
                val letter = contact.sectionLetter
                if (letter != last) {
                    add(ListEntry.Header(letter))
                    last = letter
                }
                add(ListEntry.Person(contact))
            }
        }
    }
    val letterPositions = remember(entries) {
        entries.withIndex()
            .filter { it.value is ListEntry.Header }
            .associate { (it.value as ListEntry.Header).letter to it.index }
    }
    // Names are matched in memory; numbers use the phone's own number index (contact id -> matching number).
    val searchDigits = query.filter(Char::isDigit)
    val numberHits by produceState(emptyMap<Long, String>(), searchDigits) {
        value = if (searchDigits.length >= 3) viewModel.searchByNumber(searchDigits) else emptyMap()
    }
    val results = remember(contacts, query, numberHits) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else {
            contacts.filter { contact -> contact.name.contains(q, ignoreCase = true) || contact.id in numberHits }.take(300)
        }
    }

    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.contactListBackground)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            when {
                selecting -> SelectionHeader(
                    count = selected.size,
                    allSelected = selected.size == contacts.size && contacts.isNotEmpty(),
                    onCancel = ::exitSelection,
                    onToggleAll = {
                        selected = if (selected.size == contacts.size) emptySet() else contacts.map { it.id }.toSet()
                    }
                )
                searching -> Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 25.dp, end = 25.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SearchPill(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "${contacts.size} contacts",
                        editable = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Cancel",
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .clickable {
                                searching = false
                                query = ""
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.contactAccent
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 25.dp, end = 12.dp, top = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contacts",
                            modifier = Modifier.weight(1f),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        IconButton(onClick = { selecting = true }, enabled = contacts.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Select contacts", modifier = Modifier.size(16.dp))
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = { menuOpen = false; viewModel.refresh() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open phone Contacts app") },
                                    onClick = {
                                        menuOpen = false
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
                                        } catch (e: ActivityNotFoundException) {
                                            toast("No contacts app found")
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchPill(
                        query = "",
                        onQueryChange = {},
                        placeholder = if (contacts.isEmpty()) "Search contacts" else "${contacts.size} contacts",
                        editable = false,
                        onClick = { if (readPermission.isGranted) searching = true },
                        modifier = Modifier.padding(horizontal = 25.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            when {
                !readPermission.isGranted -> PermissionRequiredView(
                    message = "Contacts permission is required to show your contact list.",
                    onRequestPermission = { readPermission.requestPermission() }
                )
                searching -> {
                    if (query.isBlank()) {
                        // Reference behaviour: the list stays visible but dimmed until the user types.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .clickable { searching = false }
                        )
                    } else if (results.isEmpty()) {
                        EmptyState(title = "No results", icon = { SearchEmptyIcon() })
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(results, key = { it.id }) { contact ->
                                val matchedNumber = numberHits[contact.id]
                                ContactRow(
                                    contact = contact,
                                    subtitle = matchedNumber,
                                    selecting = false,
                                    selected = false,
                                    onClick = { onOpenContact(contact.id) },
                                    onLongClick = {}
                                )
                            }
                        }
                    }
                }
                !loaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.contactAccent)
                }
                contacts.isEmpty() -> EmptyState(
                    title = "No contacts",
                    message = "Tap + to add your first contact.",
                    icon = { SearchEmptyIcon() }
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (selecting) 90.dp else 100.dp)
                ) {
                    items(
                        entries,
                        key = {
                            when (it) {
                                ListEntry.Favorites -> "favorites"
                                is ListEntry.Header -> "h${it.letter}"
                                is ListEntry.Person -> "c${it.contact.id}"
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            ListEntry.Favorites -> if (!selecting) {
                                FavoritesRow(count = contacts.count { it.starred }, onClick = onOpenFavorites)
                            }
                            is ListEntry.Header -> LetterHeader(entry.letter)
                            is ListEntry.Person -> ContactRow(
                                contact = entry.contact,
                                subtitle = null,
                                selecting = selecting,
                                selected = entry.contact.id in selected,
                                onClick = {
                                    if (selecting) {
                                        selected = if (entry.contact.id in selected) selected - entry.contact.id else selected + entry.contact.id
                                    } else onOpenContact(entry.contact.id)
                                },
                                onLongClick = {
                                    if (!selecting) {
                                        selecting = true
                                        selected = setOf(entry.contact.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (readPermission.isGranted && !searching && !selecting && loaded) {
            AlphabetIndex(
                letters = letterPositions.keys.toList(),
                onLetterSelected = { letter ->
                    letterPositions[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)
            )
            // Green "+" like the reference.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .shadow(8.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.contactAccent, spotColor = MaterialTheme.colorScheme.contactAccent)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.contactAccent)
                    .clickable(onClick = onNewContact),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "New contact", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        if (selecting) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SelectionAction(Icons.Default.Delete, "Delete", enabled = selected.isNotEmpty(), tint = MaterialTheme.colorScheme.error) {
                        confirmDelete = true
                    }
                    SelectionAction(Icons.Default.Warning, "Block", enabled = selected.isNotEmpty(), tint = MaterialTheme.colorScheme.onSurface) {
                        viewModel.block(contacts.filter { it.id in selected }) { added ->
                            toast(if (added > 0) "$added number(s) added to blocklist" else "Already on your blocklist")
                        }
                        exitSelection()
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} contact(s)?") },
            text = { Text("They will be removed from your phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val ids = selected
                    val doDelete = {
                        viewModel.delete(ids) { removed -> toast("$removed contact(s) deleted") }
                        exitSelection()
                    }
                    if (writePermission.isGranted) doDelete() else {
                        pendingAfterPermission = doDelete
                        writePermission.requestPermission()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SearchEmptyIcon() {
    Icon(
        Icons.Default.Search,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    )
}

/** The grey rounded search field. In its idle form it is just a button; when [editable] it takes input. */
@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    editable: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    if (editable) LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.searchPill)
            .then(if (!editable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), maxLines = 1)
            }
            if (editable) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.contactAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
        }
    }
}

@Composable
private fun SelectionHeader(count: Int, allSelected: Boolean, onCancel: () -> Unit, onToggleAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Cancel", modifier = Modifier.clickable(onClick = onCancel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.contactAccent)
        Text(
            text = if (count == 0) "Select items" else "$count selected",
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = if (allSelected) "Select none" else "Select all",
            modifier = Modifier.clickable(onClick = onToggleAll),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.contactAccent
        )
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.3f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) tint else tint.copy(alpha = 0.3f))
    }
}

/** Green square badge with the letter, as section header. */
@Composable
private fun LetterHeader(letter: Char) {
    Box(modifier = Modifier.padding(start = 25.dp, top = 14.dp, bottom = 8.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.contactAccent, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(letter.toString(), color = Color.White.copy(alpha = 0.95f), fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FavoritesRow(count: Int, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(start = 25.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.contactAvatar),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Favorites", modifier = Modifier.weight(1f), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            if (count > 0) {
                Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.tertiaryText)
        }
        HorizontalDivider(modifier = Modifier.padding(start = 73.dp, end = 28.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactRow(
    contact: DeviceContact,
    subtitle: String?,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (subtitle != null) 64.dp else 52.dp)
                .padding(start = 25.dp, end = 28.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.contactAccent else Color.Transparent)
                        .border(1.5.dp, if (selected) MaterialTheme.colorScheme.contactAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            ContactAvatar(name = contact.name, photoUri = contact.photoUri)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = if (selecting) 109.dp else 73.dp, end = 28.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Row used by Search results (people found by name). */
@Composable
fun ContactItem(
    contact: ContactEntity,
    onClick: (() -> Unit)? = null,
    onCallClick: (() -> Unit)? = null
) {
    CallMindCard(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(name = contact.name, photoUri = null, size = 40.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (onCallClick != null) {
                IconButton(onClick = onCallClick) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.contactAccent)
                }
            }
        }
    }
}

@Composable
fun PermissionRequiredView(message: String, onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactRowPreview() {
    CallMindTheme {
        ContactRow(
            contact = DeviceContact(1, "k", "Arun Kumar", null, false),
            subtitle = null, selecting = false, selected = false, onClick = {}, onLongClick = {}
        )
    }
}
