package com.example.callmind.core.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.callmind.core.notifications.NotificationHelper
import com.example.callmind.core.phone.PhoneModeStore

/**
 * Shows the "Call ended" prompt once a real call (ringing or off-hook) returns to idle.
 * The previous state is persisted because the process can be recreated between broadcasts.
 */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        // Phone mode has its own in-call screen; the "Call ended" card is then optional.
        if (PhoneModeStore.isEnabled(context) && !PhoneModeStore.postCallCard.value) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousState = prefs.getString(KEY_STATE, TelephonyManager.EXTRA_STATE_IDLE)
        if (state == previousState) return // the system sends duplicate broadcasts (dual SIM)

        // The number is only delivered with the RINGING broadcast, so remember it.
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val editor = prefs.edit().putString(KEY_STATE, state)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> editor.putString(KEY_NUMBER, incomingNumber)
            TelephonyManager.EXTRA_STATE_OFFHOOK ->
                if (!incomingNumber.isNullOrBlank()) editor.putString(KEY_NUMBER, incomingNumber)
        }
        editor.apply()

        if (state == TelephonyManager.EXTRA_STATE_IDLE &&
            previousState != TelephonyManager.EXTRA_STATE_IDLE
        ) {
            val number = incomingNumber?.takeIf { it.isNotBlank() } ?: prefs.getString(KEY_NUMBER, null)
            prefs.edit().remove(KEY_NUMBER).apply()
            NotificationHelper.showPostCallNotification(context, number)
        }
    }

    private companion object {
        const val PREFS = "callmind_call_state"
        const val KEY_STATE = "last_state"
        const val KEY_NUMBER = "last_number"
    }
}
