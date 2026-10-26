package com.example.callmind.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.callmind.core.util.formatRelativeTime
import com.example.callmind.core.util.formatScheduledLabel
import com.example.callmind.core.util.formatTimeUntil
import com.example.callmind.data.local.entities.CallLogDisplayItem
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.local.entities.GroupedCallLog
import com.example.callmind.data.local.entities.ReminderEntity
import com.example.callmind.features.postcall.PostCallViewModel
import com.example.callmind.ui.components.*
import com.example.callmind.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    postCallViewModel: PostCallViewModel = hiltViewModel(),
    onSearchClick: () -> Unit = {},
    onCallsClick: () -> Unit = {},
    onNotesClick: () -> Unit = {},
    onRemindersClick: () -> Unit = {}
) {
    val state by viewModel.homeState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadHomeData()
    }

    HomeScreenContent(
        state = state,
        onSearchClick = onSearchClick,
        onCallsClick = onCallsClick,
        onNotesClick = onNotesClick,
        onRemindersClick = onRemindersClick,
        onCallClick = { postCallViewModel.callAgain("") },
        onWhatsAppClick = { postCallViewModel.openWhatsApp("") },
        onTelegramClick = { postCallViewModel.openTelegram("") },
        postCallViewModel = postCallViewModel
    )
}

@Composable
fun HomeScreenContent(
    state: HomeState,
    onSearchClick: () -> Unit,
    onCallsClick: () -> Unit,
    onNotesClick: () -> Unit,
    onRemindersClick: () -> Unit,
    onCallClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onTelegramClick: () -> Unit,
    postCallViewModel: PostCallViewModel? = null
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                TodayCommunicationCard(state)
            }

            item {
                QuickActionsCard(
                    onSearch = onSearchClick,
                    onNotes = onNotesClick,
                    onCall = onCallClick,
                    onWhatsApp = onWhatsAppClick,
                    onTelegram = onTelegramClick
                )
            }

            item {
                SectionHeader(title = "Recent calls", onActionClick = onCallsClick)
            }
            
            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val recentGroups = state.recentCalls.filterIsInstance<CallLogDisplayItem.CallGroup>().take(3)
                    if (recentGroups.isEmpty()) {
                        Text(
                            text = "No recent calls",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            recentGroups.forEach { item ->
                                RecentActivityItem(item.group, postCallViewModel)
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Upcoming", onActionClick = onRemindersClick)
            }
            
            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.upcomingReminders.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "No upcoming items",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            state.upcomingReminders.forEach { reminder ->
                                UpcomingCard(reminder)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TodayCommunicationCard(state: HomeState) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { // Reduced padding
            Text(
                text = "Today's communication",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(20.dp)) // Reduced height
            
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItemVertical(Icons.Default.Call, state.callCount.toString(), "Calls", AccentGreen, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)).align(Alignment.CenterVertically))
                StatItemVertical(Icons.Default.DateRange, state.totalTalkTime, "Talk time", MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1.2f))
                Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)).align(Alignment.CenterVertically))
                StatItemVertical(Icons.Default.Edit, state.noteCount.toString(), "Notes", AccentNotes, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatItemVertical(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QuickActionsCard(
    onSearch: () -> Unit,
    onNotes: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onTelegram: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), // Reduced vertical padding
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Quick actions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ActionCircleButtonSmall(Icons.Default.Call, "Dialer", AccentGreen, onClick = onCall, modifier = Modifier.weight(1f))
                ActionCircleButtonSmall(Icons.Default.Phone, "WhatsApp", AccentWhatsApp, onClick = onWhatsApp, modifier = Modifier.weight(1f))
                ActionCircleButtonSmall(Icons.AutoMirrored.Filled.Send, "Telegram", AccentTelegram, onClick = onTelegram, modifier = Modifier.weight(1f))
                ActionCircleButtonSmall(Icons.Default.Search, "Search", MaterialTheme.colorScheme.primary, onClick = onSearch, modifier = Modifier.weight(1f))
                ActionCircleButtonSmall(Icons.Default.Edit, "Notes", AccentNotes, onClick = onNotes, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ActionCircleButtonSmall(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(48.dp) // Reduced from 56
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector? = null, onActionClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(text = title, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = "View all",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onActionClick() }
        )
    }
}

@Composable
fun UpcomingCard(reminder: ReminderEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when(reminder.type) {
                    com.example.callmind.data.local.entities.ReminderType.CALL -> Icons.Default.Call
                    com.example.callmind.data.local.entities.ReminderType.POPUP_NOTE -> Icons.Default.Edit
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = reminder.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(text = formatScheduledLabel(reminder.scheduledTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            Text(
                text = formatTimeUntil(reminder.scheduledTime),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RecentActivityItem(
    group: GroupedCallLog,
    postCallViewModel: PostCallViewModel? = null
) {
    val call = group.lastCall
    val avatarColor = remember(call.phoneNumber) {
        val colors = listOf(Color(0xFF4361EE), Color(0xFFF72585), Color(0xFF4CC9F0), Color(0xFF7209B7), Color(0xFFFB8500))
        colors[Math.abs(call.phoneNumber.hashCode()) % colors.size]
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp), // Reduced vertical from 16
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp) // Reduced from 48
                .background(avatarColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (call.contactName != null) {
                Text(
                    text = call.contactName.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp // Fixed size
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    append(call.contactName ?: call.phoneNumber)
                    if (group.calls.size > 1) {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(" (${group.calls.size})")
                        }
                    }
                },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), // Slightly smaller
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(call.type) {
                        CallType.INCOMING -> Icons.Default.KeyboardArrowDown
                        CallType.OUTGOING -> Icons.Default.KeyboardArrowUp
                        CallType.MISSED -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    modifier = Modifier.size(10.dp), // Reduced from 12
                    tint = if (call.type == CallType.MISSED) AccentRed else AccentGreen
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${if(call.type == CallType.INCOMING) "Incoming" else if(call.type == CallType.OUTGOING) "Outgoing" else "Missed"} • ${formatRelativeTime(call.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        
        IconButton(
            onClick = { postCallViewModel?.callAgain(call.phoneNumber) },
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape) // Reduced from 44
        ) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp), tint = AccentGreen)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    val mockLog1 = CallRecordEntity(phoneNumber = "+919514815656", durationSeconds = 120, timestamp = System.currentTimeMillis(), type = CallType.INCOMING)
    val mockLog2 = CallRecordEntity(phoneNumber = "+919962315656", durationSeconds = 60, timestamp = System.currentTimeMillis(), type = CallType.INCOMING)
    
    CallMindTheme {
        HomeScreenContent(
            state = HomeState(
                recentCalls = listOf(
                    CallLogDisplayItem.Header("Today"),
                    CallLogDisplayItem.CallGroup(GroupedCallLog(mockLog1, listOf(mockLog1, mockLog1))),
                    CallLogDisplayItem.CallGroup(GroupedCallLog(mockLog2, listOf(mockLog2)))
                ),
                upcomingReminders = listOf(ReminderEntity(title = "Call Arun Kumar", scheduledTime = 0)),
                callCount = 6660,
                noteCount = 0,
                scheduledCount = 2,
                totalTalkTime = "42m"
            ),
            onSearchClick = {},
            onCallsClick = {},
            onNotesClick = {},
            onRemindersClick = {},
            onCallClick = {},
            onWhatsAppClick = {},
            onTelegramClick = {}
        )
    }
}
