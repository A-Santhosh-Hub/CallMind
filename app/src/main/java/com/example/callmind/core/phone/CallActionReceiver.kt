package com.example.callmind.core.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.callmind.features.phone.InCallActivity

/** Handles the Answer / Decline / Hang up buttons of the call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> {
                CallManager.answerFirstRinging()
                context.startActivity(InCallActivity.intent(context))
            }
            ACTION_DECLINE -> CallManager.declineFirstRinging()
            ACTION_HANG_UP -> CallManager.hangUpAll()
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.callmind.action.ANSWER"
        const val ACTION_DECLINE = "com.example.callmind.action.DECLINE"
        const val ACTION_HANG_UP = "com.example.callmind.action.HANG_UP"
    }
}
