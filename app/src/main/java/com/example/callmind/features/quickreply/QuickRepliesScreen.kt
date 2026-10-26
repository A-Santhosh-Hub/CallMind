package com.example.callmind.features.quickreply

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.data.local.entities.QuickReplyEntity

/** Settings page to set up the messages that appear when tapping Quick reply on a call. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRepliesScreen(
    onBack: () -> Unit,
    viewModel: QuickReplyViewModel = hiltViewModel()
) {
    val replies by viewModel.replies.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuickReplyEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Quick replies", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = androidx.compose.foundation.shape.CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Add quick reply")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (replies.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No quick replies yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Save the messages you send after calls (class details, consultation, horoscope...). On any call, tap Quick reply, pick one, and WhatsApp opens with it ready to send.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(replies, key = { it.id }) { reply ->
                        QuickReplyRow(
                            reply = reply,
                            onClick = null,
                            onEdit = { editing = reply; showEditor = true }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        val target = editing
        QuickReplyEditorDialog(
            initial = target,
            onSave = { title, message ->
                viewModel.save(target?.id ?: 0L, title, message)
                showEditor = false
            },
            onDelete = target?.let { reply ->
                {
                    viewModel.delete(reply)
                    showEditor = false
                }
            },
            onDismiss = { showEditor = false }
        )
    }
}
