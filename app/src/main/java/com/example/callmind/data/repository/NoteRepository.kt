package com.example.callmind.data.repository

import com.example.callmind.data.local.daos.NoteDao
import com.example.callmind.data.local.entities.NoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun getNotesForNumber(phoneNumber: String): Flow<List<NoteEntity>> = 
        noteDao.getNotesForNumber(phoneNumber)

    suspend fun insertNote(note: NoteEntity) = noteDao.insertNote(note)

    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)

    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
}
