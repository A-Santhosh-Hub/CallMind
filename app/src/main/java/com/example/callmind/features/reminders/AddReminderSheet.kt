package com.example.callmind.features.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.callmind.data.local.entities.ReminderEntity
import com.example.callmind.data.local.entities.ReminderType

private val DelayOptionsMinutes = listOf(30, 60, 120, 240, 24 * 60)

private fun delayLabel(minutes: Int): String = when {
    minutes >= 24 * 60 -> "Tomorrow"
    minutes >= 60 -> "${minutes / 60} hr"
    else -> "$minutes min"
}

/**
 * Bottom sheet for scheduling a reminder. When [phoneNumber] is blank (opened from the
 * Reminders tab) the number is optional and can be typed in.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddReminderSheet(
    phoneNumber: String,
    contactName: String? = null,
    onReminderSaved: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: ReminderViewModel,
    initialType: ReminderType = ReminderType.CALL
) {
    val canEditNumber = phoneNumber.isBlank()
    var number by remember { mutableStateOf(phoneNumber) }
    val displayName = contactName ?: number.ifBlank { "contact" }

    fun defaultTitle(type: ReminderType) =
        if (type == ReminderType.CALL) "Call $displayName" else "Note for $displayName"

    var type by remember { mutableStateOf(initialType) }
    var title by remember { mutableStateOf(defaultTitle(initialType)) }
    var titleEdited by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var delayMinutes by remember { mutableStateOf(60) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = if (type == ReminderType.CALL) "Schedule Call" else "Schedule Pop-up Note",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == ReminderType.CALL,
                    onClick = {
                        type = ReminderType.CALL
                        if (!titleEdited) title = defaultTitle(type)
                    },
                    label = { Text("Call") },
                    leadingIcon = { Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
                FilterChip(
                    selected = type == ReminderType.POPUP_NOTE,
                    onClick = {
                        type = ReminderType.POPUP_NOTE
                        if (!titleEdited) title = defaultTitle(type)
                    },
                    label = { Text("Pop-up Note") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (canEditNumber) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Phone number (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleEdited = true
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reminder Title") },
                singleLine = true
            )

            if (type == ReminderType.POPUP_NOTE) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note Content") },
                    placeholder = { Text("What do you want to be reminded about?") },
                    minLines = 2
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Remind me in:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DelayOptionsMinutes.forEach { minutes ->
                    FilterChip(
                        selected = delayMinutes == minutes,
                        onClick = { delayMinutes = minutes },
                        label = { Text(delayLabel(minutes)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.insertReminder(
                        ReminderEntity(
                            title = title.trim(),
                            description = if (type == ReminderType.POPUP_NOTE) description.ifBlank { null } else null,
                            scheduledTime = System.currentTimeMillis() + delayMinutes * 60_000L,
                            phoneNumber = number.trim().ifBlank { null },
                            type = type
                        )
                    )
                    onReminderSaved()
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Reminder")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
