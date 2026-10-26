package com.example.callmind.features.quickreply

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callmind.data.local.entities.QuickReplyEntity
import com.example.callmind.ui.theme.AccentWhatsApp

/** Create ([initial] == null) or edit a quick reply. */
@Composable
fun QuickReplyEditorDialog(
    initial: QuickReplyEntity?,
    onSave: (title: String, message: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var message by remember { mutableStateOf(initial?.message.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New quick reply" else "Edit quick reply") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name (e.g. Classes, Consultation)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message to send") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), message.trim()) },
                enabled = title.isNotBlank() && message.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/**
 * "What do you want to send?" sheet shown from a call. Tapping a reply calls [onSend] (which opens
 * WhatsApp with the text typed in). Replies can also be added and edited right here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReplyPickerSheet(
    replies: List<QuickReplyEntity>,
    recipientLabel: String,
    onSend: (QuickReplyEntity) -> Unit,
    onSave: (id: Long, title: String, message: String) -> Unit,
    onDelete: (QuickReplyEntity) -> Unit,
    onDismiss: () -> Unit
) {
    // null = closed, EditorTarget(null) = new, EditorTarget(reply) = edit
    var editor by remember { mutableStateOf<EditorTarget?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                text = "Quick reply",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose a message to send to $recipientLabel on WhatsApp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (replies.isEmpty()) {
                Text(
                    text = "No quick replies yet. Add messages you send often (class details, consultation fees, ...) and they'll be one tap away on every call.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(replies, key = { it.id }) { reply ->
                        QuickReplyRow(
                            reply = reply,
                            onClick = { onSend(reply) },
                            onEdit = { editor = EditorTarget(reply) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { editor = EditorTarget(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add new message")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editor?.let { target ->
        QuickReplyEditorDialog(
            initial = target.reply,
            onSave = { title, message ->
                onSave(target.reply?.id ?: 0L, title, message)
                editor = null
            },
            onDelete = target.reply?.let { reply ->
                {
                    onDelete(reply)
                    editor = null
                }
            },
            onDismiss = { editor = null }
        )
    }
}

private class EditorTarget(val reply: QuickReplyEntity?)

@Composable
fun QuickReplyRow(
    reply: QuickReplyEntity,
    onClick: (() -> Unit)?,
    onEdit: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.clickable(onClick = onEdit))
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reply.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (onClick != null) AccentWhatsApp else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = reply.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit ${reply.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
