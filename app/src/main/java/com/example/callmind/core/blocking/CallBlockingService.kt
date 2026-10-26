package com.example.callmind.core.blocking

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.callmind.core.notifications.NotificationHelper
import com.example.callmind.data.local.entities.BlockReason
import com.example.callmind.data.repository.CallBlockingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Called by Android for each incoming call once the user has made CallMind the call-screening app.
 * It must answer quickly, so the lookup runs off the main thread and always ends with a response -
 * if anything goes wrong the call is allowed through rather than lost.
 */
class CallBlockingService : CallScreeningService() {

    // Entry point instead of @Inject: Hilt 2.51 cannot read the metadata of field-injected Kotlin 2.1 classes.
    private val repository: CallBlockingRepository by lazy {
        EntryPointAccessors.fromApplication(applicationContext, CallBlockingEntryPoint::class.java).repository()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        if (!incoming) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        scope.launch {
            val decision = try {
                repository.screen(number)
            } catch (e: Exception) {
                Decision.Allow
            }

            val response = CallResponse.Builder().apply {
                if (decision.block) {
                    setDisallowCall(true)
                    setRejectCall(true)
                    setSkipNotification(true)
                }
            }.build()
            respondToCall(callDetails, response)

            if (decision.block && decision.reason != null) notifyIfWanted(number, decision.reason)
        }
    }

    private fun notifyIfWanted(number: String, reason: BlockReason) {
        val mode = repository.settingsStore.settings.value.notifyMode
        val notify = when (mode) {
            BlockNotifyMode.ALWAYS -> true
            BlockNotifyMode.EXCEPT_BLOCKLIST -> reason != BlockReason.BLOCKLIST
            BlockNotifyMode.NEVER -> false
        }
        if (notify) NotificationHelper.showBlockedCallNotification(applicationContext, number, reason)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CallBlockingEntryPoint {
    fun repository(): CallBlockingRepository
}
