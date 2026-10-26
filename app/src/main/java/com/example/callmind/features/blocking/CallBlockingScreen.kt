package com.example.callmind.features.blocking

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.callmind.core.blocking.BlockNotifyMode
import com.example.callmind.core.blocking.CallBlockingRole
import com.example.callmind.core.permissions.rememberPermissionState
import com.example.callmind.ui.components.*

private fun BlockNotifyMode.label() = when (this) {
    BlockNotifyMode.ALWAYS -> "Notify after blocking"
    BlockNotifyMode.EXCEPT_BLOCKLIST -> "Notify (except blocklist numbers)"
    BlockNotifyMode.NEVER -> "Do not notify after blocking"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallBlockingScreen(
    onBack: () -> Unit,
    onOpenBlocklist: () -> Unit,
    onOpenTrustlist: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: CallBlockingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val blocklist by viewModel.blocklist.collectAsState()
    val trustlist by viewModel.trustlist.collectAsState()
    val history by viewModel.blockedCalls.collectAsState()
    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    var roleHeld by remember { mutableStateOf(CallBlockingRole.isHeld(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { roleHeld = CallBlockingRole.isHeld(context) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = CallBlockingRole.isHeld(context)
    }

    var showNotifyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Call blocking", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status: blocking only works once Android has made CallMind the call-screening app.
            SettingsGroup {
                when {
                    !CallBlockingRole.isSupported -> NavRow(
                        title = "Not available on this phone",
                        subtitle = "Call blocking needs Android 10 or newer.",
                        onClick = {},
                        showChevron = false
                    )
                    roleHeld -> NavRow(
                        title = "Call blocking is on",
                        subtitle = "CallMind screens incoming calls with the rules below.",
                        onClick = {},
                        showChevron = false
                    )
                    else -> Column(modifier = Modifier.padding(16.dp)) {
                        Text("Call blocking is off", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Allow CallMind to screen incoming calls. Android will ask you to confirm, and you can switch it off in system settings any time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { CallBlockingRole.requestIntent(context)?.let(roleLauncher::launch) }) {
                            Text("Turn on")
                        }
                    }
                }
            }

            SettingsGroup {
                SwitchRow(
                    title = "Block calls from unsaved numbers",
                    checked = settings.blockUnsaved,
                    onCheckedChange = { on ->
                        if (on && !contactsPermission.isGranted) contactsPermission.requestPermission()
                        viewModel.setBlockUnsaved(on)
                    },
                    subtitle = if (settings.blockUnsaved && !contactsPermission.isGranted) {
                        "Needs permission to read your contacts. Tap again after allowing it."
                    } else null
                )
            }

            SettingsGroup {
                SwitchRow(
                    title = "Block calls from unknown/empty numbers",
                    checked = settings.blockUnknown,
                    onCheckedChange = viewModel::setBlockUnknown
                )
            }

            SettingsGroup {
                SwitchRow(
                    title = "Do not block repeated calls",
                    subtitle = "If a blocked unsaved number calls again within three minutes, the call is let through.",
                    checked = settings.allowRepeat,
                    onCheckedChange = viewModel::setAllowRepeat
                )
                SettingsDivider()
                NavRow(
                    title = "Blocking notification",
                    subtitle = settings.notifyMode.label(),
                    onClick = { showNotifyDialog = true }
                )
            }

            SectionTitle("List management")
            SettingsGroup {
                NavRow(
                    title = "Blocklist",
                    subtitle = "Always blocked",
                    value = blocklist.size.takeIf { it > 0 }?.toString(),
                    onClick = onOpenBlocklist
                )
                SettingsDivider()
                NavRow(
                    title = "Trustlist",
                    subtitle = "Never blocked, even by the rules above",
                    value = trustlist.size.takeIf { it > 0 }?.toString(),
                    onClick = onOpenTrustlist
                )
                SettingsDivider()
                NavRow(
                    title = "Blocking history",
                    value = history.size.takeIf { it > 0 }?.toString(),
                    onClick = onOpenHistory
                )
            }

            SectionHint("Blocking applies to phone calls only. Spam labels, call recording and AI call features are provided by the phone maker and are not part of CallMind.")
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showNotifyDialog) {
        AlertDialog(
            onDismissRequest = { showNotifyDialog = false },
            title = { Text("Blocking notification") },
            text = {
                Column {
                    BlockNotifyMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.notifyMode == mode,
                                    role = Role.RadioButton,
                                    onClick = {
                                        viewModel.setNotifyMode(mode)
                                        showNotifyDialog = false
                                    }
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.notifyMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(mode.label())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNotifyDialog = false }) { Text("Close") } }
        )
    }
}
