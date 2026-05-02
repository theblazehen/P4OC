package dev.blazelight.p4oc.data.session

import dev.blazelight.p4oc.domain.session.SessionId

sealed interface MutationIntent {
    data class CreateSession(val title: String? = null, val parentId: SessionId? = null) : MutationIntent
    data class RenameSession(val sessionId: SessionId, val title: String) : MutationIntent
    data class DeleteSession(val sessionId: SessionId) : MutationIntent
    data class SendMessage(val sessionId: SessionId, val messageId: String? = null) : MutationIntent
}
