package com.example.callmind.core.phone

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CallUiState { DIALING, RINGING, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED }

/** What the in-call screen and notification need to know about one call. */
data class CallInfo(
    val id: Int,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val state: CallUiState,
    val isIncoming: Boolean,
    /** When the call was answered (epoch ms); 0 until then. */
    val connectTimeMillis: Long,
    val isConference: Boolean,
    val canHold: Boolean,
    val canMerge: Boolean,
    val canSwap: Boolean,
    val simLabel: String?,
    /** Voice over Wi-Fi / HD voice, shown as a small tag under the name. */
    val isWifi: Boolean = false,
    val isHd: Boolean = false,
    /** Where the number is registered (from the call log), e.g. "Tamil Nadu". */
    val location: String? = null
) {
    val displayName: String get() = name ?: number.ifBlank { if (isConference) "Conference call" else "Unknown number" }
}

/** A call that just finished: kept so the screen can say "Call ended" before it closes. */
data class EndedCall(val info: CallInfo, val reason: String, val durationSeconds: Long)

data class AudioInfo(
    val muted: Boolean = false,
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedRoutes: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
) {
    val hasBluetooth: Boolean get() = supportedRoutes and CallAudioState.ROUTE_BLUETOOTH != 0
    val hasWiredHeadset: Boolean get() = supportedRoutes and CallAudioState.ROUTE_WIRED_HEADSET != 0
}

/**
 * The bridge between Android's call framework ([CallMindInCallService]) and the in-call screen.
 * All calls into and out of it happen on the main thread (the framework's callback thread).
 */
object CallManager {
    private class Tracked(
        val call: Call,
        val id: Int,
        val isIncoming: Boolean,
        var name: String? = null,
        var photoUri: String? = null,
        var location: String? = null,
        var lookedUp: Boolean = false
    )

    private val tracked = LinkedHashMap<Call, Tracked>()
    private var nextId = 1
    private var service: InCallService? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())

    /** Calls to show, ringing first. Conference members are folded into the conference call. */
    val calls: StateFlow<List<CallInfo>> = _calls

    private val _audio = MutableStateFlow(AudioInfo())
    val audio: StateFlow<AudioInfo> = _audio

    private val _canAddCall = MutableStateFlow(true)
    val canAddCall: StateFlow<Boolean> = _canAddCall

    private val _ended = MutableStateFlow<EndedCall?>(null)
    val ended: StateFlow<EndedCall?> = _ended

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            publish()
            if (state == Call.STATE_ACTIVE) CallRecorder.onCallActive(appContext(), tracked[call]?.let { info(it) })
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publish()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = publish()
    }

    private var appContext: Context? = null
    private fun appContext(): Context = appContext ?: error("CallManager used before the call service started")

    fun attach(service: InCallService) {
        this.service = service
        appContext = service.applicationContext
        _audio.value = audioOf(service)
    }

    fun detach(service: InCallService) {
        if (this.service === service) this.service = null
    }

    fun onCallAdded(context: Context, call: Call) {
        appContext = context.applicationContext
        val entry = Tracked(call, nextId++, isIncoming = call.state == Call.STATE_RINGING)
        tracked[call] = entry
        call.registerCallback(callback)
        _ended.value = null
        publish()
        lookUp(context.applicationContext, entry)
        if (call.state == Call.STATE_ACTIVE) CallRecorder.onCallActive(context.applicationContext, info(entry))
    }

    fun onCallRemoved(call: Call) {
        val entry = tracked.remove(call) ?: return
        call.unregisterCallback(callback)
        publish()
        if (call.parent == null) {
            val details = call.details
            val connected = details.connectTimeMillis
            val seconds = if (connected > 0) ((System.currentTimeMillis() - connected) / 1000).coerceAtLeast(0) else 0
            _ended.value = EndedCall(
                info = info(entry).copy(state = CallUiState.DISCONNECTED),
                reason = disconnectText(details.disconnectCause, entry.isIncoming, connected > 0),
                durationSeconds = seconds
            )
            CallRecorder.onCallEnded(appContext())
        }
    }

    fun onAudioChanged(state: CallAudioState) {
        _audio.value = AudioInfo(state.isMuted, state.route, state.supportedRouteMask)
    }

    fun onCanAddCallChanged(can: Boolean) {
        _canAddCall.value = can
    }

    // --- actions -------------------------------------------------------------------------------

    private fun callOf(id: Int): Call? = tracked.values.firstOrNull { it.id == id }?.call

    fun answer(id: Int) = callOf(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun reject(id: Int, message: String? = null) {
        callOf(id)?.reject(message != null, message)
    }

    fun disconnect(id: Int) = callOf(id)?.disconnect()

    fun hold(id: Int) = callOf(id)?.hold()

    fun unhold(id: Int) = callOf(id)?.unhold()

    /** Merges two calls into a conference. */
    fun merge(id: Int) {
        val call = callOf(id) ?: return
        if (call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
            call.mergeConference()
        } else {
            call.conferenceableCalls.firstOrNull()?.let { call.conference(it) }
        }
    }

    /** Puts the other call on hold and brings [id] back. */
    fun swapTo(id: Int) {
        val call = callOf(id) ?: return
        if (call.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) call.swapConference() else call.unhold()
    }

    fun playDtmf(id: Int, digit: Char) {
        callOf(id)?.playDtmfTone(digit)
    }

    fun stopDtmf(id: Int) {
        callOf(id)?.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun setRoute(route: Int) {
        service?.setAudioRoute(route)
    }

    fun answerFirstRinging() {
        _calls.value.firstOrNull { it.state == CallUiState.RINGING }?.let { answer(it.id) }
    }

    fun declineFirstRinging() {
        _calls.value.firstOrNull { it.state == CallUiState.RINGING }?.let { reject(it.id) }
    }

    fun hangUpAll() {
        tracked.values.filter { it.call.parent == null }.forEach { it.call.disconnect() }
    }

    // --- internals -----------------------------------------------------------------------------

    private fun publish() {
        _calls.value = tracked.values
            .filter { it.call.parent == null }
            .map { info(it) }
            .sortedBy { if (it.state == CallUiState.RINGING) 0 else 1 }
    }

    private fun info(entry: Tracked): CallInfo {
        val call = entry.call
        val details = call.details
        val number = details.handle?.schemeSpecificPart.orEmpty().let { Uri.decode(it) }
        val conference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE)
        return CallInfo(
            id = entry.id,
            number = number,
            name = entry.name ?: details.callerDisplayName?.takeIf { it.isNotBlank() },
            photoUri = entry.photoUri,
            state = uiState(call.state),
            isIncoming = entry.isIncoming,
            connectTimeMillis = details.connectTimeMillis,
            isConference = conference,
            canHold = details.can(Call.Details.CAPABILITY_HOLD),
            canMerge = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty(),
            canSwap = details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            simLabel = simLabel(details),
            isWifi = details.hasProperty(Call.Details.PROPERTY_WIFI),
            isHd = details.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO),
            location = entry.location
        )
    }

    private fun uiState(state: Int) = when (state) {
        Call.STATE_RINGING, Call.STATE_SIMULATED_RINGING -> CallUiState.RINGING
        Call.STATE_ACTIVE -> CallUiState.ACTIVE
        Call.STATE_HOLDING -> CallUiState.HOLDING
        Call.STATE_DISCONNECTING -> CallUiState.DISCONNECTING
        Call.STATE_DISCONNECTED -> CallUiState.DISCONNECTED
        else -> CallUiState.DIALING // NEW, CONNECTING, DIALING, PULLING, SELECT_PHONE_ACCOUNT
    }

    private fun simLabel(details: Call.Details): String? {
        val handle = details.accountHandle ?: return null
        val context = appContext ?: return null
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            val accounts = telecom.callCapablePhoneAccounts
            if (accounts.size > 1) {
                val index = accounts.indexOf(handle)
                if (index >= 0) "SIM ${index + 1}" else telecom.getPhoneAccount(handle)?.label?.toString()
            } else null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun disconnectText(cause: DisconnectCause?, incoming: Boolean, connected: Boolean): String {
        if (connected) return "Call ended"
        return when (cause?.code) {
            DisconnectCause.MISSED -> "Missed call"
            DisconnectCause.REJECTED -> "Call declined"
            DisconnectCause.BUSY -> "Busy"
            DisconnectCause.CANCELED -> "Call canceled"
            DisconnectCause.ERROR -> cause.description?.toString() ?: "Call failed"
            DisconnectCause.REMOTE -> if (incoming) "Call ended" else "No answer"
            else -> "Call ended"
        }
    }

    private fun lookUp(context: Context, entry: Tracked) {
        if (entry.lookedUp) return
        val number = entry.call.details.handle?.schemeSpecificPart.orEmpty()
        if (number.isBlank()) return
        entry.lookedUp = true
        scope.launch {
            val found = if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) withContext(Dispatchers.IO) { findContact(context, number) } else null
            val place = withContext(Dispatchers.IO) { findLocation(context, number) }
            if (tracked.containsKey(entry.call) && (found != null || place != null)) {
                if (found != null) {
                    entry.name = found.first
                    entry.photoUri = found.second
                }
                entry.location = place
                publish()
            }
        }
    }

    private fun findContact(context: Context, number: String): Pair<String, String?>? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
        }
    } catch (e: Exception) {
        Log.w("CallManager", "Contact lookup failed", e)
        null
    }

    /** The place the phone's call log recorded for this number on an earlier call, if any. */
    private fun findLocation(context: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val tail = number.filter { it.isDigit() }.takeLast(10)
            if (tail.length < 7) return null
            context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI.buildUpon()
                    .appendQueryParameter(android.provider.CallLog.Calls.LIMIT_PARAM_KEY, "1").build(),
                arrayOf(android.provider.CallLog.Calls.GEOCODED_LOCATION),
                "${android.provider.CallLog.Calls.NUMBER} LIKE ? AND ${android.provider.CallLog.Calls.GEOCODED_LOCATION} IS NOT NULL",
                arrayOf("%$tail"),
                "${android.provider.CallLog.Calls.DATE} DESC"
            )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null }
        } catch (e: Exception) {
            null
        }
    }

    private fun audioOf(service: InCallService): AudioInfo {
        @Suppress("DEPRECATION")
        val state = service.callAudioState ?: return AudioInfo()
        return AudioInfo(state.isMuted, state.route, state.supportedRouteMask)
    }
}
