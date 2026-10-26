package com.example.callmind.features.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.data.local.entities.ReminderEntity
import com.example.callmind.data.local.entities.ReminderType
import com.example.callmind.ui.components.CallMindCard
import com.example.callmind.ui.theme.AccentRed
import com.example.callmind.ui.theme.CallMindTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RemindersScreen(viewModel: ReminderViewModel = hiltViewModel()) {
    val reminders by viewModel.allReminders.collectAsState(initial = emptyList())
    var showAddReminder by remember { mutableStateOf(false) }

    RemindersContent(
        reminders = reminders,
        onDeleteReminder = { viewModel.deleteReminder(it) },
        onToggleCompleted = { reminder, completed -> viewModel.setCompleted(reminder, completed) },
        onAddReminder = { showAddReminder = true }
    )

    if (showAddReminder) {
        AddReminderSheet(
            phoneNumber = "",
            onReminderSaved = { showAddReminder = false },
            onDismiss = { showAddReminder = false },
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersContent(
    reminders: List<ReminderEntity>,
    onDeleteReminder: (ReminderEntity) -> Unit,
    onToggleCompleted: (ReminderEntity, Boolean) -> Unit,
    onAddReminder: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Reminders", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddReminder, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = androidx.compose.foundation.shape.CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Add reminder")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (reminders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No reminders scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
                ) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderItem(reminder, onDeleteReminder, onToggleCompleted)
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderItem(
    reminder: ReminderEntity,
    onDeleteReminder: (ReminderEntity) -> Unit,
    onToggleCompleted: (ReminderEntity, Boolean) -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }
    val dateString = remember(reminder.scheduledTime) { sdf.format(Date(reminder.scheduledTime)) }
    val isOverdue = !reminder.isCompleted && reminder.scheduledTime < System.currentTimeMillis()

    CallMindCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.isCompleted,
                onCheckedChange = { onToggleCompleted(reminder, it) }
            )

            Icon(
                imageVector = when (reminder.type) {
                    ReminderType.CALL -> Icons.Default.Call
                    ReminderType.POPUP_NOTE -> Icons.Default.Edit
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null
                )
                if (!reminder.description.isNullOrBlank()) {
                    Text(
                        text = reminder.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = buildString {
                        append(dateString)
                        if (!reminder.phoneNumber.isNullOrBlank()) append(" • ${reminder.phoneNumber}")
                        if (isOverdue) append(" • Overdue")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { onDeleteReminder(reminder) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete reminder",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RemindersScreenPreview() {
    CallMindTheme {
        RemindersContent(
            reminders = listOf(
                ReminderEntity(id = 1, title = "Call Arun Kumar", scheduledTime = System.currentTimeMillis() + 3600000, phoneNumber = "+91 98765 43210"),
                ReminderEntity(id = 2, title = "Project follow-up", scheduledTime = System.currentTimeMillis() + 86400000)
            ),
            onDeleteReminder = {},
            onToggleCompleted = { _, _ -> },
            onAddReminder = {}
        )
    }
}
