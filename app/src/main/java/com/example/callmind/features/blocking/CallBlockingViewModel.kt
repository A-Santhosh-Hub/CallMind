package com.example.callmind.features.blocking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.blocking.BlockNotifyMode
import com.example.callmind.core.blocking.BlockingSettings
import com.example.callmind.data.local.entities.BlockedCallEntity
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.NumberRuleEntity
import com.example.callmind.data.local.entities.RuleType
import com.example.callmind.data.repository.CallBlockingRepository
import com.example.callmind.data.system.CallLogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallBlockingViewModel @Inject constructor(
    private val repository: CallBlockingRepository,
    private val callLogProvider: CallLogProvider
) : ViewModel() {

    val settings: StateFlow<BlockingSettings> = repository.settingsStore.settings

    val blocklist: StateFlow<List<NumberRuleEntity>> = repository.observeRules(RuleType.BLOCK)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trustlist: StateFlow<List<NumberRuleEntity>> = repository.observeRules(RuleType.TRUST)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockedCalls: StateFlow<List<BlockedCallEntity>> = repository.observeBlockedCalls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recentNumbers = MutableStateFlow<List<CallRecordEntity>>(emptyList())
    val recentNumbers: StateFlow<List<CallRecordEntity>> = _recentNumbers

    fun setBlockUnsaved(value: Boolean) = repository.settingsStore.update { it.copy(blockUnsaved = value) }
    fun setBlockUnknown(value: Boolean) = repository.settingsStore.update { it.copy(blockUnknown = value) }
    fun setAllowRepeat(value: Boolean) = repository.settingsStore.update { it.copy(allowRepeat = value) }
    fun setNotifyMode(mode: BlockNotifyMode) = repository.settingsStore.update { it.copy(notifyMode = mode) }

    /** [onResult] gets false when the number is invalid or already on the list. */
    fun addRule(type: RuleType, number: String, name: String?, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.addRule(type, number, name)) }
    }

    fun removeRule(rule: NumberRuleEntity) {
        viewModelScope.launch { repository.removeRule(rule) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    /** Latest distinct numbers from the call log, for the "From recent calls" picker. */
    fun loadRecentNumbers() {
        viewModelScope.launch {
            _recentNumbers.value = callLogProvider.getCallLogs()
                .filter { it.phoneNumber != "Unknown" }
                .distinctBy { it.phoneNumber }
                .take(RECENT_LIMIT)
        }
    }

    private companion object {
        const val RECENT_LIMIT = 30
    }
}
