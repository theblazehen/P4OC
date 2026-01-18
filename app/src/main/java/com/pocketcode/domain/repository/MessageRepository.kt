package com.pocketcode.domain.repository

import com.pocketcode.core.network.ApiResult
import com.pocketcode.domain.model.Message
import com.pocketcode.domain.model.MessageWithParts
import com.pocketcode.domain.model.Part
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForSession(sessionId: String): Flow<List<MessageWithParts>>
    suspend fun fetchMessages(sessionId: String, limit: Int? = null): ApiResult<List<MessageWithParts>>
    suspend fun sendMessage(sessionId: String, text: String): ApiResult<Unit>
    suspend fun respondToPermission(sessionId: String, permissionId: String, response: String): ApiResult<Boolean>
    fun updateMessagePart(part: Part, delta: String?)
}
