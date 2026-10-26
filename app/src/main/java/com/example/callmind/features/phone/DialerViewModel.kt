package com.example.callmind.features.phone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.core.phone.T9
import com.example.callmind.data.system.DeviceContact
import com.example.callmind.data.system.DeviceContactsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.text.Normalizer
import javax.inject.Inject

/** A contact suggested while typing on the dial pad, or found by the search box. */
data class DialMatch(val contactId: Long, val name: String, val photoUri: String?, val number: String?)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DialerViewModel @Inject constructor(
    private val contactsSource: DeviceContactsSource
) : ViewModel() {

    private val _digits = MutableStateFlow("")
    val digits: StateFlow<String> = _digits

    private val _query = MutableStateFlow("")

    /** Text of the "Search contacts" box. */
    val query: StateFlow<String> = _query

    /** Contacts whose name (by keypad letters) or number matches what has been typed on the pad. */
    val matches: StateFlow<List<DialMatch>> = combine(_digits, contactsSource.contacts) { d, c -> d to c }
        .debounce(60)
        .flatMapLatest { (typed, contacts) ->
            flow {
                if (typed.isEmpty()) {
                    emit(emptyList())
                    return@flow
                }
                val byId = contacts.associateBy { it.id }

                // Number matches come from the provider's index and already carry the number.
                val byNumber = contactsSource.searchByNumber(typed.filter { it.isDigit() || it == '+' })
                    .mapNotNull { (id, number) -> byId[id]?.let { DialMatch(id, it.name, it.photoUri, number) } }
                    .take(MAX_MATCHES)
                val byName = if (typed.all { it in '0'..'9' }) {
                    contacts.asSequence()
                        .filter { T9.matchesName(it.name, typed) }
                        .map { DialMatch(it.id, it.name, it.photoUri, null) }
                        .take(MAX_MATCHES)
                        .toList()
                } else emptyList()

                // A few digits are more likely the start of a name; a longer run is more likely part of a number.
                val ordered = if (typed.length < 5) byName + byNumber else byNumber + byName
                emitWithNumbers(ordered.distinctBy { it.contactId }.take(MAX_MATCHES))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Results of the "Search contacts" box: names containing the text, or numbers containing the digits. */
    val searchResults: StateFlow<List<DialMatch>> = combine(_query, contactsSource.contacts) { q, c -> q to c }
        .debounce(120)
        .flatMapLatest { (text, contacts) ->
            flow {
                val q = text.trim()
                if (q.isEmpty()) {
                    emit(emptyList())
                    return@flow
                }
                val needle = fold(q)
                val byName = contacts.asSequence()
                    .filter { fold(it.name).contains(needle) }
                    .map { DialMatch(it.id, it.name, it.photoUri, null) }
                    .take(MAX_MATCHES)
                    .toList()
                val digits = q.filter { it.isDigit() || it == '+' }
                val byNumber = if (digits.length >= 3) {
                    val byId = contacts.associateBy { c: DeviceContact -> c.id }
                    contactsSource.searchByNumber(digits)
                        .mapNotNull { (id, number) -> byId[id]?.let { DialMatch(id, it.name, it.photoUri, number) } }
                        .take(MAX_MATCHES)
                } else emptyList()
                emitWithNumbers((byName + byNumber).distinctBy { it.contactId }.take(MAX_MATCHES))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shows the matches straight away, then fills in the first number of each name match. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<List<DialMatch>>.emitWithNumbers(list: List<DialMatch>) {
        emit(list)
        if (list.any { it.number == null }) {
            emit(list.map { m -> if (m.number != null) m else m.copy(number = contactsSource.numbersOf(m.contactId).firstOrNull()?.number) })
        }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun append(key: Char) {
        _digits.value += key
    }

    fun backspace() {
        _digits.value = _digits.value.dropLast(1)
    }

    fun clear() {
        _digits.value = ""
    }

    fun set(number: String) {
        _digits.value = number
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    private companion object {
        const val MAX_MATCHES = 30
    }
}
