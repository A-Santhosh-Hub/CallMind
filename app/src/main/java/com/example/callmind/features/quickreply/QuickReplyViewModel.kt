package com.example.callmind.features.quickreply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callmind.data.local.entities.QuickReplyEntity
import com.example.callmind.data.repository.QuickReplyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickReplyViewModel @Inject constructor(
    private val repository: QuickReplyRepository
) : ViewModel() {

    val replies: StateFlow<List<QuickReplyEntity>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a new reply when [id] is 0, otherwise updates the existing one. */
    fun save(id: Long, title: String, message: String) {
        viewModelScope.launch {
            val existing = replies.value.firstOrNull { it.id == id }
            repository.save(
                (existing ?: QuickReplyEntity(title = title, message = message))
                    .copy(title = title.trim(), message = message.trim())
            )
        }
    }

    fun delete(reply: QuickReplyEntity) {
        viewModelScope.launch { repository.delete(reply) }
    }
}
