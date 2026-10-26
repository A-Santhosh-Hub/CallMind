package com.example.callmind.features.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet for writing a note. When [phoneNumber] is blank (e.g. opened from the Notes tab)
 * the user can type the number the note is about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteSheet(
    phoneNumber: String,
    onSave: (phoneNumber: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    var number by remember { mutableStateOf(phoneNumber) }
    var noteContent by remember { mutableStateOf("") }
    val askForNumber = phoneNumber.isBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = if (askForNumber) "Add Note" else "Add Note for $phoneNumber",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (askForNumber) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What do you want to remember?") },
                minLines = 3
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSave(number.trim(), noteContent.trim()) },
                enabled = noteContent.isNotBlank() && number.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Note")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
