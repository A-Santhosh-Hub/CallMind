package com.example.callmind.core.communication

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.callmind.core.phone.CallPlacer
import com.example.callmind.core.phone.DialerRole
import com.example.callmind.core.phone.PhoneModeStore

class CommunicationLauncher(private val context: Context) {

    /** Opens the WhatsApp chat; when [message] is given it is typed into the box, ready to send. */
    fun launchWhatsApp(phoneNumber: String, message: String? = null) {
        val digits = normalizeNumber(phoneNumber)
        if (digits.isEmpty()) {
            openApp(WHATSAPP_PACKAGE, "WhatsApp")
        } else {
            val url = buildString {
                append("https://api.whatsapp.com/send?phone=").append(digits)
                if (!message.isNullOrBlank()) append("&text=").append(Uri.encode(message))
            }
            start(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "WhatsApp not installed")
        }
    }

    fun launchTelegram(phoneNumber: String) {
        val digits = normalizeNumber(phoneNumber)
        if (digits.isEmpty()) {
            openApp(TELEGRAM_PACKAGE, "Telegram")
        } else {
            start(
                Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$digits")),
                "Telegram not installed"
            )
        }
    }

    fun launchDialer(phoneNumber: String) {
        // Phone mode: CallMind is the phone app, so it places the call itself. Office mode (or
        // without the phone permission) hands over to whichever dialer the phone uses.
        if (phoneNumber.isNotBlank() && PhoneModeStore.isEnabled(context) && DialerRole.isHeld(context) &&
            CallPlacer.place(context, phoneNumber)
        ) return
        start(
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null)),
            "No dialer app found"
        )
    }

    fun launchSMS(phoneNumber: String) {
        start(
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phoneNumber, null)),
            "No messaging app found"
        )
    }

    /** Opens Google Pay. A UPI payee can't be derived from a phone number, so no payment is pre-filled. */
    fun launchGPay() = openApp(GPAY_PACKAGE, "Google Pay")

    fun viewContact(phoneNumber: String) {
        val lookupUri = findContactUri(phoneNumber)
        val intent = if (lookupUri != null) {
            Intent(Intent.ACTION_VIEW, lookupUri)
        } else {
            // Not in the address book (or no permission): offer to add it.
            Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            }
        }
        start(intent, "Could not open contacts")
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone Number", text))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findContactUri(phoneNumber: String): Uri? {
        if (phoneNumber.isBlank() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        return try {
            val lookup = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                lookup,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContactsContract.Contacts.getLookupUri(cursor.getLong(0), cursor.getString(1))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun openApp(packageName: String, appName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, "$appName not installed", Toast.LENGTH_SHORT).show()
        } else {
            start(intent, "$appName not installed")
        }
    }

    private fun start(intent: Intent, errorMessage: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeNumber(number: String): String = number.filter { it.isDigit() }

    private companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val TELEGRAM_PACKAGE = "org.telegram.messenger"
        const val GPAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"
    }
}
