package com.example.callmind.features.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.data.local.entities.RuleType
import com.example.callmind.data.repository.CallBlockingRepository
import com.example.callmind.data.system.DeviceContact
import com.example.callmind.data.system.DeviceContactsSource
import com.example.callmind.data.system.NewContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val source: DeviceContactsSource,
    private val blocking: CallBlockingRepository
) : ViewModel() {

    /** Shared by every Contacts screen, loaded once and refreshed when the screen comes back. */
    val contacts: StateFlow<List<DeviceContact>> = source.contacts
    val loaded: StateFlow<Boolean> = source.loaded

    val canWrite: Boolean get() = source.canWrite

    fun lookupUri(contact: DeviceContact): Uri = source.lookupUri(contact)

    suspend fun numbersOf(contactId: Long) = source.numbersOf(contactId)

    suspend fun searchByNumber(digits: String) = source.searchByNumber(digits)

    fun refresh() {
        viewModelScope.launch { source.refresh() }
    }

    fun setStarred(contact: DeviceContact, starred: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(source.setStarred(contact.id, starred)) }
    }

    fun delete(ids: Collection<Long>, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch { onResult(source.delete(ids)) }
    }

    fun save(contact: NewContact, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = source.insert(contact)
            if (saved) source.refresh()
            onResult(saved)
        }
    }

    /** Adds every number of the given contacts to the blocklist. Returns how many numbers were new. */
    fun block(contacts: Collection<DeviceContact>, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            var added = 0
            contacts.forEach { contact ->
                source.numbersOf(contact.id).forEach { n ->
                    if (blocking.addRule(RuleType.BLOCK, n.number, contact.name)) added++
                }
            }
            onResult(added)
        }
    }
}
