package com.example.callmind.features.phone

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.callmind.core.phone.CallManager
import com.example.callmind.core.phone.DialerRole
import com.example.callmind.core.phone.PhoneModeStore

/** Turning phone mode on and off, including the system "default phone app" dialog. */
class PhoneModeSetup(
    val isDefault: Boolean,
    val enable: () -> Unit,
    val disable: () -> Unit
)

private val PHONE_PERMISSIONS = buildList {
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
fun rememberPhoneModeSetup(): PhoneModeSetup {
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(DialerRole.isHeld(context)) }

    // The user can also change the default phone app in system settings; look again on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isDefault = DialerRole.isHeld(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isDefault = DialerRole.isHeld(context)
        if (isDefault) {
            PhoneModeStore.setEnabled(context, true)
            val missing = PHONE_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        } else {
            // Declined: take the entry points away again so CallMind stays a normal app.
            DialerRole.setComponentsEnabled(context, false)
            if (result.resultCode != Activity.RESULT_CANCELED) {
                Toast.makeText(context, "CallMind was not set as the phone app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    return remember(isDefault) {
        PhoneModeSetup(
            isDefault = isDefault,
            enable = {
                DialerRole.setComponentsEnabled(context, true)
                val intent = DialerRole.requestIntent(context)
                if (intent == null) {
                    DialerRole.setComponentsEnabled(context, false)
                    Toast.makeText(context, "This phone does not allow changing the phone app", Toast.LENGTH_LONG).show()
                } else {
                    roleLauncher.launch(intent)
                }
            },
            disable = {
                if (CallManager.calls.value.isNotEmpty()) {
                    Toast.makeText(context, "Finish your call first", Toast.LENGTH_SHORT).show()
                } else {
                    PhoneModeStore.setEnabled(context, false)
                    DialerRole.setComponentsEnabled(context, false)
                    Toast.makeText(context, "Office mode: calls open your phone's own dialer", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
