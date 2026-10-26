package com.example.callmind.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Calls : Screen("calls", "Calls", Icons.Default.Call)
    object Contacts : Screen("contacts", "Contacts", Icons.Default.Person)
    object Reminders : Screen("reminders", "Reminders", Icons.Default.Notifications)
    object Notes : Screen("notes", "Notes", Icons.Default.Edit)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Calls,
    Screen.Contacts,
    Screen.Reminders,
    Screen.Notes,
    Screen.Settings
)
