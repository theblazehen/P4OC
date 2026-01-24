package dev.blazelight.p4oc.domain.repository

import dev.blazelight.p4oc.core.network.ApiResult
import dev.blazelight.p4oc.domain.model.Message
import dev.blazelight.p4oc.domain.model.MessageWithParts
import dev.blazelight.p4oc.domain.model.Part
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessagesForSession(sessionId: String): Flow<List<MessageWithParts>>
    suspend fun fetchMessages(sessionId: String, limit: Int? = null): ApiResult<List<MessageWithParts>>
    suspend fun sendMessage(sessionId: String, text: String): ApiResult<Unit>
    suspend fun respondToPermission(sessionId: String, permissionId: String, response: String): ApiResult<Boolean>
    fun updateMessagePart(part: Part, delta: String?)
}
