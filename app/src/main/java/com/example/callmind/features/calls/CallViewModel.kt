package com.example.callmind.features.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.util.groupCallLogs
import com.example.callmind.data.local.entities.CallLogDisplayItem
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.system.CallLogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callLogProvider: CallLogProvider
) : ViewModel() {

    private val _allCallLogs = MutableStateFlow<List<CallRecordEntity>>(emptyList())
    
    private val _filteredCallLogs = MutableStateFlow<List<CallLogDisplayItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogDisplayItem>> = _filteredCallLogs

    private val _currentFilter = MutableStateFlow<CallFilter>(CallFilter.All)
    val currentFilter: StateFlow<CallFilter> = _currentFilter

    /** SIM slot and location of each call, for the recents list. */
    val extras = callLogProvider.extras

    /** False until the first read has finished, so screens can show nothing instead of "No calls". */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    // Declared after the state it uses: the collector below starts running inside init.
    init {
        // Reloads by itself whenever the phone's call log changes; no need to re-read on every visit.
        viewModelScope.launch {
            callLogProvider.changes.collect { reloadNow() }
        }
    }

    private suspend fun reloadNow() {
        if (!_loaded.value) {
            // First paint: the newest page only. The full log (thousands of rows on some phones) follows.
            _allCallLogs.value = callLogProvider.getCallLogs(limit = FIRST_PAGE)
            applyFilter(_currentFilter.value)
            _loaded.value = true
        }
        val logs = callLogProvider.getCallLogs()
        _allCallLogs.value = logs
        applyFilter(_currentFilter.value)
    }

    fun loadCallLogs() {
        viewModelScope.launch { reloadNow() }
    }

    /** Deletes the calls of one recents row from the phone's call log; [onDone] gets how many were removed. */
    fun deleteCalls(calls: List<CallRecordEntity>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val removed = callLogProvider.delete(calls)
            reloadNow()
            onDone(removed)
        }
    }

    fun setFilter(filter: CallFilter) {
        _currentFilter.value = filter
        applyFilter(filter)
    }

    private fun applyFilter(filter: CallFilter) {
        val baseLogs = when (filter) {
            CallFilter.All -> _allCallLogs.value
            CallFilter.Incoming -> _allCallLogs.value.filter { it.type == CallType.INCOMING }
            CallFilter.Outgoing -> _allCallLogs.value.filter { it.type == CallType.OUTGOING }
            CallFilter.Missed -> _allCallLogs.value.filter { it.type == CallType.MISSED }
        }
        _filteredCallLogs.value = groupCallLogs(baseLogs)
    }
}

private const val FIRST_PAGE = 60

sealed class CallFilter {
    object All : CallFilter()
    object Incoming : CallFilter()
    object Outgoing : CallFilter()
    object Missed : CallFilter()
}
