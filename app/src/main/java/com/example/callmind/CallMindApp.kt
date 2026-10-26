package com.example.callmind

import android.app.Application
import com.example.callmind.core.phone.PhoneModeStore
import com.example.callmind.data.system.CallLogProvider
import com.example.callmind.data.system.DeviceContactsSource
import com.example.callmind.ui.theme.ThemeModeStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WarmUpEntryPoint {
    fun contacts(): DeviceContactsSource
    fun callLogs(): CallLogProvider
}

@HiltAndroidApp
class CallMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeModeStore.init(this)
        PhoneModeStore.init(this)

        // Start the slow reads now, off the main thread, so they are ready by the time a screen asks.
        // (The phone's own Contacts app is already running in the background; this is the equivalent.)
        CoroutineScope(Dispatchers.IO).launch {
            val entryPoint = EntryPointAccessors.fromApplication(this@CallMindApp, WarmUpEntryPoint::class.java)
            entryPoint.callLogs()          // creating it starts watching the call log
            entryPoint.contacts().warmUp() // saved copy first, then a fresh read
        }
    }
}
