package com.example.callmind.core.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat

/** A SIM that can place calls. */
data class SimAccount(val handle: PhoneAccountHandle, val label: String)

/** Places calls through Android's telecom service, optionally on a chosen SIM. */
object CallPlacer {
    fun canCall(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** The phone's call-capable SIMs, in slot order; empty without the phone permission. */
    fun simAccounts(context: Context): List<SimAccount> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.callCapablePhoneAccounts.mapIndexed { index, handle ->
                SimAccount(handle, "SIM ${index + 1}")
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Returns false when the call could not be started (no permission, empty number, ...). */
    fun place(context: Context, number: String, account: PhoneAccountHandle? = null): Boolean {
        val digits = number.trim()
        if (digits.isEmpty() || !canCall(context)) return false
        return try {
            val extras = Bundle()
            if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", digits, null), extras)
            true
        } catch (e: SecurityException) {
            Log.w("CallPlacer", "Cannot place call", e)
            false
        }
    }

    /** Calls the voicemail box (long-press 1 on the pad). Returns false without the phone permission. */
    fun placeVoicemail(context: Context): Boolean {
        if (!canCall(context)) return false
        return try {
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts(android.telecom.PhoneAccount.SCHEME_VOICEMAIL, "", null), Bundle())
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
