package com.example.callmind.core.phone

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager

/**
 * "Default phone app". Android lets exactly one app be the phone app; the user grants it through a
 * system dialog and can give it back in system settings.
 *
 * The parts that make CallMind *eligible* (the dial-pad entry point and the in-call service) are
 * switched on only in phone mode and off in office mode, so turning phone mode off also lets Android
 * hand the role back to the phone's own dialer.
 */
object DialerRole {
    private const val DIALER_ALIAS = "com.example.callmind.DialerAlias"
    private const val IN_CALL_SERVICE = "com.example.callmind.core.phone.CallMindInCallService"

    fun isHeld(context: Context): Boolean = try {
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
    } catch (e: Exception) {
        false
    }

    /** The system dialog asking the user to make CallMind the phone app, or null if unavailable. */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        }
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
    }

    /** Turns the dial-pad entry point and in-call service on or off. */
    fun setComponentsEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val pm = context.packageManager
        for (name in listOf(DIALER_ALIAS, IN_CALL_SERVICE)) {
            pm.setComponentEnabledSetting(ComponentName(context.packageName, name), state, PackageManager.DONT_KILL_APP)
        }
    }
}
