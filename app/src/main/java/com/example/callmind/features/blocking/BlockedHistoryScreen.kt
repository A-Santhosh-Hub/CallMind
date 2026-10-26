package com.example.callmind.features.blocking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.data.local.entities.BlockReason
import com.example.callmind.ui.components.*
import com.example.callmind.ui.theme.AccentRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun BlockReason.label() = when (this) {
    BlockReason.BLOCKLIST -> "On your blocklist"
    BlockReason.UNSAVED -> "Not in your contacts"
    BlockReason.UNKNOWN_NUMBER -> "Unknown number"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedHistoryScreen(
    onBack: () -> Unit,
    viewModel: CallBlockingViewModel = hiltViewModel()
) {
    val history by viewModel.blockedCalls.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Blocking history", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear history")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(innerPadding),
                title = "No blocked calls",
                message = "Calls that CallMind blocks will show up here.",
                icon = {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        )
                        Box(
                            modifier = Modifier.size(28.dp).background(AccentRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 24.dp)
            ) {
                item {
                    SettingsGroup {
                        history.forEachIndexed { index, call ->
                            if (index > 0) SettingsDivider()
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    call.phoneNumber.ifBlank { "Unknown number" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${call.reason.label()} • ${format.format(Date(call.timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear blocking history?") },
            text = { Text("The list of blocked calls will be deleted. Your blocklist is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
