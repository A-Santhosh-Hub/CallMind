package com.example.callmind.core.blocking

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Android only lets one app at a time screen incoming calls ("Caller ID & spam" role, Android 10+).
 * The user grants it through a system dialog and can take it back in system settings.
 */
object CallBlockingRole {
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun isHeld(context: Context): Boolean {
        if (!isSupported) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    /** The system dialog asking the user to make CallMind the call-screening app, or null if unavailable. */
    fun requestIntent(context: Context): Intent? {
        if (!isSupported) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
