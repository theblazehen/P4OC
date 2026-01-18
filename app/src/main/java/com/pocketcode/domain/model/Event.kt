package com.pocketcode.domain.model

import kotlinx.serialization.Serializable

sealed class OpenCodeEvent {
    data class MessageUpdated(val message: Message) : OpenCodeEvent()
    data class MessagePartUpdated(val part: Part, val delta: String?) : OpenCodeEvent()
    data class SessionCreated(val session: Session) : OpenCodeEvent()
    data class SessionUpdated(val session: Session) : OpenCodeEvent()
    data class SessionStatusChanged(val sessionID: String, val status: SessionStatus) : OpenCodeEvent()
    data class PermissionRequested(val permission: Permission) : OpenCodeEvent()
    data object Connected : OpenCodeEvent()
    data class Disconnected(val reason: String?) : OpenCodeEvent()
    data class Error(val throwable: Throwable) : OpenCodeEvent()
}

@Serializable
sealed class SessionStatus {
    @Serializable
    data object Idle : SessionStatus()
    
    @Serializable
    data object Busy : SessionStatus()
    
    @Serializable
    data class Retry(val attempt: Int, val message: String) : SessionStatus()
}
