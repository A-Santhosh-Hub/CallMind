package com.example.callmind.features.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.util.formatTalkTime
import com.example.callmind.core.util.groupCallLogs
import com.example.callmind.core.util.startOfToday
import com.example.callmind.data.local.entities.CallLogDisplayItem
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.ReminderEntity
import com.example.callmind.data.repository.NoteRepository
import com.example.callmind.data.repository.ReminderRepository
import com.example.callmind.data.system.CallLogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private class CallData(val recent: List<CallRecordEntity>, val today: List<CallRecordEntity>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val callLogProvider: CallLogProvider,
    noteRepository: NoteRepository,
    reminderRepository: ReminderRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val snapshots = HomeSnapshotStore(context)
    private val manualRefresh = MutableStateFlow(0)

    private fun ReminderEntity.isUpcoming(now: Long) = !isCompleted && scheduledTime > now

    /** Drives the badge on the Reminders tab. */
    val activeReminderCount: Flow<Int> = reminderRepository.getAllReminders()
        .map { reminders ->
            val now = System.currentTimeMillis()
            reminders.count { it.isUpcoming(now) }
        }

    // Two small queries instead of reading the whole log: the newest calls, and today's calls.
    // They re-run by themselves when the phone's call log changes.
    private val callData = combine(callLogProvider.changes, manualRefresh) { _, _ -> }
        .mapLatest {
            CallData(
                recent = callLogProvider.getCallLogs(limit = RECENT_CALL_LIMIT),
                today = callLogProvider.getCallLogs(since = startOfToday())
            )
        }

    /**
     * Starts as soon as the app does (not when Home appears) and begins with the last snapshot,
     * so the first frame already shows real values.
     */
    val homeState: StateFlow<HomeState> = combine(
        callData,
        noteRepository.getAllNotes(),
        reminderRepository.getAllReminders()
    ) { calls, notes, reminders ->
        val now = System.currentTimeMillis()
        val dayStart = startOfToday()
        val upcoming = reminders.filter { it.isUpcoming(now) }.sortedBy { it.scheduledTime }

        HomeState(
            recentCalls = groupCallLogs(calls.recent),
            upcomingReminders = upcoming.take(2),
            callCount = calls.today.size,
            noteCount = notes.count { it.createdAt >= dayStart },
            scheduledCount = upcoming.size,
            totalTalkTime = formatTalkTime(calls.today.sumOf { it.durationSeconds })
        )
    }
        .onEach { snapshots.save(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, snapshots.load() ?: HomeState())

    /** Cheap: re-reads when Home comes back to the front (e.g. after granting the call-log permission). */
    fun loadHomeData() {
        manualRefresh.value++
    }

    private companion object {
        const val RECENT_CALL_LIMIT = 20
    }
}

data class HomeState(
    val recentCalls: List<CallLogDisplayItem> = emptyList(),
    val upcomingReminders: List<ReminderEntity> = emptyList(),
    val callCount: Int = 0,
    val noteCount: Int = 0,
    val scheduledCount: Int = 0,
    val totalTalkTime: String = "0s"
)
