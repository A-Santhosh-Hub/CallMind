package com.example.callmind.data.system

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

data class ContactNumber(val number: String, val label: String)

/**
 * One person in the phone's address book. The list holds only what the list screen shows;
 * phone numbers are read on demand with [DeviceContactsSource.numbersOf].
 */
data class DeviceContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean
) {
    /** Letter used for the section header; every non A-Z start (digits, Tamil, ...) goes under '#'. */
    val sectionLetter: Char = sectionLetterOf(name)

    companion object {
        fun sectionLetterOf(name: String): Char {
            val first = name.trim().firstOrNull() ?: return '#'
            // "Élodie" -> E
            val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull() ?: first
            val upper = base.uppercaseChar()
            return if (upper in 'A'..'Z') upper else '#'
        }
    }
}

data class NewContact(
    val givenName: String = "",
    val familyName: String = "",
    val displayName: String = "",
    val company: String = "",
    val title: String = "",
    val phones: List<Pair<Int, String>> = emptyList(),   // (ContactsContract phone TYPE_*, number)
    val emails: List<Pair<Int, String>> = emptyList(),   // (ContactsContract email TYPE_*, address)
    val note: String = "",
    val photoJpeg: ByteArray? = null
)

/**
 * Reads and edits the phone's contacts.
 *
 * Built to open instantly, the way the phone's own Contacts app does:
 *  - the list is read from the Contacts table only (no join with every phone number) and is
 *    sorted by the provider's own sort key, so nothing is re-sorted here;
 *  - the last result is saved to a small file and shown right away on the next launch;
 *  - it is loaded in the background when the app starts, not when the tab is opened;
 *  - it is re-read only when the phone says contacts changed (ContentObserver), not on every visit.
 */
@Singleton
class DeviceContactsSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _contacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val contacts: StateFlow<List<DeviceContact>> = _contacts

    /** True once something (saved copy or fresh read) is available to show. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private var observerRegistered = false
    private var pendingRefresh: Job? = null
    private val cacheFile get() = File(context.filesDir, CACHE_FILE)

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val canRead get() = has(Manifest.permission.READ_CONTACTS)
    val canWrite get() = has(Manifest.permission.WRITE_CONTACTS)

    /** Called once at app start: show the saved list immediately, then bring it up to date. */
    suspend fun warmUp() {
        if (!canRead) return
        loadFromCache()
        registerObserver()
        refresh()
    }

    /** Re-reads the address book. The old list stays on screen until the new one is ready. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (!canRead) {
            _contacts.value = emptyList()
            _loaded.value = true
            return@withContext
        }
        registerObserver()
        refreshLock.withLock {
            val fresh = try {
                query()
            } catch (e: Exception) {
                null
            }
            if (fresh != null) {
                if (fresh != _contacts.value) {
                    _contacts.value = fresh
                    saveToCache(fresh)
                }
                _loaded.value = true
            }
        }
    }

    private fun query(): List<DeviceContact> {
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.STARRED),
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER}=1",
            null,
            "${ContactsContract.Contacts.SORT_KEY_PRIMARY} ASC"
        ) ?: return emptyList()

        val list = ArrayList<DeviceContact>(cursor.count.coerceAtLeast(16))
        cursor.use {
            val iId = it.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val iKey = it.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val iName = it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val iPhoto = it.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val iStar = it.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            while (it.moveToNext()) {
                val name = it.getString(iName)?.trim().orEmpty()
                if (name.isEmpty()) continue
                list.add(
                    DeviceContact(
                        id = it.getLong(iId),
                        lookupKey = it.getString(iKey).orEmpty(),
                        name = name,
                        photoUri = it.getString(iPhoto),
                        starred = it.getInt(iStar) == 1
                    )
                )
            }
        }
        return groupBySection(list)
    }

    /**
     * The provider already sorted the names. This only gathers them under their header letter
     * (stable, so provider order is kept inside each letter) with '#' first, as in the reference.
     */
    private fun groupBySection(list: List<DeviceContact>): List<DeviceContact> =
        list.sortedWith(compareBy<DeviceContact> { if (it.sectionLetter == '#') 0 else 1 }.thenBy { it.sectionLetter })

    // ---- saved copy so the next launch has data before the first query returns ----

    private fun loadFromCache() {
        if (_contacts.value.isNotEmpty()) return
        try {
            val file = cacheFile
            if (!file.exists()) return
            val list = ArrayList<DeviceContact>(10_000)
            file.bufferedReader().use { reader ->
                if (reader.readLine() != CACHE_VERSION) return
                reader.forEachLine { line ->
                    val p = line.split('\t', limit = 5)
                    if (p.size == 5) {
                        list.add(
                            DeviceContact(
                                id = p[0].toLongOrNull() ?: return@forEachLine,
                                lookupKey = p[1],
                                name = p[4],
                                photoUri = p[3].ifEmpty { null },
                                starred = p[2] == "1"
                            )
                        )
                    }
                }
            }
            if (list.isNotEmpty() && _contacts.value.isEmpty()) {
                _contacts.value = list
                _loaded.value = true
            }
        } catch (e: Exception) {
            // A bad copy is simply ignored; the fresh read replaces it.
        }
    }

    private fun saveToCache(list: List<DeviceContact>) {
        try {
            val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
            tmp.bufferedWriter().use { w ->
                w.write(CACHE_VERSION); w.write("\n")
                for (c in list) {
                    w.write(c.id.toString()); w.write("\t")
                    w.write(c.lookupKey); w.write("\t")
                    w.write(if (c.starred) "1" else "0"); w.write("\t")
                    w.write(c.photoUri.orEmpty()); w.write("\t")
                    w.write(c.name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')); w.write("\n")
                }
            }
            tmp.renameTo(cacheFile)
        } catch (e: Exception) {
            // Cache is only an optimisation.
        }
    }

    // ---- stay current without polling ----

    private fun registerObserver() {
        if (observerRegistered) return
        observerRegistered = true
        try {
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        // Bursts of changes (a sync touching many rows) become one refresh.
                        pendingRefresh?.cancel()
                        pendingRefresh = scope.launch {
                            delay(700)
                            refresh()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            observerRegistered = false
        }
    }

    // ---- on-demand details ----

    fun lookupUri(contact: DeviceContact): Uri =
        ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)

    /** The numbers of one contact (read when its page opens, not for the whole list). */
    suspend fun numbersOf(contactId: Long): List<ContactNumber> = withContext(Dispatchers.IO) {
        if (!canRead) return@withContext emptyList()
        try {
            val result = ArrayList<ContactNumber>()
            context.contentResolver.query(
                CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(CommonDataKinds.Phone.NUMBER, CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.LABEL),
                "${CommonDataKinds.Phone.CONTACT_ID}=?",
                arrayOf(contactId.toString()),
                null
            )?.use {
                while (it.moveToNext()) {
                    val number = it.getString(0)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    // The same number is returned once per account (Google, WhatsApp, SIM...).
                    if (result.none { n -> n.number.filter(Char::isDigit) == number.filter(Char::isDigit) }) {
                        result.add(ContactNumber(number, CommonDataKinds.Phone.getTypeLabel(context.resources, it.getInt(1), it.getString(2)).toString()))
                    }
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Contacts whose number matches [digits], using the provider's own number index. contactId -> matching number. */
    suspend fun searchByNumber(digits: String): Map<Long, String> = withContext(Dispatchers.IO) {
        if (!canRead || digits.length < 3) return@withContext emptyMap()
        try {
            val found = LinkedHashMap<Long, String>()
            context.contentResolver.query(
                Uri.withAppendedPath(CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(digits)),
                arrayOf(CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use {
                while (it.moveToNext() && found.size < 300) {
                    val id = it.getLong(0)
                    if (id !in found) found[id] = it.getString(1).orEmpty()
                }
            }
            found
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun setStarred(contactId: Long, starred: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite) return@withContext false
        try {
            val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
            val updated = context.contentResolver.update(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), values, null, null
            )
            if (updated > 0) {
                _contacts.value = _contacts.value.map { if (it.id == contactId) it.copy(starred = starred) else it }
                saveToCache(_contacts.value)
            }
            updated > 0
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes contacts from the phone. Returns how many were removed. */
    suspend fun delete(contactIds: Collection<Long>): Int = withContext(Dispatchers.IO) {
        if (!canWrite) return@withContext 0
        var removed = 0
        for (id in contactIds) {
            try {
                val rows = context.contentResolver.delete(
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), null, null
                )
                if (rows > 0) removed++
            } catch (e: Exception) {
                // keep going with the rest
            }
        }
        if (removed > 0) {
            val gone = contactIds.toSet()
            _contacts.value = _contacts.value.filterNot { it.id in gone }
            saveToCache(_contacts.value)
        }
        removed
    }

    /** Saves a new contact on the device (no account, shown as "Phone"). */
    suspend fun insert(contact: NewContact): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite) return@withContext false
        try {
            val ops = ArrayList<ContentProviderOperation>()
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()

            fun data(mime: String) = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, mime)

            val name = CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            if (contact.givenName.isNotBlank() || contact.familyName.isNotBlank()) {
                ops += data(name)
                    .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName.trim())
                    .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName.trim())
                    .build()
            } else if (contact.displayName.isNotBlank()) {
                ops += data(name)
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName.trim())
                    .build()
            }
            if (contact.company.isNotBlank() || contact.title.isNotBlank()) {
                ops += data(CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Organization.COMPANY, contact.company.trim())
                    .withValue(CommonDataKinds.Organization.TITLE, contact.title.trim())
                    .build()
            }
            contact.phones.filter { it.second.isNotBlank() }.forEach { (type, number) ->
                ops += data(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, number.trim())
                    .withValue(CommonDataKinds.Phone.TYPE, type)
                    .build()
            }
            contact.emails.filter { it.second.isNotBlank() }.forEach { (type, address) ->
                ops += data(CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Email.ADDRESS, address.trim())
                    .withValue(CommonDataKinds.Email.TYPE, type)
                    .build()
            }
            if (contact.note.isNotBlank()) {
                ops += data(CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Note.NOTE, contact.note.trim())
                    .build()
            }
            contact.photoJpeg?.let {
                ops += data(CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Photo.PHOTO, it)
                    .build()
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val CACHE_FILE = "contacts_cache.tsv"
        const val CACHE_VERSION = "v1"
    }
}
