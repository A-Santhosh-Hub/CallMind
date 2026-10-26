package com.example.callmind.core.phone

import android.app.KeyguardManager
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.example.callmind.features.phone.InCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android binds this service for every call once CallMind is the phone app. It hands the calls to
 * [CallManager], keeps the call notification up to date and opens the in-call screen.
 */
class CallMindInCallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
        scope.launch {
            CallManager.calls.collect { calls -> CallNotifications.update(applicationContext, calls) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        CallNotifications.cancel(applicationContext)
        CallManager.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(applicationContext, call)
        if (call.state == Call.STATE_RINGING && shouldWaitForNotification()) return // full-screen / heads-up from the notification
        try {
            startActivity(InCallActivity.intent(this))
        } catch (e: Exception) {
            Log.w("CallMindInCallService", "Could not open the call screen", e)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
    }

    @Deprecated("Replaced by onCallEndpointChanged on Android 14+, still delivered on every version")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.onAudioChanged(audioState)
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        CallManager.onCanAddCallChanged(canAddCall)
    }

    /**
     * An incoming call is announced by a heads-up notification while the phone is in use, and by a
     * full-screen notification when it is locked or off. Without notification permission the
     * call screen has to be opened directly.
     */
    private fun shouldWaitForNotification(): Boolean {
        if (!CallNotifications.canNotify(this)) return false
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val off = getSystemService(PowerManager::class.java)?.isInteractive == false
        return !(locked || off)
    }
}
