package com.example.callmind.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.callmind.data.local.entities.ContactEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import android.util.Log

@Singleton
class ContactProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("ContactProvider", "Permission READ_CONTACTS not granted")
            return@withContext emptyList()
        }
        
        val contacts = mutableListOf<ContactEntity>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                Log.d("ContactProvider", "Found ${it.count} phone contacts")

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)
                    val id = it.getLong(idIndex)

                    contacts.add(
                        ContactEntity(
                            androidContactId = id,
                            name = name,
                            phoneNumber = number ?: "Unknown"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ContactProvider", "Error querying contacts", e)
        }
        // The same person/number is returned once per account (Google, WhatsApp, SIM...).
        contacts.distinctBy { it.name to it.phoneNumber.filter { c -> c.isDigit() } }
    }
}
