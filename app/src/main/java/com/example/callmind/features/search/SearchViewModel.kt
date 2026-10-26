package com.example.callmind.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.data.local.entities.ContactEntity
import com.example.callmind.data.local.entities.NoteEntity
import com.example.callmind.data.repository.NoteRepository
import com.example.callmind.data.system.ContactProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val contactProvider: ContactProvider,
    private val noteRepository: NoteRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Re-queried on every search so contacts granted/added after launch still show up.
    val searchResults: StateFlow<SearchResults> = _searchQuery
        .debounce(200)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.length < MIN_QUERY_LENGTH) {
                flowOf(SearchResults())
            } else {
                val contacts = contactProvider.getContacts().filter {
                    it.name?.contains(query, ignoreCase = true) == true || it.phoneNumber.contains(query)
                }
                noteRepository.getAllNotes().map { notes ->
                    SearchResults(
                        contacts = contacts,
                        notes = notes.filter {
                            it.content.contains(query, ignoreCase = true) || it.phoneNumber.contains(query)
                        }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

data class SearchResults(
    val contacts: List<ContactEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList()
)
