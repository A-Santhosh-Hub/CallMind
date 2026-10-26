package com.example.callmind.features.phone

import android.Manifest
import android.telecom.PhoneAccountHandle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.callmind.core.phone.CallPlacer

/**
 * Returns a function that places a call from this app, asking for the phone permission first when needed.
 * [PhoneAccountHandle] picks the SIM; null lets the system choose (or ask).
 */
@Composable
fun rememberPlaceCall(): (String, PhoneAccountHandle?) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, PhoneAccountHandle?>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = pending
        pending = null
        if (request != null && !CallPlacer.place(context, request.first, request.second)) {
            Toast.makeText(context, "Allow the Phone permission to make calls", Toast.LENGTH_LONG).show()
        }
    }

    return remember {
        { number, account ->
            if (number.isBlank()) {
                Unit
            } else if (CallPlacer.canCall(context)) {
                if (!CallPlacer.place(context, number, account)) {
                    Toast.makeText(context, "Could not start the call", Toast.LENGTH_SHORT).show()
                }
            } else {
                pending = number to account
                launcher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            }
        }
    }
}
