package com.example.callmind.features.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.data.local.entities.NoteEntity
import com.example.callmind.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    val allNotes: Flow<List<NoteEntity>> = repository.getAllNotes()

    fun saveNote(phoneNumber: String, content: String) {
        viewModelScope.launch {
            val note = NoteEntity(
                phoneNumber = phoneNumber,
                content = content
            )
            repository.insertNote(note)
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
}
