package com.example.callmind.features.blocking

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.data.local.entities.NumberRuleEntity
import com.example.callmind.data.local.entities.RuleType
import com.example.callmind.ui.components.*
import com.example.callmind.ui.theme.AccentRed

/** Blocklist or trustlist: same layout, different title, hint and wording. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberListScreen(
    type: RuleType,
    onBack: () -> Unit,
    viewModel: CallBlockingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val rules by (if (type == RuleType.BLOCK) viewModel.blocklist else viewModel.trustlist).collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NumberRuleEntity?>(null) }

    val title = if (type == RuleType.BLOCK) "Blocklist" else "Trustlist"
    val hint = if (type == RuleType.BLOCK) {
        "Calls from these numbers are always blocked."
    } else {
        "Calls from these numbers are never blocked, even when other blocking rules are on."
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (rules.isNotEmpty()) {
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add number")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SectionHint(hint)

            if (rules.isEmpty()) {
                EmptyState(
                    title = "No numbers",
                    icon = {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                            )
                            if (type == RuleType.BLOCK) {
                                Box(
                                    modifier = Modifier.size(28.dp).background(AccentRed, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                ) {
                    PillButton("Add number", onClick = { showAdd = true })
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 24.dp)
                ) {
                    item {
                        SettingsGroup {
                            rules.forEachIndexed { index, rule ->
                                if (index > 0) SettingsDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rule.name ?: rule.phoneNumber, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                        if (rule.name != null) {
                                            Text(rule.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    IconButton(onClick = { pendingDelete = rule }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove ${rule.phoneNumber}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddNumberDialog(
            title = if (type == RuleType.BLOCK) "Add to blocklist" else "Add to trustlist",
            viewModel = viewModel,
            onAdd = { number, name ->
                viewModel.addRule(type, number, name) { added ->
                    if (!added) {
                        Toast.makeText(context, "Enter a valid number that is not already on this list", Toast.LENGTH_SHORT).show()
                    }
                }
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this number?") },
            text = { Text(rule.name?.let { "$it (${rule.phoneNumber})" } ?: rule.phoneNumber) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeRule(rule)
                    pendingDelete = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddNumberDialog(
    title: String,
    viewModel: CallBlockingViewModel,
    onAdd: (number: String, name: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showRecents by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    viewModel.loadRecentNumbers()
                    showRecents = true
                }) { Text("Choose from recent calls") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(number, name.ifBlank { null }) }, enabled = number.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showRecents) {
        val recents by viewModel.recentNumbers.collectAsState()
        AlertDialog(
            onDismissRequest = { showRecents = false },
            title = { Text("Recent calls") },
            text = {
                if (recents.isEmpty()) {
                    Text("No recent calls found.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(recents, key = { it.phoneNumber }) { call ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        number = call.phoneNumber
                                        if (name.isBlank()) name = call.contactName.orEmpty()
                                        showRecents = false
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(call.contactName ?: call.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                                if (call.contactName != null) {
                                    Text(call.phoneNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRecents = false }) { Text("Close") } }
        )
    }
}
