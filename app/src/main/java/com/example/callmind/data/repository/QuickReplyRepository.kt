package com.example.callmind.data.repository

import com.example.callmind.data.local.daos.QuickReplyDao
import com.example.callmind.data.local.entities.QuickReplyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickReplyRepository @Inject constructor(
    private val dao: QuickReplyDao
) {
    fun getAll(): Flow<List<QuickReplyEntity>> = dao.getAll()

    suspend fun save(reply: QuickReplyEntity) {
        if (reply.id == 0L) dao.insert(reply) else dao.update(reply)
    }

    suspend fun delete(reply: QuickReplyEntity) = dao.delete(reply)
}
