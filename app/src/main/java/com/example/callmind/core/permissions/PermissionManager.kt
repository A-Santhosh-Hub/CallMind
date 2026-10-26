package com.example.callmind.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

import android.util.Log

@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Refresh state when coming back from system settings or on startup
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val newState = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                if (isGranted != newState) {
                    Log.d("PermissionManager", "Permission $permission status changed to $newState")
                    isGranted = newState
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("PermissionManager", "Permission $permission result: $granted")
        isGranted = granted
    }

    return remember(isGranted) {
        PermissionState(
            permission = permission,
            isGranted = isGranted,
            requestPermission = { launcher.launch(permission) }
        )
    }
}

data class PermissionState(
    val permission: String,
    val isGranted: Boolean,
    val requestPermission: () -> Unit
)

object CallMindPermissions {
    val RequiredPermissions = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS
    )
}
