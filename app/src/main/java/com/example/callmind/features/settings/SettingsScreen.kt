package com.example.callmind.features.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.core.phone.PhoneModeStore
import com.example.callmind.features.phone.rememberPhoneModeSetup
import com.example.callmind.ui.components.*
import com.example.callmind.ui.theme.CallMindTheme
import com.example.callmind.ui.theme.ThemeMode
import com.example.callmind.ui.theme.ThemeModeStore

@Composable
fun SettingsScreen(
    onOpenQuickReplies: () -> Unit = {},
    onOpenCallBlocking: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    SettingsContent(
        onOpenQuickReplies = onOpenQuickReplies,
        onOpenCallBlocking = onOpenCallBlocking,
        onOpenRecordings = onOpenRecordings,
        onBack = onBack,
        onClearData = { viewModel.clearAllData() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    onClearData: () -> Unit,
    onOpenQuickReplies: () -> Unit = {},
    onOpenCallBlocking: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeMode by ThemeModeStore.mode.collectAsState()
    val phoneMode by PhoneModeStore.enabled.collectAsState()
    val autoRecord by PhoneModeStore.autoRecord.collectAsState()
    val dialTones by PhoneModeStore.dialTones.collectAsState()
    val postCallCard by PhoneModeStore.postCallCard.collectAsState()
    val phoneSetup = rememberPhoneModeSetup()
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                .padding(horizontal = 15.dp, vertical = 8.dp)
        ) {
            SectionTitle("Phone app")
            SettingsGroup {
                SwitchRow(
                    title = "Use CallMind as my phone app",
                    subtitle = if (phoneMode) {
                        "On: dial pad, recents, in-call screen and call recording are in CallMind"
                    } else {
                        "Off: Call buttons open your phone's own dialer"
                    },
                    checked = phoneMode,
                    onCheckedChange = { if (it) phoneSetup.enable() else phoneSetup.disable() }
                )
                if (phoneMode) {
                    SettingsDivider()
                    NavRow(
                        title = "Call recording",
                        subtitle = "Automatic recording and your recordings",
                        value = autoRecord.label,
                        onClick = onOpenRecordings
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "Dial pad tones",
                        checked = dialTones,
                        onCheckedChange = { PhoneModeStore.setDialTones(context, it) }
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = "\"Call ended\" card",
                        subtitle = "Note, reminder and WhatsApp shortcuts after each call",
                        checked = postCallCard,
                        onCheckedChange = { PhoneModeStore.setPostCallCard(context, it) }
                    )
                    if (!phoneSetup.isDefault) {
                        SettingsDivider()
                        NavRow(
                            title = "Set as default phone app",
                            subtitle = "CallMind is not your default phone app yet",
                            onClick = phoneSetup.enable
                        )
                    }
                }
            }
            SectionHint(
                "Phone mode makes CallMind your phone app. Office mode keeps your phone's own dialer and " +
                    "adds CallMind's notes, reminders and quick replies after calls."
            )

            SectionTitle("Calls & messages")
            SettingsGroup {
                NavRow(
                    title = "Call blocking",
                    subtitle = "Blocklist, trustlist and blocking rules",
                    onClick = onOpenCallBlocking
                )
                SettingsDivider()
                NavRow(
                    title = "Quick replies",
                    subtitle = "Saved WhatsApp messages for after a call",
                    onClick = onOpenQuickReplies
                )
            }

            SectionTitle("Appearance")
            SettingsGroup {
                NavRow(
                    title = "Theme",
                    value = themeMode.label,
                    onClick = { showThemeDialog = true }
                )
            }

            SectionTitle("Privacy")
            SettingsGroup {
                NavRow(
                    title = "Clear all CallMind data",
                    subtitle = "Notes, reminders, quick replies and block lists",
                    titleColor = MaterialTheme.colorScheme.error,
                    showChevron = false,
                    onClick = { showDeleteConfirm = true }
                )
            }

            SectionTitle("About")
            SettingsGroup {
                NavRow(
                    title = "Developed by SanStudio",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sanstudio-hub.github.io/in/"))
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                SettingsDivider()
                NavRow(title = "Version", value = versionName, showChevron = false, onClick = {})
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = themeMode == option,
                                    role = Role.RadioButton,
                                    onClick = {
                                        ThemeModeStore.set(context, option)
                                        showThemeDialog = false
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = themeMode == option, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Clear all data") },
            text = {
                Text("This will permanently delete your CallMind notes, reminders, quick replies, block lists and blocking history (your phone's call log and contacts are not touched). This action cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearData()
                    showDeleteConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    CallMindTheme {
        SettingsContent(onClearData = {})
    }
}
