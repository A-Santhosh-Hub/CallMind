package com.example.callmind.features.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.local.entities.GroupedCallLog
import com.example.callmind.features.contacts.ContactAvatar
import com.example.callmind.ui.theme.AccentRed
import com.example.callmind.ui.theme.AccentWhatsApp
import com.example.callmind.ui.theme.contactAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Call log details": who it is, one-tap actions, CallMind's tools, and the calls with that number. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailSheet(
    group: GroupedCallLog,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onQuickReply: () -> Unit,
    onNote: () -> Unit,
    onRemind: () -> Unit,
    onContact: () -> Unit,
    onCopy: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
    extras: Map<String, com.example.callmind.data.system.CallExtra>,
    recordings: RecordingsUi
) {
    val log = group.lastCall
    val hasNumber = log.phoneNumber != "Unknown"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(modifier = Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(name = log.contactName, photoUri = null, size = 52.dp, textSize = 22.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        log.contactName ?: if (hasNumber) log.phoneNumber else "Unknown number",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (log.contactName != null) {
                        Text(log.phoneNumber, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (hasNumber) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RoundAction(Icons.Default.Call, "Call", MaterialTheme.colorScheme.contactAccent, onCall)
                    RoundAction(Icons.AutoMirrored.Filled.Message, "Message", MaterialTheme.colorScheme.primary, onMessage)
                    RoundAction(Icons.Default.Call, "WhatsApp", AccentWhatsApp, onWhatsApp)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (hasNumber) SheetRow(Icons.Default.Reply, "Quick reply on WhatsApp", onQuickReply)
            SheetRow(Icons.Default.Edit, "Add note", onNote)
            if (hasNumber) {
                SheetRow(Icons.Default.Notifications, "Remind me", onRemind)
                SheetRow(
                    if (log.contactName != null) Icons.Default.Person else Icons.Default.PersonAdd,
                    if (log.contactName != null) "View contact" else "Add to contacts",
                    onContact
                )
                SheetRow(Icons.Default.ContentCopy, "Copy number", onCopy)
                SheetRow(Icons.Default.Block, "Block number", onBlock, tint = AccentRed)
            }
            SheetRow(Icons.Default.Delete, "Delete from call log", onDelete, tint = AccentRed)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Call logs",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CallHistoryRows(calls = group.calls.take(15), extras = extras, recordings = recordings)
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = label, tint = Color.White) }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.75f))
        Spacer(modifier = Modifier.width(20.dp))
        Text(label, fontSize = 16.sp, color = tint)
    }
}

internal fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
