package com.example.callmind.features.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.data.local.entities.NoteEntity
import com.example.callmind.ui.components.CallMindCard
import com.example.callmind.ui.theme.CallMindTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotesScreen(viewModel: NoteViewModel = hiltViewModel()) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    var showAddNote by remember { mutableStateOf(false) }

    NotesContent(
        notes = notes,
        onDeleteNote = { viewModel.deleteNote(it) },
        onAddNote = { showAddNote = true }
    )

    if (showAddNote) {
        AddNoteSheet(
            phoneNumber = "",
            onSave = { number, content ->
                viewModel.saveNote(number, content)
                showAddNote = false
            },
            onDismiss = { showAddNote = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesContent(
    notes: List<NoteEntity>,
    onDeleteNote: (NoteEntity) -> Unit,
    onAddNote: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = androidx.compose.foundation.shape.CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Add note")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteItem(note, onDeleteNote)
                    }
                }
            }
        }
    }
}

@Composable
fun NoteItem(note: NoteEntity, onDeleteNote: (NoteEntity) -> Unit) {
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val dateString = remember(note.createdAt) { sdf.format(Date(note.createdAt)) }

    CallMindCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "For ${note.phoneNumber} • $dateString",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDeleteNote(note) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete note",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NotesScreenPreview() {
    CallMindTheme {
        NotesContent(
            notes = listOf(
                NoteEntity(id = 1, content = "Follow up about project quotation", phoneNumber = "+91 98765 43210"),
                NoteEntity(id = 2, content = "Arun mentioned a new client opportunity", phoneNumber = "9876543210")
            ),
            onDeleteNote = {},
            onAddNote = {}
        )
    }
}
