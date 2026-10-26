package com.example.callmind.features.postcall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.blocking.BlockDecision
import com.example.callmind.core.communication.CommunicationLauncher
import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.system.CallLogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostCallViewModel @Inject constructor(
    private val communicationLauncher: CommunicationLauncher,
    private val callLogProvider: CallLogProvider
) : ViewModel() {

    private val _lastCall = MutableStateFlow<CallRecordEntity?>(null)

    /** The most recent call-log entry for the number (or the latest call when the number is unknown). */
    val lastCall: StateFlow<CallRecordEntity?> = _lastCall

    private var watching = false
    private var watchedNumber: String? = null

    /**
     * Follows the call log for the number: the entry for the call that just ended is written
     * a moment after the call, so the screen updates itself when it appears.
     */
    fun loadLastCall(phoneNumber: String?) {
        watchedNumber = phoneNumber
        if (watching) return
        watching = true
        viewModelScope.launch {
            callLogProvider.changes.collect {
                val number = watchedNumber
                val logs = callLogProvider.getCallLogs(limit = 30)
                _lastCall.value = if (number.isNullOrBlank()) {
                    logs.firstOrNull()
                } else {
                    logs.firstOrNull { BlockDecision.numbersMatch(it.phoneNumber, number) }
                }
            }
        }
    }

    fun callAgain(phoneNumber: String) = communicationLauncher.launchDialer(phoneNumber)

    fun openWhatsApp(phoneNumber: String, message: String? = null) =
        communicationLauncher.launchWhatsApp(phoneNumber, message)

    fun openTelegram(phoneNumber: String) = communicationLauncher.launchTelegram(phoneNumber)

    fun openSMS(phoneNumber: String) = communicationLauncher.launchSMS(phoneNumber)

    fun openGPay() = communicationLauncher.launchGPay()

    fun openContact(phoneNumber: String) = communicationLauncher.viewContact(phoneNumber)

    fun copyNumber(phoneNumber: String) = communicationLauncher.copyToClipboard(phoneNumber)
}
