package com.example.callmind

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.callmind.core.phone.PhoneModeStore
import com.example.callmind.data.local.entities.RuleType
import com.example.callmind.features.blocking.BlockedHistoryScreen
import com.example.callmind.features.blocking.CallBlockingScreen
import com.example.callmind.features.blocking.NumberListScreen
import com.example.callmind.features.calls.CallsScreen
import com.example.callmind.features.contacts.ContactDetailScreen
import com.example.callmind.features.contacts.ContactEditorScreen
import com.example.callmind.features.contacts.ContactsScreen
import com.example.callmind.features.contacts.FavoritesScreen
import com.example.callmind.features.home.HomeScreen
import com.example.callmind.features.home.HomeViewModel
import com.example.callmind.features.notes.NotesScreen
import com.example.callmind.features.onboarding.OnboardingScreen
import com.example.callmind.features.phone.DialRequests
import com.example.callmind.features.phone.DialUiState
import com.example.callmind.features.phone.DialerScreen
import com.example.callmind.features.phone.RecordingsScreen
import com.example.callmind.features.quickreply.QuickRepliesScreen
import com.example.callmind.features.reminders.RemindersScreen
import com.example.callmind.features.search.SearchScreen
import com.example.callmind.features.settings.SettingsScreen
import com.example.callmind.navigation.Screen
import com.example.callmind.navigation.bottomNavItems
import com.example.callmind.ui.theme.AccentRed
import com.example.callmind.ui.theme.CallMindTheme
import com.example.callmind.ui.theme.contactAccent
import com.example.callmind.ui.theme.contactListBackground
import com.example.callmind.ui.theme.tertiaryText
import dagger.hilt.android.AndroidEntryPoint

private const val SEARCH_ROUTE = "search"
private const val QUICK_REPLIES_ROUTE = "quick_replies"
private const val CALL_BLOCKING_ROUTE = "call_blocking"
private const val BLOCKLIST_ROUTE = "blocklist"
private const val TRUSTLIST_ROUTE = "trustlist"
private const val BLOCKED_HISTORY_ROUTE = "blocked_history"
private const val CONTACT_DETAIL_ROUTE = "contact/{id}"
private const val CONTACT_NEW_ROUTE = "contact_new?number={number}"
private const val FAVORITES_ROUTE = "favorites"
private const val DIAL_ROUTE = "dial"
private const val RECORDINGS_ROUTE = "call_recordings"

/** The three tabs of phone mode, like the phone's own app: Dial, Contacts, Favorites. */
private data class PhoneTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val phoneTabs = listOf(
    PhoneTab(DIAL_ROUTE, "Dial", Icons.Default.Call),
    PhoneTab("contacts", "Contacts", Icons.Default.Person),
    PhoneTab(FAVORITES_ROUTE, "Favorites", Icons.Default.Star)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    /** "tel:" links opened with CallMind as the phone app put the number on the dial pad. */
    private fun handleDialIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_DIAL) return
        val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let { android.net.Uri.decode(it) }
        DialRequests.number.value = number?.takeIf { it.isNotEmpty() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDialIntent(intent)
        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("callmind_prefs", Context.MODE_PRIVATE) }
            var showOnboarding by remember {
                mutableStateOf(sharedPrefs.getBoolean("show_onboarding", true))
            }

            CallMindTheme {
                if (showOnboarding) {
                    OnboardingScreen(onFinished = {
                        sharedPrefs.edit().putBoolean("show_onboarding", false).apply()
                        showOnboarding = false
                    })
                } else {
                    CallMindAppContent()
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String, homeRoute: String) {
    navigate(route) {
        popUpTo(homeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun CallMindAppContent() {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val activeReminderCount by homeViewModel.activeReminderCount.collectAsState(initial = 0)
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    // Two editions: office mode (the original app) and phone mode (CallMind is the phone app).
    val phoneMode by PhoneModeStore.enabled.collectAsState()
    val homeRoute = if (phoneMode) DIAL_ROUTE else Screen.Home.route
    val startRoute = remember { if (PhoneModeStore.enabled.value) DIAL_ROUTE else Screen.Home.route }

    // Flipping the mode in Settings lands on the new mode's home screen.
    var shownMode by remember { mutableStateOf(phoneMode) }
    LaunchedEffect(phoneMode) {
        if (phoneMode != shownMode) {
            shownMode = phoneMode
            navController.navigate(homeRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    // A "tel:" link opened with CallMind as the phone app goes straight to the dial pad.
    val dialRequest by DialRequests.number.collectAsState()
    LaunchedEffect(dialRequest, phoneMode) {
        if (dialRequest != null && phoneMode && navController.currentDestination?.route != DIAL_ROUTE) {
            navController.navigateToTab(DIAL_ROUTE, homeRoute)
        }
    }

    val route = navBackStackEntry?.destination?.route
    // Phone mode shows its tab bar only on the three tabs; office mode hides it on a contact's page, as in the reference.
    val dialHidesTabs by DialUiState.hideTabs.collectAsState()
    val hideBottomBar = if (phoneMode) {
        (route != null && route != DIAL_ROUTE && route != Screen.Contacts.route && route != FAVORITES_ROUTE) ||
            (route == DIAL_ROUTE && dialHidesTabs)
    } else {
        route?.let { it.startsWith("contact/") || it.startsWith("contact_new") } == true
    }

    // The list-style tabs sit on plain white / black, so the status-bar strip above them must match.
    val onListTab = route == DIAL_ROUTE || route == Screen.Contacts.route || route == FAVORITES_ROUTE
    Scaffold(
        containerColor = if (onListTab) MaterialTheme.colorScheme.contactListBackground else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideBottomBar) Column {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                // NavigationBar already pads for the system navigation bar.
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    val currentDestination = navBackStackEntry?.destination
                    if (phoneMode) {
                        val green = MaterialTheme.colorScheme.contactAccent
                        phoneTabs.forEach { tab ->
                            NavigationBarItem(
                                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                                label = { Text(text = tab.label, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                selected = currentDestination?.route == tab.route,
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = green,
                                    selectedTextColor = green,
                                    unselectedIconColor = MaterialTheme.colorScheme.tertiaryText,
                                    unselectedTextColor = MaterialTheme.colorScheme.tertiaryText,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                onClick = {
                                    if (currentDestination?.route != tab.route) navController.navigateToTab(tab.route, homeRoute)
                                }
                            )
                        }
                    } else {
                        // Each section has its own accent; Contacts (and its sub-screens) use green like the reference.
                        val inContacts = currentDestination?.route?.let {
                            it == Screen.Contacts.route || it.startsWith("contact") || it == FAVORITES_ROUTE
                        } == true
                        val sectionAccent = if (inContacts) MaterialTheme.colorScheme.contactAccent else MaterialTheme.colorScheme.primary

                        bottomNavItems.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true ||
                                (inContacts && screen.route == Screen.Contacts.route)

                            NavigationBarItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (screen.route == Screen.Reminders.route && activeReminderCount > 0) {
                                                Badge(containerColor = AccentRed) {
                                                    Text(activeReminderCount.toString(), color = Color.White)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = screen.icon, contentDescription = screen.label)
                                    }
                                },
                                label = {
                                    Text(
                                        text = screen.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                selected = selected,
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = sectionAccent,
                                    selectedTextColor = sectionAccent,
                                    unselectedIconColor = MaterialTheme.colorScheme.tertiaryText,
                                    unselectedTextColor = MaterialTheme.colorScheme.tertiaryText,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                onClick = {
                                    if (currentDestination?.route != screen.route) {
                                        navController.navigateToTab(screen.route, homeRoute)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            // Inner screens have their own Scaffold; tell them the bottom bar already consumed the insets.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(DIAL_ROUTE) {
                DialerScreen(
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenRecordings = { navController.navigate(RECORDINGS_ROUTE) },
                    onOpenCallBlocking = { navController.navigate(CALL_BLOCKING_ROUTE) },
                    onOpenQuickReplies = { navController.navigate(QUICK_REPLIES_ROUTE) },
                    onOpenNotes = { navController.navigate(Screen.Notes.route) },
                    onOpenReminders = { navController.navigate(Screen.Reminders.route) },
                    onOpenContact = { navController.navigate("contact/$it") },
                    onAddContact = { navController.navigate("contact_new?number=${android.net.Uri.encode(it)}") }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onSearchClick = { navController.navigate(SEARCH_ROUTE) },
                    onCallsClick = { navController.navigateToTab(Screen.Calls.route, homeRoute) },
                    onNotesClick = { navController.navigateToTab(Screen.Notes.route, homeRoute) },
                    onRemindersClick = { navController.navigateToTab(Screen.Reminders.route, homeRoute) }
                )
            }
            composable(Screen.Calls.route) { CallsScreen(onSearchClick = { navController.navigate(SEARCH_ROUTE) }) }
            composable(Screen.Contacts.route) {
                ContactsScreen(
                    onOpenContact = { navController.navigate("contact/$it") },
                    onNewContact = { navController.navigate("contact_new") },
                    onOpenFavorites = { navController.navigate(FAVORITES_ROUTE) }
                )
            }
            composable(CONTACT_DETAIL_ROUTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ContactDetailScreen(
                    contactId = entry.arguments?.getLong("id") ?: -1L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                CONTACT_NEW_ROUTE,
                arguments = listOf(navArgument("number") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                ContactEditorScreen(
                    onClose = { navController.popBackStack() },
                    prefillNumber = entry.arguments?.getString("number")
                )
            }
            composable(FAVORITES_ROUTE) {
                FavoritesScreen(
                    // In phone mode Favorites is a tab; in office mode it opens from Contacts and has a back arrow.
                    onBack = if (phoneMode) null else ({ navController.popBackStack() }),
                    onOpenContact = { navController.navigate("contact/$it") }
                )
            }
            composable(Screen.Reminders.route) { RemindersScreen() }
            composable(Screen.Notes.route) { NotesScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenQuickReplies = { navController.navigate(QUICK_REPLIES_ROUTE) },
                    onOpenCallBlocking = { navController.navigate(CALL_BLOCKING_ROUTE) },
                    onOpenRecordings = { navController.navigate(RECORDINGS_ROUTE) },
                    // In phone mode Settings is opened from the Dial menu rather than being a tab.
                    onBack = if (phoneMode) ({ navController.popBackStack() }) else null
                )
            }
            composable(RECORDINGS_ROUTE) { RecordingsScreen(onBack = { navController.popBackStack() }) }
            composable(QUICK_REPLIES_ROUTE) { QuickRepliesScreen(onBack = { navController.popBackStack() }) }
            composable(CALL_BLOCKING_ROUTE) {
                CallBlockingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBlocklist = { navController.navigate(BLOCKLIST_ROUTE) },
                    onOpenTrustlist = { navController.navigate(TRUSTLIST_ROUTE) },
                    onOpenHistory = { navController.navigate(BLOCKED_HISTORY_ROUTE) }
                )
            }
            composable(BLOCKLIST_ROUTE) { NumberListScreen(type = RuleType.BLOCK, onBack = { navController.popBackStack() }) }
            composable(TRUSTLIST_ROUTE) { NumberListScreen(type = RuleType.TRUST, onBack = { navController.popBackStack() }) }
            composable(BLOCKED_HISTORY_ROUTE) { BlockedHistoryScreen(onBack = { navController.popBackStack() }) }
            composable(SEARCH_ROUTE) { SearchScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
